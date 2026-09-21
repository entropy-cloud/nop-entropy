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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * check 审计 nop-auth 报告两条 LoginServiceImpl 条目的回归：
 * <ul>
 *   <li>[P3] 限流时间戳更新非原子——并发突发可同时通过间隔检查，实际发送间隔小于配置值；
 *       修复后间隔检查与 lastSendMs 占用在同一原子区，同手机号并发突发恰好放行 1 个。
 *       plan 2274 Phase 1：限流实现收敛至 {@code LocalSendCodeRateLimiter}，32 线程并发原子性
 *       回归平移至 {@code TestLocalSendCodeRateLimiter#testConcurrentBurstAllowsExactlyOne}
 *       （同语义新落点）；本类保留经 LoginServiceImpl 入口的接线与顺序语义验证。</li>
 *   <li>[P3] 登录失败审计在 failCount&gt;1 时覆盖丢失 loginType/principalId——
 *       暴力破解排查恰是最需要这两个字段的场景。</li>
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

    /**
     * 暴露protected检查路径（经 LoginServiceImpl 限流入口 → 限流组件，plan 2274 Phase 1 接线证据）：
     * 缺省 send-interval 为正数，同手机号第二次起应被间隔拒绝。
     */
    static class TestableLoginService extends LoginServiceImpl {
        void smsRateLimit(String phone, String clientIp) {
            rateLimiter().checkSmsAllowed(io.nop.auth.service.ratelimit.ISendCodeRateLimiter.SCOPE_LOGIN,
                    phone, clientIp);
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
     * 顺序间隔语义（经 LoginServiceImpl 入口的接线验证）：同手机号第二次发送被间隔拒绝。
     * 32 线程并发原子性回归见 {@code TestLocalSendCodeRateLimiter#testConcurrentBurstAllowsExactlyOne}
     * （plan 2274 Phase 1 平移，断言语义不变）。
     */
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
