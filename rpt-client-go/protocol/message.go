package protocol

import (
	"encoding/binary"
	"encoding/json"
	"fmt"
	"io"
)

// MessageType codes matching Java enum
const (
	TypeRegister     = 1
	TypeAuth         = 2
	TypeConnected    = 3
	TypeDisconnected = 4
	TypeKeepalive    = 5
	TypeData         = 6
)

// SerializationType codes
const (
	SerializationProtostuff = 1
	SerializationJSON       = 2
)

type Meta struct {
	ClientKey        string          `json:"clientKey,omitempty"`
	Connection       bool            `json:"connection,omitempty"`
	ChannelId        string          `json:"channelId,omitempty"`
	ServerId         string          `json:"serverId,omitempty"`
	RemoteConfigList json.RawMessage `json:"remoteConfigList,omitempty"`
	RemoteResult     []string        `json:"remoteResult,omitempty"`
}

type RemoteConfigMsg struct {
	RemotePort  int    `json:"remotePort"`
	LocalPort   int    `json:"localPort"`
	LocalIp     string `json:"localIp"`
	Description string `json:"description"`
	ProxyType   string `json:"proxyType"`
	Domain      string `json:"domain"`
	Token       string `json:"token"`
}

func (m *Meta) GetRemoteConfig() *RemoteConfigMsg {
	if len(m.RemoteConfigList) == 0 {
		return nil
	}
	var list []RemoteConfigMsg
	if err := json.Unmarshal(m.RemoteConfigList, &list); err != nil {
		return nil
	}
	if len(list) == 0 {
		return nil
	}
	return &list[0]
}

func (m *Meta) SetRemoteConfig(rc *RemoteConfigMsg) {
	data, _ := json.Marshal([]RemoteConfigMsg{*rc})
	m.RemoteConfigList = data
}

type Message struct {
	Type          int
	Serialization int
	Meta          *Meta
	Data          []byte
}

// Encode writes the message to writer in the wire format:
// [4-byte total length][4-byte type][4-byte serialization][4-byte meta length][meta bytes][data bytes]
// The outer length-field framing (4-byte length prefix) wraps the inner content.
func (msg *Message) Encode(w io.Writer) error {
	serType := SerializationJSON

	var metaBytes []byte
	if msg.Meta != nil {
		var err error
		metaBytes, err = json.Marshal(msg.Meta)
		if err != nil {
			return fmt.Errorf("marshal meta: %w", err)
		}
	}

	// Inner frame: type(4) + serialization(4) + metaLen(4) + meta + data
	innerLen := 4 + 4 + 4 + len(metaBytes) + len(msg.Data)
	buf := make([]byte, 4+innerLen) // 4 bytes length prefix + inner

	binary.BigEndian.PutUint32(buf[0:4], uint32(innerLen))
	binary.BigEndian.PutUint32(buf[4:8], uint32(msg.Type))
	binary.BigEndian.PutUint32(buf[8:12], uint32(serType))
	binary.BigEndian.PutUint32(buf[12:16], uint32(len(metaBytes)))
	copy(buf[16:16+len(metaBytes)], metaBytes)
	copy(buf[16+len(metaBytes):], msg.Data)

	_, err := w.Write(buf)
	return err
}

// DecodeMessage reads one message from a length-prefixed stream.
// Caller must handle the gzip layer externally.
func DecodeMessage(r io.Reader) (*Message, error) {
	// Read 4-byte length
	var lenBuf [4]byte
	if _, err := io.ReadFull(r, lenBuf[:]); err != nil {
		return nil, err
	}
	frameLen := binary.BigEndian.Uint32(lenBuf[:])
	if frameLen < 12 {
		return nil, fmt.Errorf("frame too short: %d", frameLen)
	}

	frameBuf := make([]byte, frameLen)
	if _, err := io.ReadFull(r, frameBuf); err != nil {
		return nil, err
	}

	msg := &Message{}
	msg.Type = int(binary.BigEndian.Uint32(frameBuf[0:4]))
	msg.Serialization = int(binary.BigEndian.Uint32(frameBuf[4:8]))
	metaLen := int(binary.BigEndian.Uint32(frameBuf[8:12]))

	if metaLen > 0 {
		metaBytes := frameBuf[12 : 12+metaLen]
		meta := &Meta{}
		if err := json.Unmarshal(metaBytes, meta); err != nil {
			return nil, fmt.Errorf("unmarshal meta: %w", err)
		}
		msg.Meta = meta
	}

	dataStart := 12 + metaLen
	if dataStart < len(frameBuf) {
		msg.Data = frameBuf[dataStart:]
	}

	return msg, nil
}
