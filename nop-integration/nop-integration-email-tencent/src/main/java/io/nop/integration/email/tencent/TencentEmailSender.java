/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.integration.email.tencent;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.ses.v20201002.SesClient;
import com.tencentcloudapi.ses.v20201002.models.SendEmailRequest;
import com.tencentcloudapi.ses.v20201002.models.Simple;
import io.nop.credential.api.CredentialData;
import io.nop.credential.api.ICredentialProvider;
import io.nop.integration.api.credential.CredentialResolutionSupport;
import io.nop.integration.api.email.EmailMessage;
import io.nop.integration.api.email.IEmailSender;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class TencentEmailSender implements IEmailSender {
    public static final String ENDPOINT = "ses.tencentcloudapi.com";

    static final Logger LOG = LoggerFactory.getLogger(TencentEmailSender.class);

    /** 本渠道的凭证类型（家族允许集单元素；W16-impl-ext 设计 §4.3 类型清单）。 */
    static final String CREDENTIAL_TYPE_TENCENT_EMAIL = "tencent-email";

    /** consumerRef 引用计数键（设计 §6.5：部署级单渠道，每渠道类型一条稳定引用）。 */
    static final String CONSUMER_REF = "integration:tencent-email";

    private String region;

    private String secretId;

    private String secretKey;

    /**
     * 可选的凭证引用（W16-impl-ext）：非空时 {@code tencent-email} 字段集（secretId/secretKey/region）
     * 整组取自凭证库，同名静态值被忽略；空/空白时维持静态值现状路径（既有部署零回归）。
     */
    private String credentialId;

    /**
     * 凭证消费 SPI（可选装配，{@code @Nullable} → NopIoC optional）：部署不含 nop-credential 时
     * 为 null——credentialId 非空时经共享解析支持 fail-closed（部署不一致），静态路径不受影响。
     */
    protected ICredentialProvider credentialProvider;

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getSecretId() {
        return secretId;
    }

    public void setSecretId(String secretId) {
        this.secretId = secretId;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public void setCredentialId(String credentialId) {
        this.credentialId = credentialId;
    }

    @Inject
    public void setCredentialProvider(@Nullable ICredentialProvider credentialProvider) {
        this.credentialProvider = credentialProvider;
    }

    /**
     * bean 初始化幂等登记引用计数（credentialId 非空且 provider 装配时）。<b>catch-all WARN 不阻断
     * 启动</b>——含 provider 前置校验抛错（credentialId 配错/凭证软删）：登记是治理辅助而非安全边界
     * （安全边界 = 发送期解析 fail-closed）。
     */
    @PostConstruct
    public void init() {
        registerUsageQuietly();
    }

    void registerUsageQuietly() {
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
     * 单次发送的凭证组（发送期惰性解析——逐次发送家族，凭证轮换/禁用下次发送即生效）。
     */
    static final class ResolvedCredential {
        final String secretId;
        final String secretKey;
        final String region;

        ResolvedCredential(String secretId, String secretKey, String region) {
            this.secretId = secretId;
            this.secretKey = secretKey;
            this.region = region;
        }
    }

    /**
     * 优先级链判定（共享解析支持单点语义）：credentialId 空/空白 → 静态值组；非空 → 解析
     * {@code tencent-email} 字段集整组覆盖（secretId/secretKey/region，同名静态值忽略），解析失败
     * fail-closed 抛错中止本次发送（不回退静态值）。包可见以便单元测试三态断言。
     */
    ResolvedCredential resolveCredential() {
        if (!CredentialResolutionSupport.isConfigured(credentialId)) {
            return new ResolvedCredential(secretId, secretKey, region);
        }
        CredentialData data = CredentialResolutionSupport.resolveGroup(credentialProvider, credentialId,
                Set.of(CREDENTIAL_TYPE_TENCENT_EMAIL));
        return new ResolvedCredential(
                CredentialResolutionSupport.requireString(data, credentialId, "secretId"),
                CredentialResolutionSupport.requireString(data, credentialId, "secretKey"),
                CredentialResolutionSupport.requireString(data, credentialId, "region"));
    }

    /**
     * client 构造点（protected seam 供测试替换）：sendEmail 经此构造，credentialId 路径下使用
     * 解析后的凭证组（同首批 {@code createSender} 先例）。
     */
    protected SesClient createClient(ResolvedCredential credential) {
        // instantiate an authentication object
        Credential cred = new Credential(credential.secretId, credential.secretKey);

        // instantiate a http option
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint(ENDPOINT);

        // Instantiate a client option
        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);

        // instantiate the client object of the requested product
        return new SesClient(cred, credential.region, clientProfile);
    }

    @Override
    public void sendEmail(EmailMessage mail) {
        ResolvedCredential credential = resolveCredential();
        SesClient client = createClient(credential);
        try {
            SendEmailRequest req = new SendEmailRequest();

            req.setSubject(mail.getSubject());
            req.setFromEmailAddress(mail.getFullFrom());
            req.setDestination(mail.getTo().toArray(new String[0]));
            if (mail.getReply() != null)
                req.setReplyToAddresses(mail.getReply());
            Simple simple = new Simple();
            if (mail.isHtml()) {
                simple.setHtml(mail.getText());
            } else {
                simple.setText(mail.getText());
            }

            req.setSimple(simple);
            client.SendEmail(req);
        } catch (TencentCloudSDKException e) {
            String ignoreInfo = "EmailAddressIsNULL";
            if (e.getErrorCode().contains(ignoreInfo)) {
                return;
            } else if ("FailedOperation.FrequencyLimit".equals(e.getErrorCode())) {
                LOG.warn("nop.send-email-exceed-limit", e);
                return;
            }
            LOG.error("nop.err.send-email-fail", e);
        }
    }
}
