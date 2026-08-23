package io.nop.sys.dao.message;

import io.nop.api.core.beans.ApiRequest;
import io.nop.dao.coderule.CodeRuleParams;
import io.nop.sys.dao.coderule.DefaultCodeRule;
import io.nop.sys.dao.entity.NopSysEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * check 审计 nop-sys 报告多条修复的回归：
 * <ul>
 *   <li>[P1] 非ApiRequest消息不再被序列化为空"{}"（ack回路返回值/纯文本消息内容丢失）；</li>
 *   <li>[P1] 事件消费周期任务入口异常保护（BindScheduledExecutor抛Throwable即永久停摆）；</li>
 *   <li>[P2] SysDaoNamingService.cleanup 周期任务异常保护（一次DB异常即永久取消）；</li>
 *   <li>[P2] randNumber 强制正数+补零（原约半数编码带'-'前缀、小count时substring越界）。</li>
 * </ul>
 */
public class TestSysEventPayloadAndScheduleProtection {

    @Test
    public void testNonApiRequestMessagePayloadNotLost() {
        NopSysEvent event = new NopSysEvent();
        SysEventHelper.toSysEvent(event, "test-topic", "hello-world", System.currentTimeMillis());
        assertEquals("\"hello-world\"", event.getEventData(),
                "plain string message must be serialized as-is, not empty {}");

        Map<String, Object> data = new HashMap<>();
        data.put("key", "value");
        NopSysEvent mapEvent = new NopSysEvent();
        SysEventHelper.toSysEvent(mapEvent, "test-topic", data, System.currentTimeMillis());
        assertTrue(mapEvent.getEventData().contains("\"key\"") && mapEvent.getEventData().contains("value"),
                "map message must keep content, got: " + mapEvent.getEventData());
    }

    @Test
    public void testApiRequestPayloadUnchanged() {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        Map<String, Object> data = new HashMap<>();
        data.put("field", "x");
        request.setData(data);
        NopSysEvent event = new NopSysEvent();
        SysEventHelper.toSysEvent(event, "test-topic", request, System.currentTimeMillis());
        assertTrue(event.getEventData().contains("field"), "ApiRequest payload path unchanged");
    }

    /** 周期任务入口吞异常：processor初始化/DB异常只记日志，不得向调度器抛出（否则任务永久停摆）。 */
    @Test
    public void testProcessEntryPointsSwallowExceptions() {
        SysDaoMessageService service = new SysDaoMessageService();
        // daoProvider未装配 → process入口内部抛NPE，但不得向上传播
        org.junit.jupiter.api.function.Executable[] entries = {
                service::processNonBroadcastEvent, service::processBroadcastEvent, service::cleanupExpiredEvents};
        for (org.junit.jupiter.api.function.Executable entry : entries) {
            assertDoesNotThrow(entry);
        }
    }

    /** randNumber：无负号前缀、长度恒为count（原BigInteger(byte[])有符号解析约半数带'-'且可能越界）。 */
    @Test
    public void testRandNumberAlwaysPositiveAndPadded() {
        TestableCodeRule rule = new TestableCodeRule();
        CodeRuleParams params = new CodeRuleParams("", LocalDateTime.now(), () -> 1L, null);
        for (int i = 0; i < 200; i++) {
            String value = rule.generateRand("2", params);
            assertEquals(2, value.length(), "rand segment must always be exactly count chars: " + value);
            assertFalse(value.startsWith("-"), "rand segment must never carry sign: " + value);
            assertTrue(value.chars().allMatch(Character::isDigit));
        }
    }

    static class TestableCodeRule extends DefaultCodeRule {
        @Override
        protected String generateRand(String options, CodeRuleParams params) {
            return super.generateRand(options, params);
        }
    }
}
