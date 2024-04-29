package cn.holmes.rpt.base.serialize.protostuff;

import cn.holmes.rpt.base.serialize.api.ObjectOutputStream;
import cn.holmes.rpt.base.serialize.protostuff.utils.WrapperUtils;
import io.protostuff.GraphIOUtil;
import io.protostuff.LinkedBuffer;
import io.protostuff.Schema;
import io.protostuff.runtime.RuntimeSchema;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class ProtostuffDataOutputStream implements ObjectOutputStream {

    private final LinkedBuffer buffer = LinkedBuffer.allocate();
    private final DataOutputStream dos;

    public ProtostuffDataOutputStream(OutputStream outputStream) {
        dos = new DataOutputStream(outputStream);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void writeObject(Object obj) throws IOException {
        byte[] bytes;
        byte[] classNameBytes;

        try {
            if (obj == null || WrapperUtils.needWrapper(obj)) {
                Schema<Wrapper> schema = RuntimeSchema.getSchema(Wrapper.class);
                Wrapper wrapper = new Wrapper(obj);
                bytes = GraphIOUtil.toByteArray(wrapper, schema, buffer);
                classNameBytes = Wrapper.class.getName().getBytes();
            } else {
                Schema schema = RuntimeSchema.getSchema(obj.getClass());
                bytes = GraphIOUtil.toByteArray(obj, schema, buffer);
                classNameBytes = obj.getClass().getName().getBytes();
            }
        } finally {
            buffer.clear();
        }

        dos.writeInt(classNameBytes.length);
        dos.writeInt(bytes.length);
        dos.write(classNameBytes);
        dos.write(bytes);
    }

    @Override
    public void writeInt(int v) throws IOException {
        dos.writeInt(v);
    }

    @Override
    public void writeByte(byte v) throws IOException {
        dos.writeByte(v);
    }

    @Override
    public void writeBytes(byte[] b) throws IOException {
        dos.writeInt(b.length);
        dos.write(b);
    }

    @Override
    public void writeUTF(String v) throws IOException {
        byte[] bytes = v.getBytes();
        dos.writeInt(bytes.length);
        dos.write(bytes);
    }

    @Override
    public void flush() throws IOException {
        dos.flush();
    }

    @Override
    public void close() throws IOException {
        dos.close();
    }
}
