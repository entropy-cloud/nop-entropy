/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.integration.sms.yunpian;

import com.yunpian.sdk.YunpianClient;
import com.yunpian.sdk.model.Result;
import com.yunpian.sdk.model.SmsSingleSend;
import io.nop.api.core.exceptions.NopException;
import io.nop.credential.api.CredentialData;
import io.nop.credential.api.ICredentialProvider;
import io.nop.integration.api.credential.CredentialResolutionSupport;
import io.nop.integration.api.sms.ISmsSender;
import io.nop.integration.api.sms.SmsMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.integration.api.IntegrationErrors.ARG_ERROR_CODE;
import static io.nop.integration.api.IntegrationErrors.ARG_MOBILE;
import static io.nop.integration.api.IntegrationErrors.ARG_MSG;
import static io.nop.integration.api.IntegrationErrors.ERR_SEND_SMS_FAIL;

public class YunpianSmsSender implements ISmsSender {
    static final Logger LOG = LoggerFactory.getLogger(YunpianSmsSender.class);

    /** 本渠道的凭证类型（家族允许集单元素；W16-impl 设计 §4.3 类型清单）。 */
    static final String CREDENTIAL_TYPE_YUNPIAN_SMS = "yunpian-sms";

    /** consumerRef 引用计数键（设计 §6.5：部署级单渠道，每渠道类型一条稳定引用）。 */
    static final String CONSUMER_REF = "integration:yunpian-sms";

    private String apiKey;

    /**
     * 可选的凭证引用（W16-impl）：非空时 {@code yunpian-sms} 字段集（apiKey）整组取自凭证库，
     * 同名静态值被忽略；空/空白时维持静态值现状路径（既有部署零回归）。
     */
    private String credentialId;

    /**
     * 凭证消费 SPI（可选装配，{@code @Nullable} → NopIoC optional）：部署不含 nop-credential 时
     * 为 null——credentialId 非空时经共享解析支持 fail-closed（部署不一致），静态路径不受影响。
     */
    protected ICredentialProvider credentialProvider;

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
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
     * 启动</b>——含 provider 前置校验抛错（credentialId 配错/凭证软删，D6-03 后 registerUsage
     * fail-closed）：登记是治理辅助而非安全边界（安全边界 = 发送期解析 fail-closed）。
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
     * 优先级链判定（共享解析支持单点语义）：credentialId 空/空白 → 静态 apiKey；非空 → 解析
     * {@code yunpian-sms} 字段集（apiKey，同名静态值忽略），解析失败 fail-closed 抛错中止本次
     * 发送（不回退静态值）。包可见以便单元测试三态断言。
     */
    String resolveApiKey() {
        if (!CredentialResolutionSupport.isConfigured(credentialId)) {
            return apiKey;
        }
        CredentialData data = CredentialResolutionSupport.resolveGroup(credentialProvider, credentialId,
                Set.of(CREDENTIAL_TYPE_YUNPIAN_SMS));
        return CredentialResolutionSupport.requireString(data, credentialId, "apiKey");
    }

    /**
     * client 构造点（protected seam 供测试替换）：sendMessage 与 sendMultiMessage 双路径均经此
     * 构造，credentialId 路径下使用解析后的 apiKey。
     */
    protected YunpianClient createClient(String apiKey) {
        return new YunpianClient(apiKey).init();
    }

    @Override
    public void sendMessage(SmsMessage message) {
        YunpianClient client = createClient(resolveApiKey());

        try {
            sendMessage(client, message);
        } finally {
            client.close();
        }
    }

    @Override
    public void sendMultiMessage(List<SmsMessage> messages) {
        YunpianClient client = createClient(resolveApiKey());
        try {
            for (SmsMessage message : messages) {
                sendMessage(client, message);
            }
        } finally {
            client.close();
        }
    }

    private void sendMessage(YunpianClient client, SmsMessage message) {
        LOG.info("nop.send-sms:areaCode={},mobile={}", message.getAreaCode(), message.getMobile());

        Map<String, String> param = client.newParam(2);
        param.put(YunpianClient.MOBILE, message.getAreaCode() + message.getMobile());
        param.put(YunpianClient.TEXT, message.getText());
        Result<SmsSingleSend> result = client.sms().single_send(param);
        if (result.getCode() != 0) {
            LOG.error("nop.send-sms-fail:mobile={},code={},msg={}", message.getMobile(), result.getCode(), result.getMsg());
            throw new NopException(ERR_SEND_SMS_FAIL, result.getThrowable())
                    .param(ARG_ERROR_CODE, result.getCode())
                    .param(ARG_MSG, result.getMsg())
                    .param(ARG_MOBILE, message.getMobile());
        }
    }
}
