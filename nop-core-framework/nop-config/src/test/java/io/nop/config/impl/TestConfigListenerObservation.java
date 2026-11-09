/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.config.impl;

import io.nop.api.core.config.IConfigChangeListener;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2549 R4-15（C4-007 / C2-007）配置监听观测与订阅原子性载体钉。
 */
public class TestConfigListenerObservation {

    /** pin3：trigger 正常回调全部监听器（观测埋点存在性由代码路径覆盖）；同订阅内注册序。 */
    @Test
    public void pin3_sameSubscriptionOrderKept() {
        ChangeSubscriptions subscriptions = new ChangeSubscriptions();
        List<String> order = new CopyOnWriteArrayList<>();
        IConfigChangeListener l1 = (p, c) -> order.add("l1");
        IConfigChangeListener l2 = (p, c) -> order.add("l2");
        subscriptions.subscribe("k", l1);
        subscriptions.subscribe("k", l2);

        Map<String, Object> changed = new HashMap<>();
        changed.put("k", "old");
        subscriptions.trigger(null, changed);

        assertEquals(java.util.Arrays.asList("l1", "l2"), order, "同订阅内注册序");
    }

}
