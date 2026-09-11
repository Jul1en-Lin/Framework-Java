package service;

import com.lien.common.core.utils.ServletUtil;
import domain.constants.CacheConstants;
import domain.constants.SecurityConstants;
import domain.constants.TokenConstants;
import domain.dto.LoginUserDTO;
import domain.dto.TokenDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import utils.JwtUtil;
import utils.SecurityUtil;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Token 令牌服务类
 */
@Service
public class TokenService {

    @Autowired
    private RedisService redisService;

    // 在 Redis 缓存的前缀 key
    private static String cacheTokenPrefixKey = TokenConstants.LOGIN_TOKEN_KEY;
    // 过期时间单位（毫秒 -> 分钟）
    private static long MILLIS_MINUTES = 60 * 1000;

    /**
     * 根据登录用户信息生成 Token 令牌（需Id、UserFrom、UserName即可）
     * @param loginUserDTO 登录用户信息
     * @return 生成的 Token 令牌
     */
    public TokenDTO createToken(LoginUserDTO loginUserDTO) {
        String userToken = UUID.randomUUID().toString();
        loginUserDTO.setUserToken(userToken);
        // 缓存用户信息
        setAndCacheToken(loginUserDTO);

        // 生成 Token 令牌逻辑
        //  1 生成原始数据声明
        Map<String, Object> claims = new HashMap<>();
        claims.put(SecurityConstants.USER_KEY, userToken);
        claims.put(SecurityConstants.USER_ID, loginUserDTO.getUserId());
        claims.put(SecurityConstants.USER_FROM, loginUserDTO.getUserFrom());
        claims.put(SecurityConstants.USERNAME, loginUserDTO.getUserName());
        // 2 生成令牌
        String token = JwtUtil.createToken(claims);

        // 返回 TokenDTO
        TokenDTO result = new TokenDTO();
        result.setAccessToken(token);
        result.setExpires(CacheConstants.EXPIRATION * MILLIS_MINUTES);
        return result;
    }

    /**
     * 设置令牌有效期并缓存用户信息
     * @param loginUserDTO 登录用户信息
     */
    public void setAndCacheToken(LoginUserDTO loginUserDTO) {
        // 补充用户信息，设置登录时间和过期时间
        loginUserDTO.setLoginTime(System.currentTimeMillis());
        loginUserDTO.setExpireTime(loginUserDTO.getLoginTime() + CacheConstants.EXPIRATION * MILLIS_MINUTES);

        // 设置令牌有效期并缓存
        redisService.setCacheObject((cacheTokenPrefixKey + loginUserDTO.getUserToken()), loginUserDTO,
                CacheConstants.EXPIRATION, TimeUnit.MINUTES);
    }


    // --------- 获取用户信息 ---------
    /**
     * 根据令牌获取用户信息
     * @param token 令牌
     * @return 用户信息
     */
    public LoginUserDTO getUserInfoWithToken(String token) {
        LoginUserDTO result = null;
        if (StringUtils.isNotEmpty(token)) {
            String userToken = JwtUtil.getUserKey(token);
            result = redisService.getCacheObject((cacheTokenPrefixKey + userToken),LoginUserDTO.class);
        }
        return result;
    }

    /**
     * 根据请求来获取用户信息（再封装）
     * @param request 请求
     * @return 用户信息
     */
    public LoginUserDTO getUserInfoWithReq(HttpServletRequest request) {
        String token = SecurityUtil.getToken(request);
        return getUserInfoWithToken(token);
    }

    /**
     * 不传参数获取用户信息（再封装）
     * @return 用户信息
     */
    public LoginUserDTO getUserInfo() {
        HttpServletRequest request = ServletUtil.getRequest();
        return getUserInfoWithReq(request);
    }


    // --------- 超管权限 ---------

    /**
     * 超管设置新增用户信息
     * @param loginUserDTO 用户信息
     */
    public void setLoginUser(LoginUserDTO loginUserDTO) {
        if (loginUserDTO != null && StringUtils.isNotEmpty(loginUserDTO.getUserToken())) {
            setAndCacheToken(loginUserDTO);
        }
    }

    /**
     * 超管撤销用户登录状态
     * @param userId 用户ID
     * @param userFrom 用户来源
     */
    public void delLoginUser(Long userId, String userFrom) {
        // 集合所有的缓存 key
        Collection<String> tokenKeys = redisService.keys(cacheTokenPrefixKey + "*");

        // 查询缓存里所有含有的用户信息，若匹配 userId && userFrom 则删除缓存
        for (String tokenKey : tokenKeys) {
            LoginUserDTO loginUserDTO = redisService.getCacheObject(tokenKey, LoginUserDTO.class);
            if (loginUserDTO != null && loginUserDTO.getUserId().equals(userId)
                    && loginUserDTO.getUserFrom().equals(userFrom)) {
                // 删除缓存
                redisService.deleteObject(tokenKey);
            }
        }
    }


    /**
     * 验证并刷新令牌有效期 不到120分钟则刷新
     * @param loginUserDTO 用户信息
     */
    public void refreshToken(LoginUserDTO loginUserDTO) {
        long currentTime = System.currentTimeMillis();
        long expireTime = loginUserDTO.getExpireTime();
        if (expireTime - currentTime <= CacheConstants.REFRESH_TIME * MILLIS_MINUTES) {
            setAndCacheToken(loginUserDTO);
        }
    }

}
