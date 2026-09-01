package com.lien.file.controller;

import com.lien.common.core.utils.BeanUtil;
import com.lien.file.entity.dto.FileDTO;
import com.lien.file.entity.dto.SignDTO;
import com.lien.file.entity.vo.FileVO;
import com.lien.file.entity.vo.SignVO;
import com.lien.file.service.IFileService;
import domain.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/source")
public class FileController {
    @Autowired
    private IFileService fileService;

    @PostMapping("/upload")
    public Result<FileVO> upload(MultipartFile file) {
        // todo：鉴权
        FileDTO fileDTO = fileService.upload(file);
        FileVO fileVO = new FileVO();
        BeanUtil.copyProperties(fileDTO, fileVO);
        return Result.success(fileVO);
    }

    @GetMapping("/sign")
    public Result<SignVO> getSign() {
        // todo：鉴权
        SignDTO sign = fileService.getSign();
        SignVO signVO = new SignVO();
        BeanUtil.copyProperties(sign, signVO);
        return Result.success(signVO);
    }
}
