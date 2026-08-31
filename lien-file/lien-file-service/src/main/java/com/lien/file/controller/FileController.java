package com.lien.file.controller;

import com.lien.file.service.IFileService;
import domain.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController("/files")
public class FileController {
    @Autowired
    private IFileService fileService;

    @PostMapping("/upload")
    public Result<FileVO> upload(MultipartFile file) {
        // todo：鉴权
        FileVO fileVO = fileService.upload(file);
        return Result.success(fileVO);
    }

    @GetMapping("/sign")
    public Result<SignVO> getSign() {
        // todo：鉴权
        SignVO signVO = fileService.getSign();
        return Result.success(signVO);
    }
}
