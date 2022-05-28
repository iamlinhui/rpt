package cn.promptness.rpt.base.serialize.api;


import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

public class SerializeFactory {

    private static final Map<SerializationType, Serialization> SERIALIZATION_MAP = new ConcurrentHashMap<>();

    static {
        ServiceLoader<Serialization> serializations = ServiceLoader.load(Serialization.class);
        for (Serialization serialization : serializations) {
            SERIALIZATION_MAP.put(serialization.getType(), serialization);
        }
    }

    public static Serialization getSerialization(SerializationType serializationType) {
        return SERIALIZATION_MAP.get(serializationType);
    }

}
