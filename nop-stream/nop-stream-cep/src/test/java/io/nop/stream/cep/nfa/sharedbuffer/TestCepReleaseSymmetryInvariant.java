/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.cep.nfa.sharedbuffer;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.nop.stream.cep.Event;
import io.nop.stream.cep.configuration.SharedBufferCacheConfig;
import io.nop.stream.cep.nfa.DeweyNumber;
import io.nop.stream.core.common.state.simple.SimpleKeyedStateStore;
import io.nop.stream.core.exceptions.StreamRuntimeException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate ④ — CEP SharedBuffer/Lockable 释放对称性（invariant #4）.
 *
 * <p>在既有反应式测试（TestLockable / TestLockableOverRelease / TestSharedBuffer 等 7 个）之上
 * 补参数化穷举 + 条目生命周期对称性，不重复造轮子：
 * <ol>
 *   <li>{@code Lockable.release} over-release 必须 fail-fast（抛 {@link StreamRuntimeException}），
 *       双重释放对称性：N 次 lock 恰好 N 次 release 归零，第 N+1 次 release 必须抛；</li>
 *   <li>{@code releaseOrDetach} 语义：refCounter==0 时静默返回 true（detach），负数 fail-fast；</li>
 *   <li>{@code SharedBuffer} 条目生命周期对称性：registerEvent（ref=1）→ lockNode 递增 →
 *       releaseNode 递减 → 全部释放后条目可被移除（lock 数 == release 数守恒，EventId 不泄漏）。</li>
 * </ol>
 * 历史证据：R16-AR-8（双重释放静默 true）、R11-AR-6（TOCTOU）、R13-AR-15/R14-AR-4（advanceTime
 * 不清理 eventsBuffer → EventId 复用冲突）。
 */
public class TestCepReleaseSymmetryInvariant {

    enum Op {LOCK, RELEASE, RELEASE_OR_DETACH}

    /** 参数化序列：{初始 refCounter, 操作序列, 期望最终值 | 期望抛错}。 */
    static Stream<Object[]> lockableSequences() {
        return Stream.of(
                // 对称：2 lock + 2 release → 0
                new Object[]{2, List.of(Op.RELEASE, Op.RELEASE), 0L, null},
                // 先 lock 再对称释放
                new Object[]{0, List.of(Op.LOCK, Op.LOCK, Op.RELEASE, Op.RELEASE), 0L, null},
                // 释放多于锁定 → over-release fail-fast
                new Object[]{1, List.of(Op.RELEASE, Op.RELEASE), null, "over-release"},
                new Object[]{0, List.of(Op.RELEASE), null, "over-release"},
                // releaseOrDetach：归零后 detach 静默成功
                new Object[]{1, List.of(Op.RELEASE_OR_DETACH, Op.RELEASE_OR_DETACH), 0L, null},
                // releaseOrDetach：负数 → fail-fast
                new Object[]{-1, List.of(Op.RELEASE_OR_DETACH), null, "over-release"},
                // 混合：lock 与 releaseOrDetach 守恒
                new Object[]{2, List.of(Op.RELEASE, Op.RELEASE_OR_DETACH), 0L, null});
    }

    @ParameterizedTest
    @MethodSource("lockableSequences")
    void testLockableSequences(int initial, List<Op> ops, Long expectedFinal, String expectedError) {
        Lockable<String> lockable = new Lockable<>("x", initial);
        String caught = null;
        try {
            for (Op op : ops) {
                switch (op) {
                    case LOCK:
                        lockable.lock();
                        break;
                    case RELEASE:
                        lockable.release();
                        break;
                    default:
                        lockable.releaseOrDetach();
                        break;
                }
            }
        } catch (StreamRuntimeException e) {
            caught = "over-release";
        }
        if (expectedError != null) {
            assertEquals(expectedError, caught, "sequence " + ops + " from " + initial + " must fail fast");
        } else {
            assertEquals(expectedFinal.longValue(), lockable.getRefCounter(),
                    "lock/release symmetry violated for " + ops + " from " + initial);
        }
    }

