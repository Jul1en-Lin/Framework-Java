package utils.Json;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

/**
 * ‘@JsonTypeInfo 和 @JsonSubTypes 用于实现多态的序列化和反序列化，需搭配 OBJECT_MAPPER 的配置开关
 */
@Data
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        // 根据 "type" 字段的值来确定具体的子类类型，反序列化时指定 type 则可以正确反序列化为对应的子类
        // 只需在 JSON 中添加 "type" 字段即可
        property = "type"
)
// 标明了 TestAnimal 类的子类类型，反序列化时根据 "type" 字段的值来确定具体的子类类型
@JsonSubTypes({
        @JsonSubTypes.Type(value = TestDog.class, name = "dog"),
        @JsonSubTypes.Type(value = TestCat.class, name = "cat")
})
public class TestAnimal {
    // 指定序列化和反序列化时的 JSON 字段名，name 会被序列化为 "nickName" 字段名
    @JsonProperty("nickName")
    private String name;
}