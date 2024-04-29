package cn.holmes.rpt.base.serialize.json;

import cn.holmes.rpt.base.serialize.api.ObjectInputStream;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.*;
import java.lang.reflect.Type;

public class JsonDataInputStream implements ObjectInputStream {

    private final BufferedReader reader;

    public JsonDataInputStream(InputStream in) {
        this.reader = new BufferedReader(new InputStreamReader(in));
    }

    @Override
    public int readInt() throws IOException {
        return read(int.class);
    }

    @Override
    public byte readByte() throws IOException {
        return read(byte.class);
    }

    @Override
    public byte[] readBytes() throws IOException {
        return readLine().getBytes();
    }

    @Override
    public String readUTF() throws IOException {
        return read(String.class);
    }

    @Override
    public <T> T readObject(final Class<T> cls) throws IOException, ClassNotFoundException {
        return read(cls);
    }

    @Override
    public <T> T readObject(final Class<T> cls, final Type genericType) throws IOException, ClassNotFoundException {
        if (genericType == null || genericType == cls) {
            return read(cls);
        }
        return read(genericType);
    }

    private <T> T read(Class<T> cls) throws IOException {
        String json = readLine();
        return JacksonUtil.getJsonMapper().readValue(json, cls);
    }

    <T> T read(Type genericType) throws IOException {
        String json = readLine();
        return JacksonUtil.getJsonMapper().readValue(json, new TypeReference<T>() {
            @Override
            public Type getType() {
                return genericType;
            }
        });
    }

    private String readLine() throws IOException {
        String line = reader.readLine();
        if (line == null || line.trim().length() == 0) {
            throw new EOFException();
        }
        return line;
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
