package domain.exception;

import domain.EnumCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceExceptionTest {

    @Test
    void shouldBuildExceptionFromEnumCode() {
        ServiceException exception = new ServiceException(EnumCode.URL_NOT_FOUND);

        assertEquals(EnumCode.URL_NOT_FOUND.getCode(), exception.getCode());
        assertEquals(EnumCode.URL_NOT_FOUND.getMsg(), exception.getMessage());
    }

    @Test
    void shouldUseDefaultErrorCodeWhenOnlyMessageIsProvided() {
        ServiceException exception = new ServiceException("服务器异常");

        assertEquals(EnumCode.ERROR.getCode(), exception.getCode());
        assertEquals("服务器异常", exception.getMessage());
    }

    @Test
    void shouldKeepCustomCodeAndMessage() {
        ServiceException exception = new ServiceException("自定义异常", 499999);

        assertEquals(499999, exception.getCode());
        assertEquals("自定义异常", exception.getMessage());
    }
}
