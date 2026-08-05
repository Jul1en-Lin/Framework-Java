package handler;

import domain.EnumCode;
import domain.Result;
import domain.exception.ServiceException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理业务服务异常。
     *
     * 捕获自定义异常
     */
    @ExceptionHandler(ServiceException.class)
    public Result<?> handleServerException(ServiceException e,
                                           HttpServletRequest request,
                                           HttpServletResponse response) {
        log.error("请求地址'{}'发生业务异常", request.getRequestURI(), e);
        setResponseCode(response, e.getCode());
        return Result.fail(e.getCode(), e.getMsg());
    }

    /**
     * 请求方式不支持
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<?> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException e,
                                                          HttpServletRequest request,
                                                          HttpServletResponse response) {
        log.error("请求地址'{}'不支持'{}'请求", request.getRequestURI(), e.getMethod());
        setResponseCode(response, EnumCode.REQUEST_METHOD_NOT_SUPPORTED.getCode());
        return Result.fail(EnumCode.REQUEST_METHOD_NOT_SUPPORTED.getCode(), EnumCode.REQUEST_METHOD_NOT_SUPPORTED.getMsg());
    }

    /**
     * 请求参数类型不匹配
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<?> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e,
                                                               HttpServletResponse response) {
        log.error("类型不匹配异常", e);
        setResponseCode(response, EnumCode.PARA_TYPE_MISMATCH.getCode());
        return Result.fail(EnumCode.PARA_TYPE_MISMATCH.getCode(), EnumCode.PARA_TYPE_MISMATCH.getMsg());
    }

    /**
     * 没有找到请求处理器
     *
     * <p>Spring Boot 3.0.2 使用 Spring Framework 6.0.x，这个版本没有
     * {@code NoResourceFoundException}，因此这里处理 {@code NoHandlerFoundException}</p>
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public Result<?> handleNoHandlerFoundException(NoHandlerFoundException e,
                                                   HttpServletResponse response) {
        log.error("url未找到异常", e);
        setResponseCode(response, EnumCode.URL_NOT_FOUND.getCode());
        return Result.fail(EnumCode.URL_NOT_FOUND.getCode(), EnumCode.URL_NOT_FOUND.getMsg());
    }

    /**
     * 拦截运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<?> handleRuntimeException(RuntimeException e,
                                            HttpServletRequest request,
                                            HttpServletResponse response) {
        log.error("请求地址'{}'发生运行时异常", request.getRequestURI(), e);
        setResponseCode(response, EnumCode.ERROR.getCode());
        return Result.fail(EnumCode.ERROR.getCode(), EnumCode.ERROR.getMsg());
    }

    /**
     * 系统异常兜底处理
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e,
                                     HttpServletRequest request,
                                     HttpServletResponse response) {
        log.error("请求地址'{}'发生异常", request.getRequestURI(), e);
        setResponseCode(response, EnumCode.ERROR.getCode());
        return Result.fail(EnumCode.ERROR.getCode(), EnumCode.ERROR.getMsg());
    }

    // 设置 HTTP 响应状态码为 EnumCode 错误码的前三位
    private void setResponseCode(HttpServletResponse response, Integer errorCode) {
        response.setStatus(toHttpStatusCode(errorCode));
    }

    // 截取错误码的前三位作为 HTTP 状态码，如果无法截取或不合法，则返回 500。
    private int toHttpStatusCode(Integer errorCode) {
        String codeText = String.valueOf(errorCode);
        if (errorCode == null || codeText.length() < 3) {
            log.error("错误码 {} 不存在或无法截取前三位，默认返回 500", errorCode);
            return HttpStatus.INTERNAL_SERVER_ERROR.value();
        }

        return Integer.parseInt(codeText.substring(0, 3));
    }
}
