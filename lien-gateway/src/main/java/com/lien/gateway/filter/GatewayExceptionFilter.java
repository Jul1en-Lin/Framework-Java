//package com.lien.gateway.filter;
//
//import domain.EnumCode;
//import domain.exception.ServiceException;
//import org.springframework.cloud.gateway.filter.GatewayFilterChain;
//import org.springframework.cloud.gateway.filter.GlobalFilter;
//import org.springframework.core.Ordered;
//import org.springframework.core.annotation.Order;
//import org.springframework.stereotype.Component;
//import org.springframework.web.server.ServerWebExchange;
//import org.springframework.web.servlet.NoHandlerFoundException;
//import reactor.core.publisher.Mono;
//
///**
// * 用于验证网关统一异常处理器是否能捕获过滤器异常。
// */
//@Component
//@Order(Ordered.HIGHEST_PRECEDENCE)
//public class GatewayExceptionFilter implements GlobalFilter {
//
//    @Override
//    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
//        throw new ServiceException("网关过滤器异常捕获测试", EnumCode.URL_NOT_FOUND.getCode());
//    }
//}
