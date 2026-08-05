package com.lien.gateway.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import domain.EnumCode;
import domain.Result;
import domain.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import org.springframework.web.servlet.NoHandlerFoundException;


/**
 * Gateway 的统一异常处理器。
 */
@Order(-1)
@Configuration
@Slf4j
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 处理器
     *
     * @param exchange ServerWebExchange
     * @param ex 异常信息
     * @return 无
     */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        //响应已经提交到客户端，无法再对这个响应进行常规的异常处理修改了，直接返回一个包含原始异常ex的Mono.error(ex)
        if (response.isCommitted()) {
            return Mono.error(ex);
        }
        int retCode = EnumCode.ERROR.getCode();
        String retMsg = EnumCode.ERROR.getMsg();
        if (ex instanceof NoHandlerFoundException) {
            retCode = EnumCode.SERVICE_NOT_FOUND.getCode();
            retMsg = EnumCode.SERVICE_NOT_FOUND.getMsg();
        } else if (ex instanceof ServiceException) {
            retMsg = ((ServiceException) ex).getMsg();
            retCode = ((ServiceException) ex).getCode();
        }

        int httpCode = Integer.parseInt(String.valueOf(retCode).substring(0,3));

        log.error("[网关异常处理]请求路径:{},异常信息:{}", exchange.getRequest().
                getPath(), ex.getMessage());

        return webFluxResponseWriter(response, HttpStatus.valueOf(httpCode),retMsg, retCode);
    }

    /**
     * 临时 webFluxResponseWriter 方法，后续可以考虑提取到公共工具类中
     * todo
     */
    private Mono<Void> webFluxResponseWriter(ServerHttpResponse response,
                                              HttpStatus status,
                                              String message,
                                              int code) {
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try {
            byte[] body = OBJECT_MAPPER.writeValueAsBytes(Result.fail(code, message));
            DataBuffer dataBuffer = response.bufferFactory().wrap(body);
            return response.writeWith(Mono.just(dataBuffer));
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }
}
