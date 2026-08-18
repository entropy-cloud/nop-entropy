/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.integration.sftp;

import io.nop.credential.api.ICredentialProvider;
import io.nop.integration.api.credential.CredentialResolutionSupport;
import io.nop.integration.api.file.IFileServiceClientFactory;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SftpClientFactory implements IFileServiceClientFactory {
    static final Logger LOG = LoggerFactory.getLogger(SftpClientFactory.class);

    /** consumerRef 引用计数键（设计 §6.5：部署级单渠道，每渠道类型一条稳定引用）。 */
    static final String CONSUMER_REF = "integration:sftp-ssh";

    private SftpConfig config;

    /**
     * 凭证消费 SPI（可选装配，{@code @Nullable} → NopIoC optional）：部署不含 nop-credential 时
     * 为 null——credentialId 非空时经共享解析支持 fail-closed（部署不一致），静态路径不受影响。
     * 经 {@code newClient()} 传递给每个 {@code SftpClient}（逐次操作期解析）。
     */
    protected ICredentialProvider credentialProvider;

    public SftpConfig getConfig() {
        return config;
    }

    public void setConfig(SftpConfig config) {
        this.config = config;
    }

    @Inject
    public void setCredentialProvider(@Nullable ICredentialProvider credentialProvider) {
        this.credentialProvider = credentialProvider;
    }

    /**
     * bean 初始化幂等登记引用计数（config.credentialId 非空且 provider 装配时）。<b>catch-all WARN
     * 不阻断启动</b>——含 provider 前置校验抛错（credentialId 配错/凭证软删）：登记是治理辅助而非
     * 安全边界（安全边界 = 逐次操作期解析 fail-closed）。
     */
    @PostConstruct
    public void init() {
        registerUsageQuietly();
    }

    void registerUsageQuietly() {
        String credentialId = config != null ? config.getCredentialId() : null;
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

    @Override
    public SftpClient newClient() {
        return new SftpClient(config, credentialProvider);
    }
}
