/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.integration.sms;

import com.github.qcloudsms.SmsSingleSender;
import com.github.qcloudsms.SmsSingleSenderResult;
import io.nop.api.core.exceptions.NopException;
import io.nop.integration.api.sms.ISmsSender;
import io.nop.integration.api.sms.SmsMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static io.nop.integration.api.IntegrationErrors.ARG_ERROR_CODE;
import static io.nop.integration.api.IntegrationErrors.ARG_MOBILE;
import static io.nop.integration.api.IntegrationErrors.ARG_MSG;
import static io.nop.integration.api.IntegrationErrors.ERR_SEND_SMS_FAIL;

public class TencentSmsSender implements ISmsSender {
    static final Logger LOG = LoggerFactory.getLogger(TencentSmsSender.class);

    private Integer appId;
    private String appKey;
    private String sign;

    public void setAppId(Integer appId) {
        this.appId = appId;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public void setSign(String sign) {
        this.sign = sign;
    }

    @Override
    public void sendMessage(SmsMessage message) {
        SmsSingleSender sender = new SmsSingleSender(appId, appKey);

        sendMessage(sender, message);
    }

    private void sendMessage(SmsSingleSender sender, SmsMessage message) {
        String areaCode = message.getAreaCode();
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
        SmsSingleSender sender = new SmsSingleSender(appId, appKey);

        for (SmsMessage message : messages) {
            sendMessage(sender, message);
        }
    }
}
