package com.lien.file.service.impl;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.models.ObjectACLType;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import com.aliyun.sdk.service.oss2.models.PutObjectResult;
import com.aliyun.sdk.service.oss2.transport.BinaryData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lien.file.config.OSSProperties;
import com.lien.file.constants.OSSCustomConstants;
import com.lien.file.entity.dto.FileDTO;
import com.lien.file.entity.dto.SignDTO;
import com.lien.file.service.IFileService;
import domain.EnumCode;
import domain.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.io.File;

@Slf4j
@Service
@ConditionalOnProperty(value = "storage.type", havingValue = "oss")
public class OSSFileServiceImpl implements IFileService {

    @Autowired
    private OSSClient ossClient;

    @Autowired
    private OSSProperties ossProperties;

    /**
     * 使用 SDK 进行上传文件（已集成 v4 签名）
     * @param file 文件资源
     */
    @Override
    public FileDTO upload(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            // 获取原始的文件名
            String originalFilename = file.getOriginalFilename();
            String extName = originalFilename.substring(originalFilename.lastIndexOf(".")+1);
            // oss 存储命名规则：UUID + 文件的后缀名（key）
            String objectName = ossProperties.getPathPrefix() + UUID.randomUUID()+"."+extName;
            // 设置公共可读权限
            String objectAcl = ObjectACLType.PUBLIC_READ.toString();

            // 创建 PutObjectRequest 对象
            PutObjectRequest putObjectRequest = PutObjectRequest.newBuilder()
                    .bucket(ossProperties.getBucketName())
                    .key(objectName)
                    .body(BinaryData.fromStream(inputStream))
                    .objectAcl(objectAcl)
                    .build();

            // 创建 PutObject 请求
            PutObjectResult putObjectResult = ossClient.putObject(putObjectRequest);

            if (putObjectResult == null || putObjectResult.statusCode() != 200) {
                log.error("上传oss异常putObjectResult未正常返回: {}", putObjectRequest);
                throw new ServiceException(EnumCode.OSS_UPLOAD_FAILED);
            }

            FileDTO sysFileDTO = new FileDTO();
            sysFileDTO.setUrl(ossProperties.getBaseUrl() + objectName);
            sysFileDTO.setKey(objectName);
            sysFileDTO.setName(new File(objectName).getName());
            return sysFileDTO;
        } catch (Exception e) {
            log.error("上传oss异常", e);
            throw new ServiceException(EnumCode.OSS_UPLOAD_FAILED);
        }
    }

    /**
     * 获取 V4 直传签名，无 SDK 时实现上传
     */
    @Override
    public SignDTO getSign() {
        try {
            //获取ak sk
            String accesskeyid = ossProperties.getAccessKeyId();
            String accesskeysecret = ossProperties.getAccessKeySecret();
            // 获取当前时间
            Instant now = Instant.now();
            //构建返回数据
            SignDTO signDTO = new SignDTO();
            signDTO.setHost(ossProperties.getBaseUrl());
            signDTO.setPathPrefix(ossProperties.getPathPrefix());

            // 步骤1：创建 policy
            Map<String, Object> policy = new HashMap<>();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(OSSCustomConstants.SIGN_EXPIRE_TIME_FORMAT)
                    .withZone(java.time.ZoneOffset.UTC);
            String expiration = formatter.format(now.plusSeconds(ossProperties.getExpire()));
            policy.put("expiration", expiration);

            List<Object> conditions = new ArrayList<>();
            Map<String, String> bucketCondition = new HashMap<>();
            bucketCondition.put("bucket", ossProperties.getBucketName());
            conditions.add(bucketCondition);

            // 指定签名的版本和算法，固定值为OSS4-HMAC-SHA256
            Map<String, String> signatureVersionCondition = new HashMap<>();
            signatureVersionCondition.put("x-oss-signature-version", "OSS4-HMAC-SHA256");
            conditions.add(signatureVersionCondition);

            Map<String, String> credentialCondition = new HashMap<>();
            formatter = DateTimeFormatter.ofPattern(OSSCustomConstants.SIGN_DATE_FORMAT)
                    .withZone(java.time.ZoneOffset.UTC);
            String dateStr = formatter.format(now);
            String xOSSCredential = accesskeyid + "/" + dateStr + "/" + ossProperties.getRegion() + "/oss/aliyun_v4_request";
            signDTO.setXOSSCredential(xOSSCredential);
            credentialCondition.put("x-oss-credential", xOSSCredential); // 替换为实际的 access key id
            conditions.add(credentialCondition);

            Map<String, String> dateCondition = new HashMap<>();

            // 定义日期时间格式化器
            formatter = DateTimeFormatter.ofPattern(OSSCustomConstants.SIGN_REQUEST_TIME_FORMAT)
                    .withZone(java.time.ZoneOffset.UTC);
            // 格式化时间
            String xOSSDate = formatter.format(now);
            signDTO.setXOSSDate(xOSSDate);
            dateCondition.put("x-oss-date", xOSSDate);

            conditions.add(dateCondition);
            conditions.add(Arrays.asList("content-length-range", ossProperties.getMinLen(), ossProperties.getMaxLen()));
            conditions.add(Arrays.asList("eq", "$success_action_status", "200"));

            policy.put("conditions", conditions);

            ObjectMapper mapper = new ObjectMapper();
            String jsonPolicy = mapper.writeValueAsString(policy);

            // 步骤2：构造待签名字符串（StringToSign）
            String StringToSign = Base64.getEncoder().encodeToString(jsonPolicy.getBytes(StandardCharsets.UTF_8));
            signDTO.setPolicy(StringToSign);

            // 步骤3：计算 SigningKey
            byte[] dateKey = hmacsha256(("aliyun_v4" + accesskeysecret).getBytes(), dateStr);
            byte[] dateRegionKey = hmacsha256(dateKey, ossProperties.getRegion());
            byte[] dateRegionServiceKey = hmacsha256(dateRegionKey, "oss");
            byte[] signingKey = hmacsha256(dateRegionServiceKey, "aliyun_v4_request");

            // 步骤4：计算 Signature
            byte[] result = hmacsha256(signingKey, StringToSign);
            String signature = HexFormat.of().formatHex(result);
            signDTO.setSignature(signature);
            return signDTO;
        } catch (Exception e) {
            log.error("生成直传签名失败", e);
            throw new ServiceException(EnumCode.PRE_SIGN_URL_FAILED);
        }
    }

    private static byte[] hmacsha256(byte[] key, String data) {
        try {
            // 初始化HMAC密钥规格，指定算法为HMAC-SHA256并使用提供的密钥。
            SecretKeySpec secretKeySpec = new SecretKeySpec(key, "HmacSHA256");
            // 获取Mac实例，并通过getInstance方法指定使用HMAC-SHA256算法。
            Mac mac = Mac.getInstance("HmacSHA256");
            // 使用密钥初始化Mac对象。
            mac.init(secretKeySpec);
            // 执行HMAC计算，通过doFinal方法接收需要计算的数据并返回计算结果的数组。
            byte[] hmacBytes = mac.doFinal(data.getBytes());

            return hmacBytes;
        } catch (Exception e) {
            log.error("生成直传签名失败", e);
            throw new ServiceException(EnumCode.PRE_SIGN_URL_FAILED);
        }
    }

//    public static void main(String[] args) {
//        System.out.println(UUID.randomUUID());
//    }
}
