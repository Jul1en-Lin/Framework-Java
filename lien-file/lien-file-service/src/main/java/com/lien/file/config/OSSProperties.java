package com.lien.file.config;


import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * OSS 具体配置信息
 */

@Slf4j
@Data
@RefreshScope
@Configuration
@ConfigurationProperties(prefix = "oss")
@ConditionalOnProperty(value = "storage.type", havingValue = "oss")
public class OSSProperties {

    /**
     * oss是否内网上传
     */
    private Boolean internal;

    /**
     * oss的endpoint
     */
    private String endpoint;

    /**
     * oss的endpoint的内部地址
     */
    private String intEndpoint;

    /**
     * oss地域
     */
    private String region;

    /**
     * ak
     */
    private String accessKeyId;

    /**
     * sk
     */
    private String accessKeySecret;

    /**
     * 存储桶
     */
    private String bucketName;

    /**
     * 路径前缀，加在 endPoint 之后
     */
    private String pathPrefix;

    private Integer expre;

    private Integer minLen;

    private Integer maxLen;


    /**
     * 获取访问URL
     *
     * @return url信息
     */
    public String getBaseUrl() {
        return "https://" + bucketName + "." + endpoint + "/";
    }

    /**
     * 获取内部访问URL
     *
     * @return 内部访问URL
     */
    public String getInternalBaseUrl() {
        return "http://" + bucketName + "." + intEndpoint + "/";
    }
}
