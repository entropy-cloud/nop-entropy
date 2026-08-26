package io.nop.auth.service;

import io.nop.api.core.exceptions.NopException;
import io.nop.auth.service.entity.NopAuthUserBizModel;
import io.nop.auth.service.login.LoginServiceImpl;
import io.nop.integration.api.sms.ISmsSender;
import io.nop.integration.api.sms.SmsMessage;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static io.nop.auth.service.NopAuthErrors.ERR_AUTH_INVALID_LOGIN_REQUEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * check2 [P3] 回归：smsCodeStore 未装配时不得以 null code 调用短信发送器。
 *
 * <p>修复前行为：{@code sendSms}/{@code sendSmsForBinding} 仅校验 {@code smsSender == null}，
 * 不校验 code——smsCodeStore 未装配而 smsSender 已装配的部署（手工 wiring）会以
 * {@code params=[null]} 发出无效短信且无错误日志（email 侧 {@code sendMfaEmailCode} 已判空
 * fail-closed，行为不一致）。
 */
class TestSmsSendFailClosed {

    static class CapturingSmsSender implements ISmsSender {
        SmsMessage last;

        @Override
        public void sendMessage(SmsMessage message) {
            this.last = message;
        }
    }

    /** 暴露 protected sendSms 的测试子类（LoginServiceImpl 的 smsSender 为 protected 字段）。 */
    static class ExposedLoginService extends LoginServiceImpl {
        void wireSmsSender(ISmsSender sender) {
            this.smsSender = sender;
        }

        void invokeSendSms(String phone, String code) {
            sendSms(phone, code);
        }
    }

    @Test
    void testSendSmsWithNullCodeFailsClosed() {
        ExposedLoginService svc = new ExposedLoginService();
        CapturingSmsSender sender = new CapturingSmsSender();
        svc.wireSmsSender(sender);

        NopException ex = assertThrows(NopException.class, () -> svc.invokeSendSms("13800138000", null),
                "null code must fail closed instead of sending params=[null]");
        assertEquals(ERR_AUTH_INVALID_LOGIN_REQUEST.getErrorCode(), ex.getErrorCode());
        assertNull(sender.last, "no sms must be dispatched when code is missing");
    }

    @Test
    void testSendSmsWithValidCodeStillDispatches() {
        ExposedLoginService svc = new ExposedLoginService();
        CapturingSmsSender sender = new CapturingSmsSender();
        svc.wireSmsSender(sender);

        svc.invokeSendSms("13800138000", "123456");
        assertEquals("13800138000", sender.last.getMobile());
        assertEquals(List.of("123456"), sender.last.getParams());
    }

    @Test
    void testSendSmsForBindingWithNullCodeFailsClosed() throws Exception {
        NopAuthUserBizModel bizModel = new NopAuthUserBizModel();
        CapturingSmsSender sender = new CapturingSmsSender();
        setField(bizModel, "smsSender", sender);

        Method sendSmsForBinding = NopAuthUserBizModel.class.getDeclaredMethod("sendSmsForBinding",
                String.class, String.class);
        sendSmsForBinding.setAccessible(true);

        InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                () -> sendSmsForBinding.invoke(bizModel, "13800138000", null),
                "null code must fail closed instead of sending params=[null]");
        assertTrue(ex.getCause() instanceof NopException, "cause must be NopException but was " + ex.getCause());
        assertEquals(ERR_AUTH_INVALID_LOGIN_REQUEST.getErrorCode(), ((NopException) ex.getCause()).getErrorCode());
        assertNull(sender.last, "no sms must be dispatched when code is missing");
    }

    @Test
    void testSendSmsForBindingWithValidCodeStillDispatches() throws Exception {
        NopAuthUserBizModel bizModel = new NopAuthUserBizModel();
        CapturingSmsSender sender = new CapturingSmsSender();
        setField(bizModel, "smsSender", sender);

        Method sendSmsForBinding = NopAuthUserBizModel.class.getDeclaredMethod("sendSmsForBinding",
                String.class, String.class);
        sendSmsForBinding.setAccessible(true);

        sendSmsForBinding.invoke(bizModel, "13800138000", "654321");
        assertEquals("13800138000", sender.last.getMobile());
        assertEquals(List.of("654321"), sender.last.getParams());
    }

    private static void setField(Object target, String name, Object value) {
        Class<?> cls = target.getClass();
        while (cls != null) {
            try {
                java.lang.reflect.Field f = cls.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                cls = cls.getSuperclass();
            } catch (IllegalAccessException e) {
                throw NopException.adapt(e);
            }
        }
        throw new IllegalArgumentException("no field " + name + " on " + target.getClass());
    }
}
