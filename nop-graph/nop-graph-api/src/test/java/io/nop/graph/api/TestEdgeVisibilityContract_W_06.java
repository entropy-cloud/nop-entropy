package io.nop.graph.api;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 5.6-W-06（plan 2529 Phase 2 组 B / 设计裁定④）Edge.attrs 可见性契约回归钉。
 *
 * <p><b>主保护 = 反射结构钉</b>（确定性当前即红——可见性缺陷不可常规 JUnit 确定性钉死，
 * C10 口径）：{@code attrs} 字段修饰符必须含 {@code volatile}，构造后 {@code setAttrs}
 * 的发布对并发读取线程可见（裁定④.1）。其余 4 字段（sourceId/targetId/weight/type）保持
 * final，值语义（equals/hashCode 不读 attrs）零变更。
 *
 * <p><b>辅 = 多线程压力弱断言</b>（如实标注间歇红可能——2528 红③ 先例口径）：读线程观测的
 * 引用必须属于已发布引用集 ∪ {未发布空 map}；不作 happens-before 判定，结构钉才是主保护。
 *
 * <p>契约范围（显式收窄，防过度承诺）：volatile 只保证 attrs <b>引用</b>的发布可见性，
 * 不保护已发布 map <b>内容</b>的并发改写——构造完成后调用方不得并发改写已发布的 map
 * （或自行使用并发 Map）。现网唯一生产写点 {@code CodeRelationGraph} ctor :53 为构造期写点
 * （安全性来自持有者安全发布），本契约面向的是 javadoc 明文邀请的「构造后注入」未来消费方。
 */
class TestEdgeVisibilityContract_W_06 {

    /**
     * 结构钉（主保护）：pre-fix 0 volatile，确定性红；post-fix 确定性绿。
     */
    @Test
    void testW_06_attrsFieldIsVolatile() throws Exception {
        int modifiers = Edge.class.getDeclaredField("attrs").getModifiers();
        assertTrue(Modifier.isVolatile(modifiers),
                "Edge.attrs must be volatile (visibility contract for post-construction "
                        + "setAttrs publication), modifiers=" + Modifier.toString(modifiers));
    }

    /**
     * 压力弱断言（辅）：写线程在固定引用集间交替 setAttrs，读线程断言 getAttrs() 返回的
     * 引用 ∈ {emptyMap（未发布态）, 已写入引用}（identity 判定）。间歇红可能如实标注：
     * pre-fix 无 volatile 时 stale 读大多落在允许集内，此断言不作 happens-before 证明。
     */
    @Test
    void testW_06_publishedAttrsReferenceObservableUnderPressure() throws Exception {
        Edge edge = new Edge("a", "b");
        Map<String, Object> publishedA = new HashMap<>();
        publishedA.put("k", "A");
        Map<String, Object> publishedB = new HashMap<>();
        publishedB.put("k", "B");
        Map<String, Object> emptyMap = Collections.emptyMap();

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger violations = new AtomicInteger();
        AtomicInteger reads = new AtomicInteger();

        Thread writer = new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < 50_000; i++) {
                    edge.setAttrs((i & 1) == 0 ? publishedA : publishedB);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        }, "w06-writer");
        Thread reader = new Thread(() -> {
            try {
                start.await();
                for (int i = 0; i < 50_000; i++) {
                    Map<String, Object> seen = edge.getAttrs();
                    reads.incrementAndGet();
                    if (seen != emptyMap && seen != publishedA && seen != publishedB) {
                        violations.incrementAndGet();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        }, "w06-reader");

        writer.start();
        reader.start();
        start.countDown();
        done.await();

        assertTrue(violations.get() == 0,
                "getAttrs must only observe the empty map (unpublished) or a published reference, "
                        + "violations=" + violations.get() + "/" + reads.get());
        // the writer's final publication must be one of the two published maps
        Map<String, Object> finalSeen = edge.getAttrs();
        assertTrue(finalSeen == publishedA || finalSeen == publishedB,
                "final published reference must be visible to this thread");
    }
}
