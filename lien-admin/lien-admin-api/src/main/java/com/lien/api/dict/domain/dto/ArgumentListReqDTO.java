package com.lien.api.dict.domain.dto;

import domain.dto.BasePageReqDTO;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 参数列表请求 DTO
 * 需分页展示，extend BasePage
 */
@Data
public class ArgumentListReqDTO extends BasePageReqDTO {

    /**
     * 参数业务主键
     */
    @NotBlank(message = "参数业务主键不能为空")
    private String configKey;

    /**
     * 参数名称
     */
    @NotBlank(message = "参数名称不能为空")
    private String name;

}
