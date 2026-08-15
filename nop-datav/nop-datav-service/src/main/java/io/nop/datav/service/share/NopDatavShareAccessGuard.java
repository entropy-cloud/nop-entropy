package io.nop.datav.service.share;

import io.nop.commons.cache.CacheConfig;
import io.nop.commons.cache.LocalCache;

import java.util.function.LongSupplier;

import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_SHARE_RATE_LIMIT_MAX_KEYS;

/**
 * 分享访问两级限流 guard（plan 2026-08-15-0004-2，裁定见 permission-sharing-design.md「访问限流与访问统计」）。
 *
 * <p>R6 裁定形态：自定义固定窗口计数器 + 密码锁定时间戳的轻量 per-key 状态机（拒绝裸用平台
 * {@code DefaultRateLimiter}——平滑 permits/sec 语义无失败计数、无锁定状态，且其
 * {@code getAcquireFailCount()} 存在返回 {@code acquireSuccessCount} 的平台缺陷）。真实语义：
 * 固定窗口计数（窗口边界可能突发至 2x 上限，显式接受）；超限即时拒绝不排队。</p>
 *
 * <p>R2 裁定存储：进程内 {@link LocalCache} 容量上界（配置 {@code nop.datav.share.rate-limit.max-keys}，
 * 先例 {@code TaskFlowManagerImpl} 的 globalRateLimiters），被驱逐键的限流状态归零（等效窗口重开）。
 * 单节点语义：多节点部署各节点独立计数，集群级限流归网关层。</p>
 *
 * <p>R2 可测试性硬要求：时钟经 {@link #setClock(LongSupplier)} 可替换（默认系统时钟；测试注入
 * fake 时钟确定性推进窗口，禁止 Thread.sleep 盲等）。</p>
 */
public class NopDatavShareAccessGuard {

    private final LocalCache<String, KeyState> states;

    private volatile LongSupplier clock = System::currentTimeMillis;

    public NopDatavShareAccessGuard() {
        this.states = LocalCache.newCache("nop-datav-share-access-guard",
                CacheConfig.newConfig(Math.max(1, CFG_DATAV_SHARE_RATE_LIMIT_MAX_KEYS.get())));
    }

    /** 测试 seam：替换时钟（fake 时钟推进窗口，R2 硬要求）。 */
    public void setClock(LongSupplier clock) {
        this.clock = clock;
    }

    public long getCurrentTimeMillis() {
        return clock.getAsLong();
    }

    /**
     * 总速率检查（R3 总速率级）：尝试即计数（含被拒请求——攻击期持续保持键热度）；超限返回 false。
     * {@code maxPerWindow <= 0} 视为该层禁用，恒放行。
     */
    public boolean tryAcquireAccess(String key, int maxPerWindow, long windowMillis) {
        if (maxPerWindow <= 0) {
            return true;
        }
        KeyState state = state(key);
        synchronized (state) {
            long now = getCurrentTimeMillis();
            rollWindow(state, now, windowMillis);
            if (state.accessCount >= maxPerWindow) {
                return false;
            }
            state.accessCount++;
            return true;
        }
    }

    /** 密码锁定检查：锁定期间返回 true（即使密码正确也被拒，R3/R6 锁定语义）。 */
    public boolean isPasswordLocked(String key) {
        KeyState state = state(key);
        synchronized (state) {
            return getCurrentTimeMillis() < state.lockedUntil;
        }
    }

    /**
     * 密码失败记账（R3 密码失败级，失败专用计数）：窗口内失败数达阈值时置锁（锁定时长 = 窗口，
     * 窗口过后自动恢复）。锁定期间请求在密码比对前即被拒（R5 次序），故锁定状态下本方法不可达。
     * {@code maxFailures <= 0} 视为该层禁用。
     */
    public void recordPasswordFailure(String key, int maxFailures, long windowMillis) {
        if (maxFailures <= 0) {
            return;
        }
        KeyState state = state(key);
        synchronized (state) {
            long now = getCurrentTimeMillis();
            rollWindow(state, now, windowMillis);
            state.passwordFailures++;
            if (state.passwordFailures >= maxFailures && state.lockedUntil < now + windowMillis) {
                state.lockedUntil = now + windowMillis;
            }
        }
    }

    /** 密码成功清账：重置该键失败计数（成功验证不累积爆破预算）。 */
    public void recordPasswordSuccess(String key) {
        KeyState state = state(key);
        synchronized (state) {
            state.passwordFailures = 0;
        }
    }

    /** 锁定剩余毫秒（错误响应 retryAfterSeconds 用）；未锁定返回 0。 */
    public long getLockedRemainingMillis(String key) {
        KeyState state = state(key);
        synchronized (state) {
            return Math.max(0, state.lockedUntil - getCurrentTimeMillis());
        }
    }

    /** 当前窗口剩余毫秒（总速率超限 retryAfterSeconds 用）；窗口已滚动时返回整个窗口时长。 */
    public long getWindowRemainingMillis(String key, long windowMillis) {
        KeyState state = state(key);
        synchronized (state) {
            long elapsed = getCurrentTimeMillis() - state.windowStart;
            if (elapsed >= windowMillis) {
                return windowMillis;
            }
            return Math.max(1, windowMillis - elapsed);
        }
    }

    /** 测试 hygiene：清空全部限流状态。 */
    public void clearAllForTest() {
        states.clear();
    }

    private void rollWindow(KeyState state, long now, long windowMillis) {
        if (now - state.windowStart >= windowMillis) {
            state.windowStart = now;
            state.accessCount = 0;
            state.passwordFailures = 0;
        }
    }

    private KeyState state(String key) {
        return states.computeIfAbsent(key, k -> new KeyState(getCurrentTimeMillis()));
    }

    static final class KeyState {
        long windowStart;
        int accessCount;
        int passwordFailures;
        long lockedUntil;

        KeyState(long windowStart) {
            this.windowStart = windowStart;
        }
    }
}
