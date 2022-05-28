package cn.promptness.rpt.base.serialize.protostuff;

/**
 * Protostuff can only serialize/deserialize POJOs, for those it can't deal with, use this Wrapper.
 */
public class Wrapper<T> {

    private final T data;

    Wrapper(T data) {
        this.data = data;
    }

    Object getData() {
        return data;
    }
}
