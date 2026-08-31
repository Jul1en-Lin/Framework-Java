package com.lien.file.service.impl;

import com.lien.file.service.IFileService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileServiceImpl implements IFileService {
    @Override
    public FileVO upload(MultipartFile file) {
        return null;
    }

    @Override
    public SignVO getSign() {
        return null;
    }
}
