package com.lien.gateway.handler;

import domain.EnumCode;
import domain.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayExceptionHandlerTest {

    private final GatewayExceptionHandler handler = new GatewayExceptionHandler();

    @Test
    void shouldWriteServerExceptionResponse() {
        MockServerWebExchange exchange = exchange("/api/users");

        StepVerifier.create(handler.handle(exchange, new ServiceException(EnumCode.URL_NOT_FOUND)))
                .verifyComplete();

        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
        assertResponseBodyContains(exchange, "\"code\":404001", "url未找到");
    }

    @Test
    void shouldConvertNotFoundResponseStatusExceptionToServiceNotFound() {
        MockServerWebExchange exchange = exchange("/missing");

        StepVerifier.create(handler.handle(exchange,
                        new ResponseStatusException(HttpStatus.NOT_FOUND)))
                .verifyComplete();

        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
        assertResponseBodyContains(exchange, "\"code\":404000", "服务未找到");
    }

    @Test
    void shouldWriteInternalServerErrorForUnexpectedException() {
        MockServerWebExchange exchange = exchange("/api/error");

        StepVerifier.create(handler.handle(exchange, new IllegalStateException("bad state")))
                .verifyComplete();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exchange.getResponse().getStatusCode());
        assertResponseBodyContains(exchange, "\"code\":500000", "服务繁忙请稍后重试");
    }

    @Test
    void shouldPropagateExceptionWhenResponseIsCommitted() {
        MockServerWebExchange exchange = exchange("/api/committed");
        exchange.getResponse().setComplete().block();

        RuntimeException exception = new RuntimeException("already committed");

        StepVerifier.create(handler.handle(exchange, exception))
                .expectErrorMatches(error -> error == exception)
                .verify();
    }

    private MockServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path));
    }

    private void assertResponseBodyContains(MockServerWebExchange exchange, String code, String message) {
        String body = exchange.getResponse().getBodyAsString().block();
        assertTrue(body != null && body.contains(code));
        assertTrue(body != null && body.contains(message));
    }
}
