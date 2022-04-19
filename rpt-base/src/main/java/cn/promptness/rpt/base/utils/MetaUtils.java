package cn.promptness.rpt.base.utils;

import cn.promptness.rpt.base.protocol.Meta;
import io.protostuff.LinkedBuffer;
import io.protostuff.ProtostuffIOUtil;
import io.protostuff.Schema;
import io.protostuff.runtime.RuntimeSchema;

public class MetaUtils {

    private static final LinkedBuffer BUFFER = LinkedBuffer.allocate(LinkedBuffer.DEFAULT_BUFFER_SIZE);
    private static final Schema<Meta> SCHEMA_CACHE = RuntimeSchema.getSchema(Meta.class);

    public static byte[] serialize(Meta meta) {
        try {
            return ProtostuffIOUtil.toByteArray(meta, SCHEMA_CACHE, BUFFER);
        } finally {
            BUFFER.clear();
        }
    }

    public static Meta deserialize(byte[] data) {
        Meta meta = SCHEMA_CACHE.newMessage();
        ProtostuffIOUtil.mergeFrom(data, meta, SCHEMA_CACHE);
        return meta;
    }
}
