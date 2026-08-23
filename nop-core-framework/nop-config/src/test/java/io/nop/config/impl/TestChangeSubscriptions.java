/**
 * 通配 pattern 配置订阅回归测试：
 * 修复前 subscribePattern 误写入 simpleSubscriptions（trigger 的精确名 map），
 * 带 * 的订阅静默永不回调，违反 IConfigProvider.subscribeChange 的公开契约
 * （"最后一个部分可以是*，表示模糊匹配"）。
 */
package io.nop.config.impl;

import io.nop.api.core.config.IConfigChangeListener;
import io.nop.api.core.config.IConfigProvider;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestChangeSubscriptions {

    private final AtomicInteger triggered = new AtomicInteger();

    private final IConfigChangeListener listener = new IConfigChangeListener() {
        @Override
        public void onConfigChange(IConfigProvider provider, Map<String, Object> oldValues) {
            triggered.incrementAndGet();
        }
    };

    private Map<String, Object> oldValues(String... names) {
        Map<String, Object> map = new HashMap<>();
        for (String name : names) {
            map.put(name, "v");
        }
        return map;
    }

    @Test
    public void testWildcardSubscriptionIsTriggered() {
        ChangeSubscriptions subscriptions = new ChangeSubscriptions();
        subscriptions.subscribe("a.b.*", listener);

        subscriptions.trigger(null, oldValues("a.b.c"));
        assertEquals(1, triggered.get(), "a.b.* 订阅必须被 a.b.c 的变更触发（公开契约：末段 * 模糊匹配）");

        subscriptions.trigger(null, oldValues("a.b.d"));
        assertEquals(2, triggered.get(), "通配订阅对每个匹配的变更各触发一次");

        // 不匹配的变更不触发
        subscriptions.trigger(null, oldValues("a.x.c"));
        assertEquals(2, triggered.get(), "不匹配通配 pattern 的变更不得触发订阅");
    }

    @Test
    public void testSimpleSubscriptionStillWorks() {
        ChangeSubscriptions subscriptions = new ChangeSubscriptions();
        subscriptions.subscribe("a.b.c", listener);

        subscriptions.trigger(null, oldValues("a.b.c"));
        assertEquals(1, triggered.get(), "精确名订阅行为不变");

        subscriptions.trigger(null, oldValues("a.b.d"));
        assertEquals(1, triggered.get(), "精确名订阅只匹配同名变更");
    }

    @Test
    public void testUnsubscribeStopsTriggering() {
        ChangeSubscriptions subscriptions = new ChangeSubscriptions();
        Runnable unsubscribe = subscriptions.subscribe("a.b.*", listener);

        subscriptions.trigger(null, oldValues("a.b.c"));
        assertEquals(1, triggered.get());

        unsubscribe.run();
        subscriptions.trigger(null, oldValues("a.b.c"));
        assertEquals(1, triggered.get(), "退订后不得再触发");
    }

    @Test
    public void testSimpleAndWildcardCoexist() {
        ChangeSubscriptions subscriptions = new ChangeSubscriptions();
        subscriptions.subscribe("a.b.c", listener);
        subscriptions.subscribe("a.b.*", listener);

        // trigger 以 HashSet<IConfigChangeListener> 去重：同一 listener 实例同时命中
        // 精确与通配订阅也只回调一次（观察的是订阅可达性，不是重复计数）
        subscriptions.trigger(null, oldValues("a.b.c"));
        assertEquals(1, triggered.get(), "精确与通配订阅都必须可达（回调去重为一次）");

        subscriptions.trigger(null, oldValues("a.b.d"));
        assertEquals(2, triggered.get(), "仅通配订阅命中");
    }
}
