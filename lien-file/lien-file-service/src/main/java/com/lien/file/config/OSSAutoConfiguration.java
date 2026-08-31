package com.lien.file.config;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.credentials.CredentialsProvider;
import com.aliyun.sdk.service.oss2.credentials.StaticCredentialsProvider;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConditionalOnProperty(value = "storage.type", havingValue = "oss")
public class OSSAutoConfiguration {

    /**
     * oss客户端
     */
    public OSSClient ossClient;

    /**
     * 初始化客户端
     * @param prop oss配置
     * @return ossclient
     */
    @Bean
    public OSSClient ossClient(OSSProperties prop) {
        // ref: https://help.aliyun.com/zh/oss/developer-reference/oss-v2-sdk-for-java
        CredentialsProvider credentialsProvider = new StaticCredentialsProvider(
                prop.getAccessKeyId(), prop.getAccessKeySecret());

        // 判断内外网 IntEndpoint/Endpoint 进行上传
        String endpoint = Boolean.TRUE.equals(prop.getInternal())
                ? prop.getIntEndpoint()
                : prop.getEndpoint();

        ossClient = OSSClient.newBuilder()
                .endpoint(endpoint)
                .region(prop.getRegion())
                .credentialsProvider(credentialsProvider)
                .signatureVersion("v4")
                .build();

        return ossClient;
    }

    /**
     * 关闭客户端
     */
    @PreDestroy
    public void closeOSSClient() {
        if (ossClient != null) {
            try {
                ossClient.close();
            } catch (Exception e) {
                log.warn("关闭OSSClient失败", e);
            }
        }
    }
}
