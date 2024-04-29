package cn.holmes.rpt.base.serialize.json;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;

import static com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping.NON_FINAL;

public class JacksonUtil {

    /**
     * used for take the bean actual type with json string; for de-serializing the json string into real type bean.
     */
    private static final String TYPE_AS_JSON_PROPERTY = "typeAsJsonProperty";

    private static final JsonMapper MAPPER = new JsonMapper();

    static {
        final boolean takeTypeAsProperty = Boolean.getBoolean(TYPE_AS_JSON_PROPERTY);
        if (takeTypeAsProperty) {
            MAPPER.activateDefaultTypingAsProperty(new PolymorphicTypeValidator() {
                @Override
                public Validity validateBaseType(final MapperConfig<?> config,
                                                 final JavaType baseType) {
                    return Validity.ALLOWED;
                }

                @Override
                public Validity validateSubClassName(final MapperConfig<?> config,
                                                     final JavaType baseType,
                                                     final String subClassName) throws JsonMappingException {
                    return Validity.ALLOWED;
                }

                @Override
                public Validity validateSubType(final MapperConfig<?> config,
                                                final JavaType baseType,
                                                final JavaType subType) throws JsonMappingException {
                    return Validity.ALLOWED;
                }
            }, NON_FINAL, null);
        }
        MAPPER.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public static JsonMapper getJsonMapper() {
        return MAPPER;
    }
}
