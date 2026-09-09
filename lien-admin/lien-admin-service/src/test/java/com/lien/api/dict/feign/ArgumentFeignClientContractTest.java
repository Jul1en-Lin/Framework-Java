package com.lien.api.dict.feign;

import com.lien.api.dict.domain.dto.ArgumentDTO;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import feign.Feign;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ArgumentFeignClient 的客户端契约测试。
 * <p>
 * 用 JDK 内置 HttpServer 充当被调用方，通过 SpringMvcContract + SpringDecoder 构建真实的 Feign 客户端，
 * 验证消费方实际发出的请求（方法、路径、查询参数编码方式）以及响应体的反序列化结果。
 * 不依赖 Spring 容器、Nacos 和数据库。
 */
class ArgumentFeignClientContractTest {

    private HttpServer server;

    private ArgumentFeignClient client;

    /** 被 stub 的响应体；null 表示返回空响应体（模拟 Controller 返回 null） */
    private volatile String responseBody;

    private volatile String responseContentType = "application/json";

    /** 记录收到的请求：请求方法、原始 query、已解析的 query 参数 */
    private final List<RecordedRequest> recorded = new ArrayList<>();

    private record RecordedRequest(String method, String rawQuery, Map<String, List<String>> params) {
    }

    @BeforeEach
    void startServer() throws IOException {
        recorded.clear();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();

        client = Feign.builder()
            .contract(new SpringMvcContract())
            .decoder(new SpringDecoder(
                () -> new HttpMessageConverters(new MappingJackson2HttpMessageConverter())))
            .target(ArgumentFeignClient.class, "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String rawQuery = exchange.getRequestURI().getRawQuery();
        recorded.add(new RecordedRequest(exchange.getRequestMethod(), rawQuery, parseQuery(rawQuery)));

        byte[] body = responseBody == null ? new byte[0] : responseBody.getBytes(StandardCharsets.UTF_8);
        if (body.length > 0) {
            exchange.getResponseHeaders().add("Content-Type", responseContentType);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        } else {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        }
    }

    private static Map<String, List<String>> parseQuery(String rawQuery) {
        Map<String, List<String>> params = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            int index = pair.indexOf('=');
            String name = index < 0 ? pair : pair.substring(0, index);
            String value = index < 0 ? "" : pair.substring(index + 1);
            params.computeIfAbsent(decode(name), k -> new ArrayList<>()).add(decode(value));
        }
        return params;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    // ---------- GET /argument/key ----------

    @Test
    void getByConfigKey_sendsGetWithSingleQueryParam_andDecodesDto() {
        responseBody = "{\"id\":1,\"name\":\"超时时间\",\"configKey\":\"app.timeout\","
            + "\"value\":\"30\",\"remark\":\"秒\"}";

        ArgumentDTO result = client.getByConfigKey("app.timeout");

        assertEquals(1, recorded.size());
        RecordedRequest request = recorded.get(0);
        assertEquals("GET", request.method());
        assertEquals(List.of("app.timeout"), request.params().get("configKey"));
        assertEquals("configKey", request.params().keySet().iterator().next());

        assertEquals(1L, result.getId());
        assertEquals("app.timeout", result.getConfigKey());
        assertEquals("超时时间", result.getName());
        assertEquals("30", result.getValue());
        assertEquals("秒", result.getRemark());
    }

    @Test
    void getByConfigKey_encodesSpecialCharactersInQueryParam() {
        responseBody = "{\"configKey\":\"app.timeout\"}";

        client.getByConfigKey("a b&c=中");

        assertEquals(List.of("a b&c=中"), recorded.get(0).params().get("configKey"));
        // 原始 query 中必须已完成百分号编码，参数值不能被拆成多段
        assertTrue(recorded.get(0).rawQuery().contains("%20"), recorded.get(0).rawQuery());
        assertEquals(1, recorded.get(0).params().size());
    }

    @Test
    void getByConfigKey_whenServerReturnsEmptyBody_returnsNull() {
        responseBody = null;

        assertNull(client.getByConfigKey("no_such_key"));
        assertEquals(List.of("no_such_key"), recorded.get(0).params().get("configKey"));
    }

    // ---------- GET /argument/keys ----------

    /** 集合参数默认按 EXPLODED 展开成同名重复参数，提供方 @RequestParam List<String> 可正确绑定 */
    @Test
    void getByConfigKeys_expandsListIntoRepeatedQueryParams() {
        responseBody = "[{\"id\":1,\"configKey\":\"app.timeout\",\"name\":\"超时时间\",\"value\":\"30\"},"
            + "{\"id\":2,\"configKey\":\"app.retry\",\"name\":\"重试次数\",\"value\":\"3\"}]";

        List<ArgumentDTO> result = client.getByConfigKeys(List.of("app.timeout", "app.retry"));

        assertEquals(1, recorded.size());
        assertEquals("GET", recorded.get(0).method());
        assertEquals(List.of("app.timeout", "app.retry"), recorded.get(0).params().get("configKeys"));
        assertEquals(1, recorded.get(0).params().size());

        assertEquals(2, result.size());
        assertEquals("app.timeout", result.get(0).getConfigKey());
        assertEquals("30", result.get(0).getValue());
        assertEquals("app.retry", result.get(1).getConfigKey());
        assertEquals("3", result.get(1).getValue());
    }

    @Test
    void getByConfigKeys_whenServerReturnsEmptyBody_returnsNull() {
        responseBody = null;

        assertNull(client.getByConfigKeys(List.of("no_such_key")));
        assertEquals(List.of("no_such_key"), recorded.get(0).params().get("configKeys"));
    }
}
