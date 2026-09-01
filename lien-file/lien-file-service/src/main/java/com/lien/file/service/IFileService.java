package com.lien.file.service;

import com.lien.file.entity.dto.FileDTO;
import com.lien.file.entity.dto.SignDTO;
import org.springframework.web.multipart.MultipartFile;

public interface IFileService {
    FileDTO upload(MultipartFile file);

    SignDTO getSign();
}
