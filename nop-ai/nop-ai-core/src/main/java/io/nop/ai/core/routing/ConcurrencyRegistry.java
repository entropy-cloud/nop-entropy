package io.nop.ai.core.routing;

import io.nop.ai.core.NopAiCoreErrors;
import io.nop.ai.core.NopAiCoreException;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 进程内 per-账号并发计数注册表（plan 2026-08-15-0849-2，设计 §3.3）。
 *
 * <p>计数键 = {@code (provider, accountKey)}——与 {@code LlmConfigHelper.resolveConcurrencyLimit}
 * 的消费键一致：主账号（无 {@code LlmAccountModel} 实例）= {@code (provider, null)}。
 * 并发上限（{@code concurrencyLimit}）不在本注册表内判定——上限已由候选解析期解析（W3 层级语义），
 * 饱和判定由 {@code CandidateHealth} 完成（null/≤0 = 不限制）。
 *
 * <p><b>语义契约</b>（plan 落档）：acquire/release 是纯计数器原语（不检查上限）；
 * "流建立 +1 / 结束 -1"的挂钩时点 = {@code callStream} 调用时刻而非订阅时刻（W1 spike 输入），
 * 真实调用路径挂钩编排归 W6/W7——本类只交付计数原语 + 当前值查询。
 *
 * <p><b>release 下溢语义（裁定，plan Phase 1/4）</b>：计数已为 0 时重复 release = acquire/release
 * 不配对（编排缺陷）→ 显式 fail-fast（{@code ERR_AI_AGENT_INVALID_ARG}），不静默钳制为 0
 * （Minimum Rules #24——不把缺陷伪装成正常状态）。
 *
 * <p><b>线程安全（P2-REL 硬化）</b>：acquire/release 的全部计数变更都在
 * {@link ConcurrentHashMap#compute} 的 per-key 原子临界区内执行——同键的并发
 * acquire/release 串行化，因此：release 下溢检查与减法之间不存在被并发 acquire 插入的窗口
 * （旧实现 decrementAndGet 后 incrementAndGet 补偿会与并发 acquire 竞争产生永久 +1 幽灵计数），
 * 且计数归零的键可安全移除（map 收缩，长跑网关不泄漏）。下溢仍 fail-fast 抛错，计数保持 0。
 *
 * <p><b>计数表收缩</b>：release 使计数归零时对应键从 map 移除；后续 {@link #currentCount}
 * 对缺席键返回 0（与零计数语义一致），再次 acquire 重建计数。移除与并发 acquire 在
 * compute 原子区内互斥，不会丢失或重复计数。
 */
public final class ConcurrencyRegistry {

    private final ConcurrentMap<Key, AtomicInteger> counts = new ConcurrentHashMap<>();

    /**
     * 计数 +1（in-flight 请求建立时调用）。
     *
     * <p>经 {@code counts.compute} 在 per-key 原子临界区内自增——与同键的移除（归零收缩）
     * 互斥，杜绝"引用已移除计数器后自增"的孤儿计数。
     *
     * @param provider   provider 名称（非 null）
     * @param accountKey 账号 key（备用账号 apiKey / 主账号 null）；null = 主账号
     * @return 递增后的当前计数
     */
    public int acquire(String provider, String accountKey) {
        Key k = key(provider, accountKey);
        int[] holder = new int[1];
        counts.compute(k, (key, existing) -> {
            AtomicInteger counter = existing != null ? existing : new AtomicInteger();
            holder[0] = counter.incrementAndGet();
            return counter;
        });
        return holder[0];
    }

    /**
     * 计数 -1（in-flight 请求结束时调用）。
     *
     * <p>经 {@code counts.compute} 在 per-key 原子临界区内"先比较后减"：计数为 0 时直接
     * fail-fast（{@code ERR_AI_AGENT_INVALID_ARG}），不递减、不补偿——与并发 acquire 之间
     * 不存在可插入的竞争窗口，因此不会产生幽灵 +1 计数。递减到 0 时返回 null 移除该键
     * （计数表收缩）；计数保持不变（并发 acquire 已把 0 推回 ≥1）时保留。
     *
     * @param provider   provider 名称（非 null）
     * @param accountKey 账号 key（null = 主账号）
     * @return 递减后的当前计数
     * @throws NopAiCoreException 当计数已为 0（下溢 = acquire/release 不配对，fail-fast）
     */
    public int release(String provider, String accountKey) {
        Key k = key(provider, accountKey);
        int[] holder = new int[1];
        counts.compute(k, (key, existing) -> {
            AtomicInteger counter = existing != null ? existing : new AtomicInteger();
            int current = counter.get();
            if (current <= 0) {
                // 计数已为 0 时重复 release：显式失败而非静默钳制（裁定：编排缺陷 fail-fast）。
                // compute 内抛异常不改动 map 条目（计数保持 0，无幽灵 +1）。
                throw new NopAiCoreException(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG)
                        .param(NopAiCoreErrors.ARG_MSG,
                                "concurrency counter underflow: release without matching acquire (provider="
                                        + provider + ", accountKey=" + accountKey + ")");
            }
            holder[0] = current - 1;
            counter.set(holder[0]);
            // 计数归零 → 移除该键（计数表收缩；并发 acquire 与移除在 compute 内原子互斥，
            // 不丢失/不重复计数）。返回 null = 移除条目，CHM compute 语义。
            return holder[0] == 0 ? null : counter;
        });
        return holder[0];
    }

    /**
     * 查询当前计数（健康视图读取入口——router 经 {@code CandidateHealthProvider} 在运行时读取本值）。
     * 缺席键（含收缩移除后）返回 0，与零计数语义一致。
     */
    public int currentCount(String provider, String accountKey) {
        AtomicInteger counter = counts.get(key(provider, accountKey));
        return counter != null ? counter.get() : 0;
    }

    private static Key key(String provider, String accountKey) {
        if (provider == null) {
            throw new NopAiCoreException(NopAiCoreErrors.ERR_AI_AGENT_INVALID_ARG)
                    .param(NopAiCoreErrors.ARG_MSG, "concurrency registry key provider must not be null");
        }
        return new Key(provider, accountKey);
    }

    /**
     * 计数键 = (provider, accountKey)；主账号 = (provider, null)。
     * Java 11 source level 下不用 record（模块编译级约束）。
     */
    private static final class Key {
        private final String provider;
        private final String accountKey;

        private Key(String provider, String accountKey) {
            this.provider = Objects.requireNonNull(provider, "provider");
            this.accountKey = accountKey;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key)) {
                return false;
            }
            Key that = (Key) o;
            return provider.equals(that.provider) && Objects.equals(accountKey, that.accountKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(provider, accountKey);
        }
    }
}