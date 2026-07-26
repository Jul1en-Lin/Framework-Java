package utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class JsonUtil {

    private static ObjectMapper OBJECT_MAPPER;

    static {
        OBJECT_MAPPER = JsonMapper.builder()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)
                .configure(SerializationFeature.WRITE_DATE_KEYS_AS_TIMESTAMPS, false)
                .configure(MapperFeature.USE_ANNOTATIONS, false)
                .addModule(new JavaTimeModule())
                .defaultDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"))
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .build();
    }

    /**
     * 将对象转换为JSON字符串
     */
    public static <T> String Obj2string(T obj) {
        if (obj == null) return null;
        try {
            return obj instanceof String ? (String) obj : OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("对象转换为JSON字符串时发生异常", e);
            return null;
        }
    }

    /**
     * 将对象转换为格式化的JSON字符串
     */
    public static <T> String Obj2stringPretty(T obj) {
        if (obj == null) return null;
        try {
            return obj instanceof String ? (String) obj :
                    OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("对象转换为格式化的JSON字符串时发生异常", e);
            return null;
        }
    }

    /**
     * JSON字符串转换为对象
     */
    public static <T> T string2Obj(String str, Class<T> clazz) {
        if (!StringUtils.hasLength(str) || clazz == null) return null;
        try {
            return clazz.equals(String.class) ? (T) str :
                    OBJECT_MAPPER.readValue(str, clazz);
        } catch (JsonProcessingException e) {
            log.error("JSON字符串转换为对象时发生异常", e);
            return null;
        }
    }

    /**
     * 泛型擦除
     * JSON字符串转换为自定义对象的List
     */
    public static <T> List<T> string2List(String str, Class<T> clazz) {
        if (!StringUtils.hasLength(str) || clazz == null) return null;
        JavaType javaType = OBJECT_MAPPER.getTypeFactory().constructParametricType(List.class, clazz);
        try {
            return OBJECT_MAPPER.readValue(str, javaType);
        } catch (JsonProcessingException e) {
            log.error("JSON字符串转换为对象列表时发生异常", e);
            return null;
        }
    }

    /**
     * 泛型擦除
     * JSON字符串转换为自定义对象的Map
     */
    public static <T> Map<String,T> string2Map(String str, Class<T> clazz) {
        if (!StringUtils.hasLength(str) || clazz == null) return null;
        JavaType javaType = OBJECT_MAPPER.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, clazz);
        try {
            return OBJECT_MAPPER.readValue(str, javaType);
        } catch (JsonProcessingException e) {
            log.error("JSON字符串转换为对象列表时发生异常", e);
            return null;
        }
    }

    /**
     * 泛型擦除嵌套
     * JSON字符串转换为嵌套的自定义对象，如 List<Map<String, T>> 或 Map<String, List<T>>
     */
    public static <T> T string2Obj(String str, TypeReference<T> typeRef) {
        if (!StringUtils.hasLength(str) || typeRef == null) return null;
        try {
            return OBJECT_MAPPER.readValue(str, typeRef);
        } catch (JsonProcessingException e) {
            log.error("JSON字符串转换为对象时发生异常", e);
            return null;
        }
    }
}
