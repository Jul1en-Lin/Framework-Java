package com.lien.adminservice.dict.service;

import com.lien.api.dict.domain.dto.ArgumentAddReqDTO;
import com.lien.api.dict.domain.dto.ArgumentEditReqDTO;
import com.lien.api.dict.domain.dto.ArgumentListReqDTO;
import com.lien.api.dict.domain.vo.ArgumentVO;
import domain.vo.BasePageVO;

/**
 * 参数服务接口
 */
public interface ISysArgumentService {

    /**
     * 新增参数
     * @param argumentAddReqDTO 添加参数DTO
     * @return 参数的自增 Id
     */
    Long add(ArgumentAddReqDTO argumentAddReqDTO);

    /**
     * 参数列表
     * @param argumentListReqDTO 查看参数DTO
     * @return 分页展示参数列表
     */
    BasePageVO<ArgumentVO> list(ArgumentListReqDTO argumentListReqDTO);

    /**
     * 编辑参数
     * @param argumentEditReqDTO 编辑参数DTO
     * @return 参数的自增 Id
     */
    Long edit(ArgumentEditReqDTO argumentEditReqDTO);
}
