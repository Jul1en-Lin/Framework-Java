package security;

import domain.EnumCode;
import domain.Result;
import domain.exception.ServiceException;
import handler.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final HttpServletRequest request = new MockHttpServletRequest("GET", "/test");

    @Test
    void shouldHandleServerException() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        Result<?> result = handler.handleServerException(
                new ServiceException(EnumCode.URL_NOT_FOUND), request, response);

        assertEquals(EnumCode.URL_NOT_FOUND.getCode(), result.getCode());
        assertEquals(EnumCode.URL_NOT_FOUND.getMsg(), result.getMsg());
        assertEquals(404, response.getStatus());
    }

    @Test
    void shouldHandleUnsupportedHttpMethod() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        Result<?> result = handler.handleHttpRequestMethodNotSupported(
                new HttpRequestMethodNotSupportedException("POST"), request, response);

        assertEquals(EnumCode.REQUEST_METHOD_NOT_SUPPORTED.getCode(), result.getCode());
        assertEquals(405, response.getStatus());
    }

    @Test
    void shouldHandleArgumentTypeMismatch() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
                "not-a-number", Integer.class, "id", null, null);

        Result<?> result = handler.handleMethodArgumentTypeMismatchException(exception, response);

        assertEquals(EnumCode.PARA_TYPE_MISMATCH.getCode(), result.getCode());
        assertEquals(400, response.getStatus());
    }

    @Test
    void shouldHandleMissingHandler() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        NoHandlerFoundException exception = new NoHandlerFoundException(
                "GET", "/missing", new HttpHeaders());

        Result<?> result = handler.handleNoHandlerFoundException(exception, response);

        assertEquals(EnumCode.URL_NOT_FOUND.getCode(), result.getCode());
        assertEquals(404, response.getStatus());
    }

    @Test
    void shouldHandleRuntimeException() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        Result<?> result = handler.handleRuntimeException(
                new IllegalStateException("bad state"), request, response);

        assertEquals(EnumCode.ERROR.getCode(), result.getCode());
        assertEquals(500, response.getStatus());
    }

    @Test
    void shouldHandleCheckedException() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        Result<?> result = handler.handleException(
                new Exception("bad request"), request, response);

        assertEquals(EnumCode.ERROR.getCode(), result.getCode());
        assertEquals(500, response.getStatus());
    }

    @Test
    void shouldFallbackToInternalServerErrorForMalformedCustomCode() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        Result<?> result = handler.handleServerException(
                new ServiceException("自定义异常", 42), request, response);

        assertEquals(42, result.getCode());
        assertEquals(500, response.getStatus());
    }

    @Test
    void shouldRegisterGlobalExceptionHandlerAsAutoConfiguration() throws IOException {
        ClassPathResource imports = new ClassPathResource(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");

        assertTrue(imports.exists());
        String content = new String(imports.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals("handler.GlobalExceptionHandler", content.trim());
    }
}
