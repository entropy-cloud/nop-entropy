/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.service.mock;

import io.nop.integration.api.sms.ISmsSender;
import io.nop.integration.api.sms.SmsMessage;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 容器 E2E 用的捕获型短信发送器（W13-impl）：登记通道 proof 发码链路（bindMfa 前置 →
 * {@code SmsCodeStore.send} → {@code sendSmsForBinding}）经本 bean "发送"，验证码可从
 * {@link #lastMessage} 读取（MockShardSelector 同目录先例）。
 */
public class CapturingSmsSender implements ISmsSender {

    private static final AtomicReference<SmsMessage> LAST = new AtomicReference<>();

    @Override
    public void sendMessage(SmsMessage message) {
        LAST.set(message);
    }

    public static SmsMessage lastMessage() {
        return LAST.get();
    }

    /** 最近一条消息中的 6 位验证码（params 首元素）。 */
    public static String lastCode() {
        SmsMessage msg = LAST.get();
        if (msg == null || msg.getParams() == null || msg.getParams().isEmpty())
            return null;
        Object code = msg.getParams().get(0);
        return code == null ? null : code.toString();
    }

    public static void reset() {
        LAST.set(null);
    }
}
