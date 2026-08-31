package com.lien.file.service;

import org.springframework.web.multipart.MultipartFile;

public interface IFileService {
    FileVO upload(MultipartFile file);

    SignVO getSign();
}
