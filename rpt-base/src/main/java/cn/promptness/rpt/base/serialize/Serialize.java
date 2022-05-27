package cn.promptness.rpt.base.serialize;

import cn.promptness.rpt.base.protocol.Meta;

public interface Serialize {

    byte[] serialize(Meta meta) throws Exception;

    Meta deserialize(byte[] data) throws Exception;
}
