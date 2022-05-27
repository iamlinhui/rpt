package cn.promptness.rpt.base.serialize;

import cn.promptness.rpt.base.protocol.Meta;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public class Jackson implements Serialize {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public byte[] serialize(Meta meta) throws Exception {
        return OBJECT_MAPPER.writeValueAsBytes(meta);
    }

    @Override
    public Meta deserialize(byte[] data) throws IOException {
        return OBJECT_MAPPER.readValue(data, Meta.class);
    }
}
