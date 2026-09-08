package com.lien.adminservice.dict.controller;

import com.lien.adminservice.dict.service.ISysArgumentService;
import com.lien.api.dict.domain.dto.ArgumentAddReqDTO;
import com.lien.api.dict.domain.dto.ArgumentEditReqDTO;
import com.lien.api.dict.domain.dto.ArgumentListReqDTO;
import com.lien.api.dict.domain.vo.ArgumentVO;
import domain.Result;
import domain.vo.BasePageVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/argument")
public class ArgumentController {

    @Autowired
    private ISysArgumentService iSysArgumentService;
    /**
     * 新增参数
     * @param argumentAddReqDTO 新增参数请求DTO
     * @return Long
     */
    @PostMapping("/add")
    public Result<Long> add(@RequestBody @Validated ArgumentAddReqDTO argumentAddReqDTO) {
        return Result.success(iSysArgumentService.add(argumentAddReqDTO));
    }


    /**
     * 参数列表
     * @param argumentListReqDTO 查看参数DTO
     * @return 分页展示参数列表
     */
    @GetMapping("/list")
    public Result<BasePageVO<ArgumentVO>> list(ArgumentListReqDTO argumentListReqDTO) {
        return Result.success(iSysArgumentService.list(argumentListReqDTO));
    }

    /**
     * 编辑参数
     * @param argumentEditReqDTO 编辑参数DTO
     * @return 参数的自增 Id
     */
    @PostMapping("/edit")
    public Result<Long> edit(@RequestBody @Validated ArgumentEditReqDTO argumentEditReqDTO) {
        return Result.success(iSysArgumentService.edit(argumentEditReqDTO));
    }
}
