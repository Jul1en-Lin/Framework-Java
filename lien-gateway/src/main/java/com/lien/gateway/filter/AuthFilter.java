package com.lien.gateway.filter;


import com.lien.common.core.utils.ServletUtil;
import com.lien.common.core.utils.StringUtil;
import com.lien.gateway.config.IgnoreWhiteProperties;
import domain.EnumCode;
import domain.constants.SecurityConstants;
import domain.constants.TokenConstants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import service.RedisService;
import utils.JwtUtil;
import utils.SecurityUtil;

/**
 * 网关鉴权拦截器
 */
@Slf4j
@Component
public class AuthFilter implements GlobalFilter, Ordered {

    // 在 Redis 缓存的前缀 key
    private static String cacheTokenPrefixKey = TokenConstants.LOGIN_TOKEN_KEY;

    @Autowired
    private IgnoreWhiteProperties ignoreWhiteProperties;

    @Autowired
    private RedisService redisService;

    /**
     * 过滤逻辑
     * @param exchange the current server exchange
     * @param chain    provides a way to delegate to the next filter
     * @return
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 获取请求
        ServerHttpRequest request = exchange.getRequest();
        // 若匹配到白名单则放行
        String url = request.getURI().getPath();
        if (StringUtil.matchesListUrl(url, ignoreWhiteProperties.getWhites())) {
            return chain.filter(exchange);
        }

        // 获取请求中的 Token 令牌
        String token = getReqToken(request);
        if (StringUtils.isEmpty(token)) {
            return unauthorizedResponse(exchange, EnumCode.TOKEN_EMPTY);
        }
        // 根据 Token 令牌获取有效信息
        Claims claims;
        try {
            claims = JwtUtil.parseToken(token);
        } catch (JwtException | IllegalArgumentException e) {
            return unauthorizedResponse(exchange, EnumCode.TOKEN_INVALID);
        }
        if (claims == null) {
            return unauthorizedResponse(exchange, EnumCode.TOKEN_INVALID);
        }

        // 检查缓存中是否包含对应的用户信息，没有则拦截
        String userToken = JwtUtil.getUserKey(token);
        if (!redisService.hasKey(cacheTokenPrefixKey + userToken)) {
            return unauthorizedResponse(exchange, EnumCode.LOGIN_STATUS_OVERTIME);
        }

        // 从解析后的 claims 中获取用户数据信息
        String userId = JwtUtil.getUserId(claims);
        String userName = JwtUtil.getUserName(claims);
        String userFrom = JwtUtil.getUserFrom(claims);
        if (StringUtils.isEmpty(userId) || StringUtils.isEmpty(userName)) {
            return unauthorizedResponse(exchange, EnumCode.TOKEN_CHECK_FAILED);
        }

        // 设置信息到请求头
        // 表示基于原请求创建请求构建器，然后通过它添加 Header 或其他元素
        ServerHttpRequest.Builder mutate = request.mutate();
        addHeader(mutate, SecurityConstants.USER_KEY, userToken);
        addHeader(mutate, SecurityConstants.USER_ID, userId);
        addHeader(mutate, SecurityConstants.USERNAME, userName);
        addHeader(mutate, SecurityConstants.USER_FROM, userFrom);

        // 加上 header 信息之后交给后续过滤器处理

        // 1. 根据 mutate 中添加的 Header，构建一个新的 Request
        ServerHttpRequest newRequest = mutate.build();
        // 2. 基于原 exchange，替换成新的 Request，构建新的 Exchange
        ServerWebExchange newExchange = exchange.mutate().request(newRequest)
                .build();
        // 把新的 Exchange 交给后续过滤器
        return chain.filter(newExchange);
    }

    /**
     * 决定各个拦截器的执行顺序，因为可能有多个拦截器
     * 值越小优先级越高
     * @return 执行顺序
     */
    @Override
    public int getOrder() {
        return -1;
    }

    /**
     * 获取请求中的 Token 令牌
     * @param request 请求对象
     * @return Token 令牌
     */
    private String getReqToken(ServerHttpRequest request) {
        String token = request.getHeaders().getFirst(SecurityConstants.AUTHENTICATION);
        return SecurityUtil.replaceTokenIfExistPrefix(token);
    }


    /**
     * 设置鉴权异常的响应结果
     * @param exchange ServerWebExchange
     * @param enumCode 结果码
     * @return Mono<Void> 请求处理是否完成的信号
     */
    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, EnumCode enumCode) {
        log.error("鉴权处理异常,请求路径:{}", exchange.getRequest().getPath());
        // 根据枚举码获取前3位作为http状态码
        int retCode = Integer.parseInt(String.valueOf(enumCode.getCode()).substring(0, 3));
        // 设置 webflux Response 响应
        return ServletUtil.webFluxResponseWriter(exchange.getResponse(), HttpStatus.valueOf(retCode),
                enumCode.getMsg(), enumCode.getCode());
    }


    /**
     * 构建请求头 header
     * @param mutate 基于当前对象创建一个可修改的构建器（Builder）
     * @param name key
     * @param value 值
     */
    private void addHeader(ServerHttpRequest.Builder mutate, String name, Object value) {
        if (value == null) return;
        // 对 value 值进行字符串化和编码处理，统一为字符串传输
        String encodedValue = ServletUtil.encodeFormValue(value.toString());
        // 添加报头值
        mutate.header(name, encodedValue);
    }
}
