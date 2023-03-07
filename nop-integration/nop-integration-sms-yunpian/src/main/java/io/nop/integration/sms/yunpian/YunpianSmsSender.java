/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.integration.sms.yunpian;

import com.yunpian.sdk.YunpianClient;
import com.yunpian.sdk.model.Result;
import com.yunpian.sdk.model.SmsSingleSend;
import io.nop.api.core.exceptions.NopException;
import io.nop.integration.api.sms.ISmsSender;
import io.nop.integration.api.sms.SmsMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static io.nop.integration.api.IntegrationErrors.ARG_ERROR_CODE;
import static io.nop.integration.api.IntegrationErrors.ARG_MOBILE;
import static io.nop.integration.api.IntegrationErrors.ARG_MSG;
import static io.nop.integration.api.IntegrationErrors.ERR_SEND_SMS_FAIL;

public class YunpianSmsSender implements ISmsSender {
    static final Logger LOG = LoggerFactory.getLogger(YunpianSmsSender.class);

    private String apiKey;

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public void sendMessage(SmsMessage message) {


        YunpianClient client = new YunpianClient(apiKey).init();

        try {
            sendMessage(client, message);
        } finally {
            client.close();
        }
    }

    @Override
    public void sendMultiMessage(List<SmsMessage> messages) {
        YunpianClient client = new YunpianClient(apiKey).init();
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