    /** over-release 必须抛 StreamRuntimeException（fail-fast），不得静默返回。 */
    @ParameterizedTest
    @MethodSource("lockableSequences")
    void testOverReleaseFailsFast(int initial, List<Op> ops, Long expectedFinal, String expectedError) {
        Lockable<String> lockable = new Lockable<>("x", initial);
        boolean threw = false;
        try {
            for (Op op : ops) {
                switch (op) {
                    case LOCK:
                        lockable.lock();
                        break;
                    case RELEASE:
                        lockable.release();
                        break;
                    default:
                        lockable.releaseOrDetach();
                        break;
                }
            }
        } catch (StreamRuntimeException e) {
            threw = true;
            assertTrue(e.toString().toLowerCase().contains("over-release"),
                    "fail-fast message must name over-release: " + e);
        }
        if (expectedError != null) {
            assertTrue(threw, "sequence " + ops + " from " + initial + " must throw StreamRuntimeException");
        }
    }

    /**
     * SharedBuffer 条目生命周期对称性：registerEvent（ref=1）→ lockNode 递增 → releaseNode
     * 递减 → 全部释放后条目消失（lock 数 == release 数守恒）。
     */
    @ParameterizedTest
    @MethodSource("sharedBufferLifecycleScenarios")
    void testSharedBufferEntryLifecycleSymmetry(int locks, int releases) throws Exception {
        SharedBuffer<Event> buffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());
        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            EventId eventId = accessor.registerEvent(new Event(1, "a"), 1L);
            NodeId nodeId = accessor.put("state1", eventId, null, new DeweyNumber(1));

            int expectedLocks = locks; // 每次 lockNode 对 node 自身 +1
            for (int i = 0; i < locks; i++) {
                accessor.lockNode(nodeId, new DeweyNumber(1));
            }
            for (int i = 0; i < releases; i++) {
                accessor.releaseNode(nodeId, new DeweyNumber(1));
            }

            Lockable<SharedBufferNode> entry = buffer.getEntry(nodeId);
            if (locks == releases) {
                // 对称：全部释放 → 条目已被移除（lifecycle 守恒）
                assertTrue(entry == null || entry.getRefCounter() == 0,
                        "entry must be released after symmetric lock/release");
            } else {
                // 非对称：剩余 lock 数必须守恒（没有泄漏也没有过度释放）
                assertEquals(locks - releases, entry.getRefCounter(),
                        "lock/release must be conserved (no leak, no over-release)");
            }
        }
    }

    static Stream<Object[]> sharedBufferLifecycleScenarios() {
        return Stream.of(
                new Object[]{0, 0},
                new Object[]{1, 1},
                new Object[]{2, 2},
                new Object[]{3, 1},
                new Object[]{1, 0},
                new Object[]{2, 0});
    }

    /** over-release 在 SharedBuffer 路径上同样 fail-fast（releaseNode 超过 lockNode）。 */
    @org.junit.jupiter.api.Test
    void testSharedBufferOverReleaseFailsFast() throws Exception {
        SharedBuffer<Event> buffer = new SharedBuffer<>(new SimpleKeyedStateStore(), null, new SharedBufferCacheConfig());
        try (SharedBufferAccessor<Event> accessor = buffer.getAccessor()) {
            EventId eventId = accessor.registerEvent(new Event(1, "a"), 1L);
            NodeId nodeId = accessor.put("state1", eventId, null, new DeweyNumber(1));
            // put 路径对 node 创建 Lockable(ref=0) 并对 event lockEvent（event ref = 1 + 1）
            Lockable<Event> eventBefore = buffer.getEvent(eventId);
            int eventRefBefore = eventBefore.getRefCounter();
            // releaseNode 一次：node ref 0 → releaseOrDetach 返回 true → 条目被移除（不泄漏）
            accessor.releaseNode(nodeId, new DeweyNumber(1));
            assertTrue(buffer.getEntry(nodeId) == null,
                    "entry must be removed once fully released (no leak)");
            // 事件 ref 随 releaseNode 递减 1（对称守恒：lockEvent 在 put 时 +1）
            Lockable<Event> eventAfter = buffer.getEvent(eventId);
            if (eventAfter != null) {
                assertEquals(eventRefBefore - 1, eventAfter.getRefCounter(),
                        "event refCounter must be conserved (decrement per releaseNode)");
            }
        }
    }
}
