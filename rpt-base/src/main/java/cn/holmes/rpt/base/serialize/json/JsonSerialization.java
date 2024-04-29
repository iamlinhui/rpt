package cn.holmes.rpt.base.serialize.json;

import cn.holmes.rpt.base.serialize.api.ObjectInputStream;
import cn.holmes.rpt.base.serialize.api.ObjectOutputStream;
import cn.holmes.rpt.base.serialize.api.Serialization;
import cn.holmes.rpt.base.serialize.api.SerializationType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class JsonSerialization implements Serialization {

    @Override
    public SerializationType getType() {
        return SerializationType.JSON;
    }

    @Override
    public ObjectOutputStream serialize(final OutputStream output) throws IOException {
        return new JsonDataOutputStream(output);
    }

    @Override
    public ObjectInputStream deserialize(final InputStream input) throws IOException {
        return new JsonDataInputStream(input);
    }
}
