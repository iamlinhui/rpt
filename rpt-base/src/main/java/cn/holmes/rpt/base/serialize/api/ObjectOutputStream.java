package cn.holmes.rpt.base.serialize.api;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;

public interface ObjectOutputStream extends Closeable {

    void writeInt(int v) throws IOException;

    void writeByte(byte v) throws IOException;

    void writeBytes(byte[] b) throws IOException;

    void writeUTF(String v) throws IOException;

    void writeObject(Object obj) throws IOException;

    default void writeMap(Map<String, String> map) throws IOException {
        writeObject(map);
    }

    default void writeThrowable(Object obj) throws IOException {
        writeObject(obj);
    }

    default void writeEvent(Object data) throws IOException {
        writeObject(data);
    }

    void flush() throws IOException;
}
