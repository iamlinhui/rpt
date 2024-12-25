package cn.holmes.rpt.base.serialize;

import cn.holmes.rpt.base.serialize.api.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class SerializerDispatcher {

    public byte[] serialize(SerializationType serializationType, Object obj) throws Exception {
        Serialization serialization = SerializeFactory.getSerialization(serializationType);
        ByteArrayOutputStream byteBufOutputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = serialization.serialize(byteBufOutputStream)) {
            objectOutputStream.writeObject(obj);
            objectOutputStream.flush();
            return byteBufOutputStream.toByteArray();
        }
    }

    public <T> T deserialize(SerializationType serializationType, byte[] data, Class<T> clazz) throws Exception {
        Serialization serialization = SerializeFactory.getSerialization(serializationType);
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(data);
        try (ObjectInputStream objectInputStream = serialization.deserialize(byteArrayInputStream)) {
            return objectInputStream.readObject(clazz);
        }
    }
}
