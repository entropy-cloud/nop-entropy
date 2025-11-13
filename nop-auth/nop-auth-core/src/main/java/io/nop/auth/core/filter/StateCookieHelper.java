package io.nop.auth.core.filter;

import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.Guard;
import io.nop.auth.core.AuthCoreConfigs;
import io.nop.commons.util.StringHelper;
import io.nop.http.api.server.IHttpServerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpCookie;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * OAuth State Cookie 管理助手
 * 负责state参数的生成、验证和管理，防止CSRF攻击
 */
public class StateCookieHelper {
    private static final Logger LOG = LoggerFactory.getLogger(StateCookieHelper.class);

    private static final String DEFAULT_STATE_COOKIE_NAME = "OAuth_Token_Request_State";
    private static final AtomicLong counter = new AtomicLong();

    private final String cookieName;
    private final long stateTimeoutMs;
    private final boolean secureCookie;

    public StateCookieHelper() {
        this(DEFAULT_STATE_COOKIE_NAME, 10 * 60 * 1000L,
                AuthCoreConfigs.CFG_AUTH_USE_SECURE_COOKIE.get());
    }

    public StateCookieHelper(String cookieName, long stateTimeoutMs, boolean secureCookie) {
        this.cookieName = Guard.notEmpty(cookieName, "cookieName");
        this.stateTimeoutMs = Guard.positiveLong(stateTimeoutMs, "stateTimeoutMs");
        this.secureCookie = secureCookie;
    }

    /**
     * 生成带时间戳的state code
     */
    public String generateStateCode() {
        long timestamp = CoreMetrics.currentTimeMillis();
        return counter.getAndIncrement() + "/" + UUID.randomUUID() + "/" + timestamp;
    }

    /**
     * 设置state cookie
     */
    public void setStateCookie(String state, IHttpServerContext context) {
        Guard.notNull(state, "state");
        Guard.notNull(context, "context");

        HttpCookie cookie = new HttpCookie(cookieName, state);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(secureCookie);
        cookie.setMaxAge((int) (stateTimeoutMs / 1000)); // 转换为秒

        context.addCookie("Strict", cookie);
        LOG.debug("nop.auth.oauth-state-cookie-set:name={}", cookieName);
    }

    /**
     * 验证state参数并清除cookie
     */
    public boolean validateAndClearState(String receivedState, IHttpServerContext context) {
        if (StringHelper.isEmpty(receivedState)) {
            LOG.debug("nop.auth.oauth-empty-received-state");
            return false;
        }

        String storedState = context.getCookie(cookieName);
        if (StringHelper.isEmpty(storedState)) {
            LOG.warn("nop.auth.oauth-missing-stored-state");
            return false;
        }

        // 安全的state比较
        if (!receivedState.equals(storedState)) {
            LOG.warn("nop.auth.oauth-state-mismatch:expected={},actual={}",
                    maskState(storedState), maskState(receivedState));
            clearStateCookie(context);
            return false;
        }

        // 验证时间戳
        if (!isValidStateTimestamp(receivedState)) {
            LOG.warn("nop.auth.oauth-state-expired:state={}", maskState(receivedState));
            clearStateCookie(context);
            return false;
        }

        // 验证通过，清除cookie
        clearStateCookie(context);
        LOG.debug("nop.auth.oauth-state-validated");
        return true;
    }

    /**
     * 清除state cookie
     */
    public void clearStateCookie(IHttpServerContext context) {
        HttpCookie cookie = new HttpCookie(cookieName, "");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(secureCookie);
        cookie.setMaxAge(0); // 立即过期
        context.addCookie("Strict", cookie);
        LOG.debug("nop.auth.oauth-state-cookie-cleared:name={}", cookieName);
    }

    /**
     * 验证state时间戳是否在有效期内
     */
    private boolean isValidStateTimestamp(String state) {
        try {
            String[] parts = state.split("/");
            if (parts.length < 3) {
                return false;
            }

            long timestamp = Long.parseLong(parts[2]);
            long currentTime = CoreMetrics.currentTimeMillis();
            long stateAge = currentTime - timestamp;

            return stateAge <= stateTimeoutMs && stateAge >= 0;
        } catch (Exception e) {
            LOG.debug("nop.auth.oauth-invalid-state-format:state={}", maskState(state), e);
            return false;
        }
    }

    /**
     * 对state进行脱敏处理
     */
    private String maskState(String state) {
        if (StringHelper.isEmpty(state) || state.length() <= 8) {
            return "***";
        }
        return state.substring(0, 4) + "***" + state.substring(state.length() - 4);
    }

    /**
     * 获取cookie名称（用于配置等）
     */
    public String getCookieName() {
        return cookieName;
    }
}