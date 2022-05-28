package cn.promptness.rpt.base.serialize.api;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;

public interface ObjectInputStream extends Closeable {

    int readInt() throws IOException;

    byte readByte() throws IOException;

    byte[] readBytes() throws IOException;

    String readUTF() throws IOException;

    <T> T readObject(Class<T> cls) throws IOException, ClassNotFoundException;

    default <T> T readObject(Class<T> cls, Type genericType) throws IOException, ClassNotFoundException {
        return readObject(cls);
    }

    default Throwable readThrowable() throws IOException, ClassNotFoundException {
        return readObject(Throwable.class);
    }

    default Map readMap() throws IOException, ClassNotFoundException {
        return readObject(Map.class);
    }
}
