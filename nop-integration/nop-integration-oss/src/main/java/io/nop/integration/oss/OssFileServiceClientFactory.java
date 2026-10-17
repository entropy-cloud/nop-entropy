/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.integration.oss;

import io.nop.credential.api.CredentialData;
import io.nop.credential.api.ICredentialProvider;
import io.nop.integration.api.credential.CredentialResolutionSupport;
import io.nop.integration.api.file.IFileServiceClient;
import io.nop.integration.api.file.IFileServiceClientFactory;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;
import java.util.Set;

/**
 * 很多云存储都兼容s3协议: {阿里云OSS，腾讯云COS，七牛云，京东云，minio 等}
 */
public class OssFileServiceClientFactory implements IFileServiceClientFactory {
    static final Logger LOG = LoggerFactory.getLogger(OssFileServiceClientFactory.class);

    /** 本渠道的凭证类型（家族允许集单元素；W16-impl-ext 设计 §4.3 类型清单）。 */
    static final String CREDENTIAL_TYPE_OSS_S3 = "oss-s3";

    /** consumerRef 引用计数键（设计 §6.5：部署级单渠道，每渠道类型一条稳定引用）。 */
    static final String CONSUMER_REF = "integration:oss-s3";

    private OssConfig ossConfig;

    private S3Client client;

    /**
     * 凭证消费 SPI（可选装配，{@code @Nullable} → NopIoC optional）：部署不含 nop-credential 时
     * 为 null——credentialId 非空时经共享解析支持 fail-closed（部署不一致），静态路径不受影响。
     */
    protected ICredentialProvider credentialProvider;

    public void setOssConfig(OssConfig config) {
        this.ossConfig = config;
    }

    @Inject
    public void setCredentialProvider(@Nullable ICredentialProvider credentialProvider) {
        this.credentialProvider = credentialProvider;
    }

    /**
     * bean 初始化幂等登记引用计数（credentialId 非空且 provider 装配时）。<b>catch-all WARN 不阻断
     * 启动</b>——含 provider 前置校验抛错（credentialId 配错/凭证软删）：登记是治理辅助而非安全边界
     * （安全边界 = 构造期解析 fail-closed）。enabled 门控下零介入：{@code nop.integration.oss.enabled}
     * 为 false/缺省（enableIfMissing=false）时本 bean 不创建，无登记无解析。
     */
    @PostConstruct
    public void init() {
        registerUsageQuietly();
        S3ClientBuilder builder = newClientBuilder();

        // 构造期凭证组（W16-impl-ext，设计 §4.1 结论 6 客户端缓存家族）：credentialId 空/空白 →
        // OssConfig 静态值；非空 → 解析 oss-s3 字段集整组覆盖（accessKey/secretKey 必填，同名静态值
        // 忽略），解析失败 fail-closed 抛 NopException 中止 bean 初始化（不回退静态值）——一次消费，
        // 客户端缓存语义不变（轮换可见性 = 重启，显式接受）。
        String accessKey = ossConfig.getAccessKey();
        String secretKey = ossConfig.getSecretKey();
        String credentialId = ossConfig.getCredentialId();
        if (CredentialResolutionSupport.isConfigured(credentialId)) {
            CredentialData data = CredentialResolutionSupport.resolveGroup(credentialProvider, credentialId,
                    Set.of(CREDENTIAL_TYPE_OSS_S3));
            accessKey = CredentialResolutionSupport.requireString(data, credentialId, "accessKey");
            secretKey = CredentialResolutionSupport.requireString(data, credentialId, "secretKey");
        }
        AwsCredentials awsCredentials = createAwsCredentials(accessKey, secretKey);
        String region = ossConfig.getRegion();
        this.client = builder
                // v1 的 disableChunkedEncoding() 在 v2 的对应面：默认不附加 CRC32 校验和与分块
                // 传输编码，保持对阿里云 OSS、腾讯云 COS、旧版 MinIO 等 S3 兼容存储的最大兼容
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .endpointOverride(URI.create(ossConfig.getEndpoint()))
                .region(Region.of(region == null || region.isEmpty() ? "us-east-1" : region))
                .credentialsProvider(StaticCredentialsProvider.create(awsCredentials))
                .forcePathStyle(Boolean.TRUE.equals(ossConfig.getPathStyleAccess()))
                .build();
    }

    /**
     * v2 客户端 builder 构造点（protected seam 供测试扩展）。
     */
    protected S3ClientBuilder newClientBuilder() {
        return S3Client.builder();
    }

    void registerUsageQuietly() {
        String credentialId = ossConfig != null ? ossConfig.getCredentialId() : null;
        if (!CredentialResolutionSupport.isConfigured(credentialId) || credentialProvider == null) {
            return;
        }
        try {
            credentialProvider.registerUsage(credentialId, CONSUMER_REF);
        } catch (Exception e) {
            LOG.warn("nop.credential-register-usage-failed:credentialId={},consumerRef={} (non-blocking)",
                    credentialId, CONSUMER_REF, e);
        }
    }

    /**
     * AWS 凭证构造点（protected seam 供测试替换）：credentialId 路径下使用解析后的凭证组
     * （同首批 {@code createSender} 先例）。
     */
    protected AwsCredentials createAwsCredentials(String accessKey, String secretKey) {
        return AwsBasicCredentials.create(accessKey, secretKey); //NOSONAR
    }

    @PreDestroy
    public void destroy() {
        if (client != null) {
            client.close();
        }
    }

    @Override
    public IFileServiceClient newClient() {
        return new OssFileServiceClient(client, ossConfig);
    }
}
