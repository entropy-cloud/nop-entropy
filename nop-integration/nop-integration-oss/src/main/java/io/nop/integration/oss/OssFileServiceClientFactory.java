/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.integration.oss;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
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

    private AmazonS3 client;

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
        ClientConfiguration clientConfiguration = new ClientConfiguration();
        AwsClientBuilder.EndpointConfiguration endpointConfiguration = new AwsClientBuilder.EndpointConfiguration(
                ossConfig.getEndpoint(), ossConfig.getRegion());

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
        AWSCredentials awsCredentials = createAwsCredentials(accessKey, secretKey);
        AWSCredentialsProvider awsCredentialsProvider = new AWSStaticCredentialsProvider(awsCredentials);
        this.client = AmazonS3Client.builder().withEndpointConfiguration(endpointConfiguration)
                .withClientConfiguration(clientConfiguration).withCredentials(awsCredentialsProvider)
                .disableChunkedEncoding().withPathStyleAccessEnabled(ossConfig.getPathStyleAccess()).build();
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
    protected AWSCredentials createAwsCredentials(String accessKey, String secretKey) {
        return new BasicAWSCredentials(accessKey, secretKey); //NOSONAR
    }

    @PreDestroy
    public void destroy() {
        if (client != null) {
            client.shutdown();
        }
    }

    @Override
    public IFileServiceClient newClient() {
        return new OssFileServiceClient(client, ossConfig);
    }
}
