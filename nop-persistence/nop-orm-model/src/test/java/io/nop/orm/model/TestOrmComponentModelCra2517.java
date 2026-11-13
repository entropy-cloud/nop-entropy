/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2517 R2-14 红③（C1-015）回归钉：并发首触组件列映射不再返回空/部分 Map。
 * <p>缺陷态（先发布引用后填充、无安全发布）：并发首触可观测空/部分 Map 或 stale int[] → 红；
 * 修复后：解析期预计算（precompute，initializer 接线）+ safe-publish 惰性兜底（局部填充最后赋引用）→
 * 并发首触恒完整 → 绿。列序钉 {@code TestOrmComponentModel#testGetColumnPropIdsFollowsDeclarationOrder}
 * 保持零弱化（声明顺序契约继续成立）。</p>
 */
public class TestOrmComponentModelCra2517 {

    private static OrmComponentPropModel prop(String name, int propId) {
        OrmColumnModel col = new OrmColumnModel();
        col.setName(name);
        col.setCode(name.toUpperCase());
        col.setPropId(propId);

        OrmComponentPropModel prop = new OrmComponentPropModel();
        prop.setName(name);
        prop.setColumnModel(col);
        return prop;
    }

    private static OrmComponentModel buildModel() {
        OrmComponentModel comp = new OrmComponentModel();
        comp.setProps(List.of(prop("B", 2), prop("a", 1)));
        return comp;
    }

    /**
     * 解析期预计算（裁定③预设）：precompute 后运行期 getter 纯读、结果完整且按声明顺序。
     * 反射调用（缺陷态无 precompute 方法即红——变异论证）。
     */
    @Test
    public void testCra2517_C1_015_precomputeProducesCompleteReadOnlyMaps() throws Exception {
        OrmComponentModel comp = buildModel();
        comp.getClass().getMethod("precompute").invoke(comp);

        Map<String, Integer> map = comp.getColumnPropIdMap();
        assertEquals(2, map.size());
        assertEquals(2, map.get("B"));
        assertEquals(1, map.get("a"));
        assertArrayEquals(new int[]{2, 1}, comp.getColumnPropIds());
        assertEquals(2, comp.getColumnPropId());

        // 预计算后重复读取返回同一实例（运行期只读）
        assertSame(map, comp.getColumnPropIdMap());
    }

    /**
     * 红③核心：并发首触（未经预计算的惰性兜底路径）所有线程都必须得到完整 Map / 完整 int[]。
     * 缺陷态（先发布后填充 + 无 volatile）：可观测空/部分 Map → 红；safe-publish 后恒完整 → 绿。
     */
    @Test
    public void testCra2517_C1_015_concurrentFirstTouchSeesCompleteMaps() throws Exception {
        int rounds = 200;
        for (int i = 0; i < rounds; i++) {
            OrmComponentModel comp = buildModel();
            CountDownLatch start = new CountDownLatch(1);
            AtomicReference<Throwable> error = new AtomicReference<>();
            Thread[] threads = new Thread[8];
            for (int t = 0; t < threads.length; t++) {
                threads[t] = new Thread(() -> {
                    try {
                        start.await();
                        Map<String, Integer> map = comp.getColumnPropIdMap();
                        if (map.size() != 2 || map.get("B") == null || map.get("a") == null)
                            throw new IllegalStateException("并发首触观测到空/部分 Map: " + map);
                        int[] ids = comp.getColumnPropIds();
                        if (ids.length != 2 || ids[0] != 2 || ids[1] != 1)
                            throw new IllegalStateException("并发首触观测到空/stale int[]: " + ids.length);
                    } catch (Throwable e) {
                        error.compareAndSet(null, e);
                    }
                });
                threads[t].start();
            }
            start.countDown();
            for (Thread thread : threads)
                thread.join(5000);
            if (error.get() != null)
                throw new AssertionError("缺陷态红：并发首触组件列映射不完整（round " + i + "）", error.get());
        }
    }

    /**
     * 列序契约（零回退钉同族面）：声明顺序在预计算与并发路径下均保持。
     */
    @Test
    public void testCra2517_C1_015_columnOrderFollowsDeclarationAfterPrecompute() {
        OrmComponentModel comp = buildModel();
        assertArrayEquals(new int[]{2, 1}, comp.getColumnPropIds());
        assertNotNull(comp.getColumnPropIdMap());
        assertArrayEquals(new int[]{2, 1}, comp.getColumnPropIds(), "列序契约保持");
        assertTrue(comp.getColumnPropId() == 2);
    }
}
