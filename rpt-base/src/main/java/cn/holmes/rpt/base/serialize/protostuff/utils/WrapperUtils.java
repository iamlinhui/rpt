package cn.holmes.rpt.base.serialize.protostuff.utils;

import cn.holmes.rpt.base.serialize.protostuff.Wrapper;
import cn.holmes.rpt.base.serialize.protostuff.delegate.TimeDelegate;
import cn.holmes.rpt.base.serialize.protostuff.delegate.TimestampDelegate;
import io.protostuff.runtime.DefaultIdStrategy;
import io.protostuff.runtime.RuntimeEnv;

import java.math.BigDecimal;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Use WrapperUtils to wrap object to {@link Wrapper}
 */
public class WrapperUtils {

    private static final Set<Class<?>> WRAPPER_SET = new HashSet<>();

    static {
        if (RuntimeEnv.ID_STRATEGY instanceof DefaultIdStrategy) {
            ((DefaultIdStrategy) RuntimeEnv.ID_STRATEGY).registerDelegate(new TimeDelegate());
            ((DefaultIdStrategy) RuntimeEnv.ID_STRATEGY).registerDelegate(new TimestampDelegate());
        }

        WRAPPER_SET.add(Map.class);
        WRAPPER_SET.add(HashMap.class);
        WRAPPER_SET.add(TreeMap.class);
        WRAPPER_SET.add(Hashtable.class);
        WRAPPER_SET.add(SortedMap.class);
        WRAPPER_SET.add(LinkedHashMap.class);
        WRAPPER_SET.add(ConcurrentHashMap.class);

        WRAPPER_SET.add(List.class);
        WRAPPER_SET.add(ArrayList.class);
        WRAPPER_SET.add(LinkedList.class);

        WRAPPER_SET.add(Vector.class);

        WRAPPER_SET.add(Set.class);
        WRAPPER_SET.add(HashSet.class);
        WRAPPER_SET.add(TreeSet.class);
        WRAPPER_SET.add(BitSet.class);

        WRAPPER_SET.add(StringBuffer.class);
        WRAPPER_SET.add(StringBuilder.class);

        WRAPPER_SET.add(BigDecimal.class);
        WRAPPER_SET.add(Date.class);
        WRAPPER_SET.add(Calendar.class);
        WRAPPER_SET.add(Time.class);
        WRAPPER_SET.add(Timestamp.class);

        WRAPPER_SET.add(Wrapper.class);

    }

    /**
     * Determine if the object needs wrap
     *
     * @param clazz object type
     * @return need wrap
     */
    public static boolean needWrapper(Class<?> clazz) {
        return WrapperUtils.WRAPPER_SET.contains(clazz) || clazz.isArray() || clazz.isEnum();
    }

    /**
     * Determine if the object needs wrap
     *
     * @param obj object
     * @return need wrap
     */
    public static boolean needWrapper(Object obj) {
        return needWrapper(obj.getClass());
    }

}
