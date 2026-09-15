package com.lien.api.appuser.feign;


import com.lien.api.appuser.domain.dto.UserEditReqDTO;
import com.lien.api.appuser.domain.vo.AppUserVO;
import domain.Result;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * C 端用户数据操作远程调用
 */
@FeignClient(contextId = "appUserFeignClient", value = "lien-admin", path = "/app_user")
public interface AppUserFeignClient {

    /**
     * 根据 openId 查询用户信息
     * @param openId 用户微信ID
     * @return C 端用户VO
     */
    @GetMapping("/open_id_find")
    Result<AppUserVO> findByOpenId(@RequestParam String openId);

    /**
     * 根据微信用户唯一标识注册 C 端用户
     * @param openId 用户唯一标识微信 ID
     * @return C 端用户 VO
     */
    @GetMapping("/register/openid")
    Result<AppUserVO> registerByOpenId(@RequestParam String openId);

    /**
     * 编辑C端用户
     * @param userEditReqDTO C 端用户 DTO
     * @return 所编辑用户的 ID
     */
    @PostMapping("/edit")
    Result<Long> edit(@RequestBody @Validated UserEditReqDTO userEditReqDTO);


}
