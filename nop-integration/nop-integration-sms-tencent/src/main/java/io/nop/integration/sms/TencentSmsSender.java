/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.integration.sms;

import com.github.qcloudsms.SmsSingleSender;
import com.github.qcloudsms.SmsSingleSenderResult;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static io.nop.integration.api.IntegrationErrors.ARG_ERROR_CODE;
import static io.nop.integration.api.IntegrationErrors.ARG_MOBILE;
import static io.nop.integration.api.IntegrationErrors.ARG_MSG;
import static io.nop.integration.api.IntegrationErrors.ERR_SEND_SMS_FAIL;

public class TencentSmsSender implements ISmsSender {
    static final Logger LOG = LoggerFactory.getLogger(TencentSmsSender.class);

    /** 本渠道的凭证类型（家族允许集单元素；W16-impl 设计 §4.3 类型清单）。 */
    static final String CREDENTIAL_TYPE_TENCENT_SMS = "tencent-sms";

    /** consumerRef 引用计数键（设计 §6.5：部署级单渠道，每渠道类型一条稳定引用）。 */
    static final String CONSUMER_REF = "integration:tencent-sms";

    /**
     * SmsMessage.areaCode 无默认值，且平台主链路（nop-auth LoginServiceImpl.sendSms /
     * NopAuthUserBizModel.sendSmsForBinding）从不设置区号（国内短信常态）——未设置时回退
     * 默认区号 "86"（腾讯 SDK sendWithParam 的 nationCode 语义），不得在入口 NPE。
     */
    static final String DEFAULT_AREA_CODE = "86";

    private Integer appId;
    private String appKey;
    private String sign;

    /**
     * 可选的凭证引用（W16-impl）：非空时 {@code tencent-sms} 字段集（appId/appKey/sign）整组取自
     * 凭证库，同名静态值被忽略；空/空白时维持静态值现状路径（既有部署零回归）。
     */
    private String credentialId;

    /**
     * 凭证消费 SPI（可选装配，{@code @Nullable} → NopIoC optional）：部署不含 nop-credential 时
     * 为 null——credentialId 非空时经共享解析支持 fail-closed（部署不一致），静态路径不受影响。
     */
    protected ICredentialProvider credentialProvider;

    public void setAppId(Integer appId) {
        this.appId = appId;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public void setSign(String sign) {
        this.sign = sign;
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
     * 单次发送的凭证组（发送期惰性解析——逐次发送家族，凭证轮换/禁用下次发送即生效）。
     */
    static final class ResolvedCredential {
        final Integer appId;
        final String appKey;
        final String sign;

        ResolvedCredential(Integer appId, String appKey, String sign) {
            this.appId = appId;
            this.appKey = appKey;
            this.sign = sign;
        }
    }

    /**
     * 优先级链判定（共享解析支持单点语义）：credentialId 空/空白 → 静态值组；非空 → 解析
     * {@code tencent-sms} 字段集整组覆盖（appId/appKey/sign，同名静态值忽略），解析失败
     * fail-closed 抛错中止本次发送（不回退静态值）。包可见以便单元测试三态断言。
     */
    ResolvedCredential resolveCredential() {
        if (!CredentialResolutionSupport.isConfigured(credentialId)) {
            return new ResolvedCredential(appId, appKey, sign);
        }
        CredentialData data = CredentialResolutionSupport.resolveGroup(credentialProvider, credentialId,
                Set.of(CREDENTIAL_TYPE_TENCENT_SMS));
        return new ResolvedCredential(
                CredentialResolutionSupport.requireInteger(data, credentialId, "appId"),
                CredentialResolutionSupport.requireString(data, credentialId, "appKey"),
                CredentialResolutionSupport.optionalString(data, "sign"));
    }

    /**
     * sender 构造点（protected seam 供测试替换）：sendMessage 与 sendMultiMessage 双路径均经此
     * 构造，credentialId 路径下使用解析后的凭证组。
     */
    protected SmsSingleSender createSender(ResolvedCredential credential) {
        return new SmsSingleSender(credential.appId, credential.appKey);
    }

    @Override
    public void sendMessage(SmsMessage message) {
        ResolvedCredential credential = resolveCredential();
        sendMessage(createSender(credential), credential.sign, message);
    }

    private void sendMessage(SmsSingleSender sender, String sign, SmsMessage message) {
        String areaCode = message.getAreaCode();
        if (areaCode == null || areaCode.isEmpty())
            areaCode = DEFAULT_AREA_CODE;
        if (areaCode.startsWith("+"))
            areaCode = areaCode.substring(1);

        try {
            SmsSingleSenderResult result;
            if (message.getTemplateCode() != null) {
                result = sender.sendWithParam(areaCode,
                        message.getMobile(), Integer.parseInt(message.getTemplateCode()),
                        toArrayList(message.getParams()), sign, "", "");
            } else {
                result = sender.send(message.getType(), message.getMobile(), message.getText(), sign, "", "");
            }
            if (result.result != 0) {
                LOG.error("nop.send-sms-fail:mobile={},code={},msg={}", message.getMobile(), result.result, result.errMsg);
                throw new NopException(ERR_SEND_SMS_FAIL)
                        .param(ARG_MOBILE, message.getMobile())
                        .param(ARG_ERROR_CODE, result.result)
                        .param(ARG_MSG, result.errMsg);
            }
        } catch (NopException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("nop.send-sms-fail:mobile={}", message.getMobile(), e);
            throw new NopException(ERR_SEND_SMS_FAIL, e).param(ARG_MOBILE, message.getMobile());
        }
    }

    private ArrayList<String> toArrayList(List<String> list) {
        if (list == null || list.isEmpty())
            return new ArrayList<>();
        if (list instanceof ArrayList)
            return (ArrayList<String>) list;
        return new ArrayList<>(list);
    }

    @Override
    public void sendMultiMessage(List<SmsMessage> messages) {
        ResolvedCredential credential = resolveCredential();
        SmsSingleSender sender = createSender(credential);
        for (SmsMessage message : messages) {
            sendMessage(sender, credential.sign, message);
        }
    }
}
