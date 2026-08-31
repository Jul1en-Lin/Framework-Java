package utils.Json;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;
import com.lien.common.core.utils.JsonUtil;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonUtilTest {

    /**
     * 测试方法：JsonUtil.Obj2string(T)
     * 测试内容：将自定义对象序列化为 JSON 字符串，并忽略值为 null 的属性。
     */
    @Test
    void shouldConvertObjectToJsonString() {
        User user = new User("Julien", 18, null);

        String json = JsonUtil.Obj2string(user);

        assertEquals("{\"name\":\"Julien\",\"age\":18}", json);
    }

    /**
     * 测试方法：JsonUtil.Obj2string(T)
     * 测试内容：传入字符串时直接返回原字符串，不重复进行 JSON 序列化。
     */
    @Test
    void shouldReturnStringWithoutSerializingAgain() {
        String json = "{\"name\":\"Julien\"}";

        assertEquals(json, JsonUtil.Obj2string(json));
    }

    /**
     * 测试方法：JsonUtil.Obj2string(T)
     * 测试内容：传入 null 时返回 null。
     */
    @Test
    void shouldReturnNullWhenConvertingNullObject() {
        assertNull(JsonUtil.Obj2string(null));
    }

    /**
     * 测试方法：JsonUtil.Obj2stringPretty(T)
     * 测试内容：将自定义对象序列化为格式化的 JSON 字符串。
     */
    @Test
    void shouldConvertObjectToPrettyJsonString() {
        String json = JsonUtil.Obj2stringPretty(new User("Julien", 18, null));

        assertTrue(json.contains("\n"));
        assertTrue(json.contains("\"name\" : \"Julien\""));
        assertTrue(json.contains("\"age\" : 18"));
    }

    /**
     * 测试方法：JsonUtil.Obj2stringPretty(T)
     * 测试内容：传入字符串时直接返回原字符串，不重复进行格式化。
     */
    @Test
    void shouldReturnStringWithoutFormattingIt() {
        String json = "{\"name\":\"Julien\"}";

        assertEquals(json, JsonUtil.Obj2stringPretty(json));
    }

    /**
     * 测试方法：JsonUtil.Obj2stringPretty(T)
     * 测试内容：传入 null 时返回 null。
     */
    @Test
    void shouldReturnNullWhenPrettyConvertingNullObject() {
        assertNull(JsonUtil.Obj2stringPretty(null));
    }

    /**
     * 测试方法：JsonUtil.string2Obj(String, Class<T>)
     * 测试内容：将 JSON 字符串反序列化为自定义对象。
     */
    @Test
    void shouldConvertJsonStringToObject() {
        User user = JsonUtil.string2Obj("{\"name\":\"Julien\",\"age\":18}", User.class);

        assertEquals(new User("Julien", 18, null), user);
    }

    /**
     * 测试方法：JsonUtil.string2Obj(String, Class<T>)
     * 测试内容：目标对象不存在的 JSON 属性不会导致反序列化失败。
     */
    @Test
    void shouldIgnoreUnknownJsonProperties() {
        User user = JsonUtil.string2Obj(
                "{\"name\":\"Julien\",\"age\":18,\"unknown\":true}", User.class);

        assertEquals(new User("Julien", 18, null), user);
    }

    /**
     * 测试方法：JsonUtil.string2Obj(String, Class<T>)
     * 测试内容：目标类型为 String 时直接返回输入字符串。
     */
    @Test
    void shouldReturnStringDirectlyWhenTargetTypeIsString() {
        String json = "plain text";

        assertEquals(json, JsonUtil.string2Obj(json, String.class));
    }

    /**
     * 测试方法：JsonUtil.Obj2string(T) 和 JsonUtil.string2Obj(String, Class<T>)
     * 测试内容：LocalDateTime 在序列化和反序列化后保持原值。
     */
    @Test
    void shouldSerializeAndDeserializeLocalDateTime() {
        Event event = new Event("meeting", LocalDateTime.of(2026, 7, 24, 10, 30));

        String json = JsonUtil.Obj2string(event);
        Event restored = JsonUtil.string2Obj(json, Event.class);

        assertEquals(event, restored);
    }

    /**
     * 测试方法：JsonUtil.string2Obj(String, Class<T>)
     * 测试内容：输入为 null、空字符串、空白字符串或非法 JSON 时返回 null。
     */
    @Test
    void shouldReturnNullForInvalidStringInput() {
        assertNull(JsonUtil.string2Obj(null, User.class));
        assertNull(JsonUtil.string2Obj("", User.class));
        assertNull(JsonUtil.string2Obj("   ", User.class));
        assertNull(JsonUtil.string2Obj("not-json", User.class));
    }

    /**
     * 测试方法：JsonUtil.string2Obj(String, Class<T>)
     * 测试内容：目标类型为 null 时返回 null。
     */
    @Test
    void shouldReturnNullForNullTargetType() {
        assertNull(JsonUtil.string2Obj("{}", (Class<User>) null));
    }

    /**
     * 测试方法：JsonUtil.string2List(String, Class<T>)
     * 测试内容：将 JSON 数组反序列化为自定义对象的 List。
     */
    @Test
    void shouldConvertJsonStringToListOfCustomObjects() {
        String json = "[{\"name\":\"Julien\",\"age\":18},{\"name\":\"Luna\",\"age\":20}]";

        List<User> users = JsonUtil.string2List(json, User.class);

        assertEquals(List.of(
                new User("Julien", 18, null),
                new User("Luna", 20, null)), users);

        System.out.println("string2List 转换结果：" + users);
    }

    /**
     * 测试方法：JsonUtil.string2Map(String, Class<T>)
     * 测试内容：将 JSON 对象反序列化为值类型为自定义对象的 Map。
     */
    @Test
    void shouldConvertJsonStringToMapOfCustomObjects() {
        String json = "{\"admin\":{\"name\":\"Julien\",\"age\":18},"
                + "\"guest\":{\"name\":\"Luna\",\"age\":20}}";

        Map<String, User> users = JsonUtil.string2Map(json, User.class);

        assertEquals(new User("Julien", 18, null), users.get("admin"));
        assertEquals(new User("Luna", 20, null), users.get("guest"));

        System.out.println("string2Map 转换结果：" + users);
    }

    /**
     * 测试方法：JsonUtil.string2Obj(String, TypeReference<T>)
     * 测试内容：利用 TypeReference 保留泛型信息，反序列化嵌套的 Map<String, List<User>>。
     */
    @Test
    void shouldConvertJsonStringToNestedGenericObjects() {
        String json = "{\"developers\":[{\"name\":\"Julien\",\"age\":18}],"
                + "\"designers\":[{\"name\":\"Luna\",\"age\":20}]}";

        Map<String, List<User>> users = JsonUtil.string2Obj(
                json, new TypeReference<Map<String, List<User>>>() {
                });

        assertEquals(List.of(new User("Julien", 18, null)), users.get("developers"));
        assertEquals(List.of(new User("Luna", 20, null)), users.get("designers"));

        System.out.println("string2Obj(TypeReference) 转换结果：" + users);
    }

    /**
     * 测试方法：JsonUtil.string2List(String, Class<T>)、JsonUtil.string2Map(String, Class<T>)、
     * JsonUtil.string2Obj(String, TypeReference<T>)
     * 测试内容：输入字符串或目标类型无效时返回 null。
     */
    @Test
    void shouldReturnNullForInvalidGenericConversionInputs() {
        assertNull(JsonUtil.string2List(null, User.class));
        assertNull(JsonUtil.string2List("[]", null));
        assertNull(JsonUtil.string2Map(null, User.class));
        assertNull(JsonUtil.string2Map("{}", null));
        assertNull(JsonUtil.string2Obj(null, new TypeReference<List<User>>() {
        }));
        assertNull(JsonUtil.string2Obj("{}", (TypeReference<List<User>>) null));
    }

    @Test
    void shouldGetLinkedHashMapWithoutTypeInfo() {
        String json = "[{\"name\":\"Julien\",\"age\":18}]";

        // 直接传 List.class，Jackson 不知道元素类型
        List result = JsonUtil.string2Obj(json, List.class);

        // 拿到的不是 User，而是 LinkedHashMap
        assertFalse(result.get(0) instanceof User);
        assertTrue(result.get(0) instanceof LinkedHashMap);
        System.out.println(result);
    }

    static class User {
        private String name;
        private int age;
        private String nickname;

        public User() {
        }

        User(String name, int age, String nickname) {
            this.name = name;
            this.age = age;
            this.nickname = nickname;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        public String getNickname() {
            return nickname;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof User user)) return false;
            return age == user.age
                    && java.util.Objects.equals(name, user.name)
                    && java.util.Objects.equals(nickname, user.nickname);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(name, age, nickname);
        }

        @Override
        public String toString() {
            return "User{" +
                    "name='" + name + '\'' +
                    ", age=" + age +
                    ", nickname='" + nickname + '\'' +
                    '}';
        }
    }

    static class Event {
        private String name;
        private LocalDateTime startTime;

        public Event() {
        }

        Event(String name, LocalDateTime startTime) {
            this.name = name;
            this.startTime = startTime;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public LocalDateTime getStartTime() {
            return startTime;
        }

        public void setStartTime(LocalDateTime startTime) {
            this.startTime = startTime;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Event event)) return false;
            return java.util.Objects.equals(name, event.name)
                    && java.util.Objects.equals(startTime, event.startTime);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(name, startTime);
        }
    }
}
