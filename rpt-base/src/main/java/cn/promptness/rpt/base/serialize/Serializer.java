package cn.promptness.rpt.base.serialize;

import cn.promptness.rpt.base.protocol.Meta;

public interface Serializer {

    byte[] serialize(Meta meta) throws Exception;

    Meta deserialize(byte[] data) throws Exception;
}
