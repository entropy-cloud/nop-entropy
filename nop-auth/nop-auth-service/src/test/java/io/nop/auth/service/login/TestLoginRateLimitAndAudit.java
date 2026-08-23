package io.nop.auth.service.login;

import io.nop.api.core.audit.AuditRequest;
import io.nop.api.core.audit.IAuditService;
import io.nop.api.core.exceptions.NopException;
import io.nop.auth.api.AuthApiConstants;
import io.nop.auth.api.messages.LoginRequest;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.unittest.BaseTestCase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * check 审计 nop-auth 报告两条 LoginServiceImpl 条目的回归：
 * <ul>
 *   <li>[P3] 限流时间戳更新非原子——并发突发可同时通过间隔检查，实际发送间隔小于配置值；
 *   修复后间隔检查与 lastSendMs 占用在同一原子区，同手机号并发突发恰好放行 1 个。</li>
 *   <li>[P3] 登录失败审计在 failCount&gt;1 时覆盖丢失 loginType/principalId——
 *   暴力破解排查恰是最需要这两个字段的场景。</li>
 * </ul>
 */
public class TestLoginRateLimitAndAudit extends BaseTestCase {

    @BeforeAll
    public static void init() {
        CoreInitialization.initialize();
    }

    @AfterAll
    public static void destroy() {
        CoreInitialization.destroy();
    }

    /** 暴露protected检查方法（缺省send-interval为正数，同手机号第二次起应被间隔拒绝）。 */
    static class TestableLoginService extends LoginServiceImpl {
        void smsRateLimit(String phone, String clientIp) {
            checkSmsRateLimit(phone, clientIp);
        }

        void auditFail(String errorCode, String defaultMessage, LoginRequest request,
                       io.nop.auth.dao.entity.NopAuthUser user, long beginTime, int failCount) {
            auditLogFail(errorCode, defaultMessage, request, user, beginTime, failCount);
        }
    }

    static class RecordingAuditService implements IAuditService {
        final List<AuditRequest> requests = new CopyOnWriteArrayList<>();

        @Override
        public void saveAudit(AuditRequest request) {
            requests.add(request);
        }

        @Override
        public boolean isAllProcessed() {
            return true;
        }
    }

    /**
     * 32线程CyclicBarrier对齐后同时对同一手机号发起checkSmsRateLimit：
     * 修复前lastSendMs在compute外裸写，多个线程可同时读到旧时间戳并同时通过间隔检查；
     * 修复后检查+占用原子化，恰好1个线程通过。
     */
    @Test
    public void testConcurrentBurstAllowsExactlyOne() throws Exception {
        TestableLoginService service = new TestableLoginService();
        int threads = 32;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        AtomicInteger passed = new AtomicInteger();
        List<Throwable> unexpected = new CopyOnWriteArrayList<>();

        Thread[] ts = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            ts[i] = new Thread(() -> {
                try {
                    barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
                    service.smsRateLimit("13800138000", null);
                    passed.incrementAndGet();
                } catch (NopException e) {
                    // 预期的RATE_LIMITED/DAILY_LIMIT拒绝
                } catch (Throwable e) {
                    unexpected.add(e);
                }
            });
        }
        for (Thread t : ts)
            t.start();
        for (Thread t : ts)
            t.join(30000);

        assertEquals(List.of(), unexpected, "no unexpected exceptions");
        assertEquals(1, passed.get(), "concurrent burst on same phone must allow exactly one request");
    }

    @Test
    public void testSequentialSecondSendWithinIntervalRejected() {
        TestableLoginService service = new TestableLoginService();
        service.smsRateLimit("13900139000", null);
        NopException err = org.junit.jupiter.api.Assertions.assertThrows(NopException.class,
                () -> service.smsRateLimit("13900139000", null));
        assertTrue(err.getErrorCode().contains("rate-limit") || err.getErrorCode().contains("daily-limit"),
                "second send within interval must be rejected, got: " + err.getErrorCode());
    }

    @Test
    public void testAuditFailKeepsLoginTypeAndPrincipalIdWhenFailCountGreaterThanOne() {
        TestableLoginService service = new TestableLoginService();
        RecordingAuditService audit = new RecordingAuditService();
        service.auditService = audit;

        LoginRequest request = new LoginRequest();
        request.setLoginType(AuthApiConstants.LOGIN_TYPE_USERNAME_PASSWORD);
        request.setPrincipalId("brute-force-target");

        service.auditFail("nop.err.auth.login-check-fail", "login check fail", request,
                null, 0L, 3);

        assertEquals(1, audit.requests.size());
        String requestData = audit.requests.get(0).getRequestData();
        assertTrue(requestData.contains("brute-force-target"),
                "requestData must keep principalId for brute-force investigation, got: " + requestData);
        assertTrue(requestData.contains("loginType"),
                "requestData must keep loginType, got: " + requestData);
        assertTrue(requestData.contains("failCount"),
                "requestData must carry failCount, got: " + requestData);
    }

    /** 对照组：failCount=1时requestData不含failCount（与一期形态一致）。 */
    @Test
    public void testAuditFailFirstAttemptHasNoFailCount() {
        TestableLoginService service = new TestableLoginService();
        RecordingAuditService audit = new RecordingAuditService();
        service.auditService = audit;

        LoginRequest request = new LoginRequest();
        request.setLoginType(AuthApiConstants.LOGIN_TYPE_USERNAME_PASSWORD);
        request.setPrincipalId("user-a");

        service.auditFail("nop.err.auth.login-check-fail", "login check fail", request,
                null, 0L, 1);

        Map<String, Object> data = io.nop.core.lang.json.JsonTool.parseBeanFromText(
                audit.requests.get(0).getRequestData(), Map.class);
        assertTrue(!data.containsKey("failCount"), "first failure should not carry failCount");
        assertEquals("user-a", data.get("principalId"));
    }
}
