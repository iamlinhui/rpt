package cn.promptness.rpt.base.utils;


import java.io.Serializable;
import java.util.Objects;

/**
 * A convenience class to represent name-value pairs.
 *
 * @author lynn
 * @date 2021/12/22 11:38
 * @since v1.0.0
 */
public class Pair<K, V> implements Serializable {

    private static final long serialVersionUID = 2717434894799906525L;

    private final K key;

    private final V value;

    public K getKey() {
        return key;
    }

    public V getValue() {
        return value;
    }

    public Pair(K key, V value) {
        this.key = key;
        this.value = value;
    }


    @Override
    public String toString() {
        return key + "=" + value;
    }


    @Override
    public int hashCode() {
        return key.hashCode() * 13 + (value == null ? 0 : value.hashCode());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof Pair) {
            @SuppressWarnings("rawtypes")
            Pair pair = (Pair) o;
            if (!Objects.equals(key, pair.key)) {
                return false;
            }
            return Objects.equals(value, pair.value);
        }
        return false;
    }
}
