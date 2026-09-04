package com.lien.adminservice.domain.dto;

import com.lien.api.domain.dto.TencentMapBaseDTO;
import lombok.Data;

/**
 * 逆地址解析的结果
 */
@Data
public class GeoResultDTO extends TencentMapBaseDTO {
    /**
     * 结果信息
     */
    private AddrResultDTO result;
}
