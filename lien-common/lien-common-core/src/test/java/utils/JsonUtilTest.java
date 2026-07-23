package utils;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonUtilTest {

    @Test
    void shouldConvertObjectToJsonString() {
        User user = new User("Julien", 18, null);

        String json = JsonUtil.Obj2string(user);

        assertEquals("{\"name\":\"Julien\",\"age\":18}", json);
    }

    @Test
    void shouldReturnStringWithoutSerializingAgain() {
        String json = "{\"name\":\"Julien\"}";

        assertEquals(json, JsonUtil.Obj2string(json));
    }

    @Test
    void shouldReturnNullWhenConvertingNullObject() {
        assertNull(JsonUtil.Obj2string(null));
    }

    @Test
    void shouldConvertObjectToPrettyJsonString() {
        String json = JsonUtil.Obj2stringPretty(new User("Julien", 18, null));

        assertTrue(json.contains("\n"));
        assertTrue(json.contains("\"name\" : \"Julien\""));
        assertTrue(json.contains("\"age\" : 18"));
    }

    @Test
    void shouldReturnStringWithoutFormattingIt() {
        String json = "{\"name\":\"Julien\"}";

        assertEquals(json, JsonUtil.Obj2stringPretty(json));
    }

    @Test
    void shouldReturnNullWhenPrettyConvertingNullObject() {
        assertNull(JsonUtil.Obj2stringPretty(null));
    }

    @Test
    void shouldConvertJsonStringToObject() {
        User user = JsonUtil.string2Obj("{\"name\":\"Julien\",\"age\":18}", User.class);

        assertEquals(new User("Julien", 18, null), user);
    }

    @Test
    void shouldIgnoreUnknownJsonProperties() {
        User user = JsonUtil.string2Obj(
                "{\"name\":\"Julien\",\"age\":18,\"unknown\":true}", User.class);

        assertEquals(new User("Julien", 18, null), user);
    }

    @Test
    void shouldReturnStringDirectlyWhenTargetTypeIsString() {
        String json = "plain text";

        assertEquals(json, JsonUtil.string2Obj(json, String.class));
    }

    @Test
    void shouldSerializeAndDeserializeLocalDateTime() {
        Event event = new Event("meeting", LocalDateTime.of(2026, 7, 24, 10, 30));

        String json = JsonUtil.Obj2string(event);
        Event restored = JsonUtil.string2Obj(json, Event.class);

        assertEquals(event, restored);
    }

    @Test
    void shouldReturnNullForInvalidStringInput() {
        assertNull(JsonUtil.string2Obj(null, User.class));
        assertNull(JsonUtil.string2Obj("", User.class));
        assertNull(JsonUtil.string2Obj("   ", User.class));
        assertNull(JsonUtil.string2Obj("not-json", User.class));
    }

    @Test
    void shouldReturnNullForNullTargetType() {
        assertNull(JsonUtil.string2Obj("{}", null));
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
