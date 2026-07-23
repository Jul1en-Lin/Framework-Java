package utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import java.text.SimpleDateFormat;

@Slf4j
public class JsonUtil {

    private static ObjectMapper OBJECT_MAPPER;

    static {
        OBJECT_MAPPER = JsonMapper.builder().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                .configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)
                //不适用默认的dateTime进行序列化,使用JSR310的LocalDateTimeSerializer
                .configure(SerializationFeature.WRITE_DATE_KEYS_AS_TIMESTAMPS, false)
                .configure(MapperFeature.USE_ANNOTATIONS, false)
                //重点,这是序列化LocalDateTIme和LocalDate的必要配置,由Jackson-data-JSR310实现
                .addModule(new JavaTimeModule())
                //所有的日期格式都统一为以下的样式，即yyyy-MM-dd HH:mm:ss
                .defaultDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"))
                // 只针对非空的值进行序列化
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
        } catch (Exception e) {
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
        } catch (Exception e) {
            log.error("对象转换为格式化的JSON字符串时发生异常", e);
            return null;
        }
    }

    public static <T> T string2Obj(String str, Class<T> clazz) {
        if (!StringUtils.hasLength(str) || clazz == null) return null;
        try {
            return clazz.equals(String.class) ? (T) str :
                    OBJECT_MAPPER.readValue(str, clazz);
        } catch (Exception e) {
            log.error("JSON字符串转换为对象时发生异常", e);
            return null;
        }


    }
}
