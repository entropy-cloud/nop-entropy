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
import io.nop.integration.api.sms.SmsMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * areaCode 缺省语义测试：平台主链路（nop-auth LoginServiceImpl.sendSms /
 * NopAuthUserBizModel.sendSmsForBinding）只设置 mobile/templateCode/params，从不设置
 * areaCode（国内短信常态）——未设置时必须回退默认区号 "86"（腾讯 SDK nationCode 语义），
 * 不得在 sendMessage 入口 NPE（修复前：areaCode 解引用在 try 之外，必现裸 NPE）。
 */
public class TestTencentSmsSenderAreaCode {

    static final String DEFAULT_AREA_CODE = "86";

    /** 记录型 SmsSingleSender stub：捕获 sendWithParam 收到的区号（成功返回 result=0）。 */
    static class AreaCodeRecordingSender extends SmsSingleSender {
        final List<String> areaCodes = new ArrayList<>();
        int plainSendCount;

        AreaCodeRecordingSender(int appId, String appKey) {
            super(appId, appKey);
        }

        private static SmsSingleSenderResult ok() {
            SmsSingleSenderResult result = new SmsSingleSenderResult();
            result.result = 0;
            return result;
        }

        @Override
        public SmsSingleSenderResult send(int type, String mobile, String text, String sign,
                                          String ext, String ext2) {
            plainSendCount++;
            return ok();
        }

        @Override
        public SmsSingleSenderResult sendWithParam(String areaCode, String mobile, int tmplId,
                                                   ArrayList<String> params, String sign,
                                                   String ext, String ext2) {
            areaCodes.add(areaCode);
            return ok();
        }
    }

    /** 测试用发送器子类：替换 sender 构造点，返回记录型 stub。 */
    static class TestableSender extends TencentSmsSender {
        final AreaCodeRecordingSender sender = new AreaCodeRecordingSender(111, "key");

        @Override
        protected SmsSingleSender createSender(ResolvedCredential credential) {
            return sender;
        }
    }

    private static SmsMessage message() {
        SmsMessage message = new SmsMessage();
        message.setMobile("13800000000");
        message.setTemplateCode("12345");
        message.setParams(List.of("code"));
        return message; // 不设置 areaCode——平台主链路形态
    }

    @Test
    public void missingAreaCodeFallsBackToDefaultNationCode() {
        // 主链路形态（模板短信、无 areaCode）：不抛 NPE，SDK 收到默认区号 "86"
        TestableSender testable = new TestableSender();

        testable.sendMessage(message()); // 修复前此处抛裸 NPE（areaCode.startsWith 解引用 null）

        assertEquals(List.of(DEFAULT_AREA_CODE), testable.sender.areaCodes);
    }

    @Test
    public void missingAreaCodePlainSendDoesNotThrow() {
        // 非模板分支（sender.send 不使用区号）同样不得在入口解引用处 NPE
        TestableSender testable = new TestableSender();
        SmsMessage msg = message();
        msg.setTemplateCode(null);
        msg.setText("plain text");

        testable.sendMessage(msg); // 修复前此处抛裸 NPE

        assertEquals(1, testable.sender.plainSendCount);
        assertEquals(0, testable.sender.areaCodes.size());
    }

    @Test
    public void plusPrefixedAreaCodeStrippedToNationCode() {
        // 显式 "+86" 既有剥离行为保持
        TestableSender testable = new TestableSender();
        SmsMessage msg = message();
        msg.setAreaCode("+86");

        testable.sendMessage(msg);

        assertEquals(List.of("86"), testable.sender.areaCodes);
    }

    @Test
    public void explicitForeignAreaCodePassedThrough() {
        // 显式海外区号原样透传（不受默认回退影响）
        TestableSender testable = new TestableSender();
        SmsMessage msg = message();
        msg.setAreaCode("1");

        testable.sendMessage(msg);

        assertEquals(List.of("1"), testable.sender.areaCodes);
    }

    @Test
    public void sendMultiMessageMissingAreaCodeDoesNotThrow() {
        // 多条入口共用同一 sendMessage 私有方法，同样受回退保护
        TestableSender testable = new TestableSender();

        testable.sendMultiMessage(List.of(message(), message()));

        assertEquals(2, testable.sender.areaCodes.size());
        assertEquals(DEFAULT_AREA_CODE, testable.sender.areaCodes.get(1));
    }
}
