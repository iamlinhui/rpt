package cn.promptness.rpt.base.serialize.json;


import cn.promptness.rpt.base.serialize.api.ObjectOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

public class JsonDataOutputStream implements ObjectOutputStream {

    private final PrintWriter writer;

    public JsonDataOutputStream(OutputStream out) {
        this.writer = new PrintWriter(out);
    }

    @Override
    public void writeInt(final int v) throws IOException {
        writeObject(v);
    }

    @Override
    public void writeByte(final byte v) throws IOException {
        writeObject(v);
    }

    @Override
    public void writeBytes(final byte[] b) throws IOException {
        writer.println(new String(b));
    }

    @Override
    public void writeUTF(final String v) throws IOException {
        writeObject(v);
    }

    @Override
    public void writeObject(final Object obj) throws IOException {
        final String content = JacksonUtil.getJsonMapper().writeValueAsString(obj);
        writer.println(content);
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        this.writer.close();
    }
}
