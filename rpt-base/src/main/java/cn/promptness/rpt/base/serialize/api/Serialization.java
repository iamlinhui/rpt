package cn.promptness.rpt.base.serialize.api;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface Serialization {

    SerializationType getType();

    ObjectOutputStream serialize(OutputStream output) throws IOException;

    ObjectInputStream deserialize(InputStream input) throws IOException;
}
