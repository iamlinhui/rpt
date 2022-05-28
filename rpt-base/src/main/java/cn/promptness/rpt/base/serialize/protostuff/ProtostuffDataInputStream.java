package cn.promptness.rpt.base.serialize.protostuff;

import cn.promptness.rpt.base.serialize.api.ObjectInputStream;
import cn.promptness.rpt.base.serialize.protostuff.utils.WrapperUtils;
import io.protostuff.GraphIOUtil;
import io.protostuff.Schema;
import io.protostuff.runtime.RuntimeSchema;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ProtostuffDataInputStream implements ObjectInputStream {

    private final DataInputStream dis;

    public ProtostuffDataInputStream(InputStream inputStream) {
        dis = new DataInputStream(inputStream);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public <T> T readObject(Class<T> clazzType) throws IOException, ClassNotFoundException {
        int classNameLength = dis.readInt();
        int bytesLength = dis.readInt();

        if (classNameLength < 0 || bytesLength < 0) {
            throw new IOException();
        }

        byte[] classNameBytes = new byte[classNameLength];
        dis.readFully(classNameBytes, 0, classNameLength);

        byte[] bytes = new byte[bytesLength];
        dis.readFully(bytes, 0, bytesLength);

        String className = new String(classNameBytes);
        Class clazz = Class.forName(className);

        Object result;
        if (WrapperUtils.needWrapper(clazz)) {
            Schema<Wrapper> schema = RuntimeSchema.getSchema(Wrapper.class);
            Wrapper wrapper = schema.newMessage();
            GraphIOUtil.mergeFrom(bytes, wrapper, schema);
            result = wrapper.getData();
        } else {
            Schema schema = RuntimeSchema.getSchema(clazz);
            result = schema.newMessage();
            GraphIOUtil.mergeFrom(bytes, result, schema);
        }

        return (T) result;
    }

    @Override
    public int readInt() throws IOException {
        return dis.readInt();
    }

    @Override
    public byte readByte() throws IOException {
        return dis.readByte();
    }

    @Override
    public byte[] readBytes() throws IOException {
        int length = dis.readInt();
        byte[] bytes = new byte[length];
        dis.read(bytes, 0, length);
        return bytes;
    }

    @Override
    public String readUTF() throws IOException {
        int length = dis.readInt();
        byte[] bytes = new byte[length];
        dis.read(bytes, 0, length);
        return new String(bytes);
    }

    @Override
    public void close() throws IOException {
        dis.close();
    }
}
