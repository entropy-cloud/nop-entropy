/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.config.impl;

import io.nop.api.core.config.IConfigChangeListener;
import io.nop.api.core.config.IConfigProvider;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChangeSubscriptions {
    static final Logger LOG = LoggerFactory.getLogger(ChangeSubscriptions.class);

    private final Map<String, ChangeSubscription> simpleSubscriptions = new ConcurrentHashMap<>();

    private final Map<String, ChangeSubscription> patternSubscriptions = new ConcurrentHashMap<>();

    public Runnable subscribe(String pattern, IConfigChangeListener listener) {
        Guard.notEmpty(pattern, "pattern");
        Guard.notNull(listener, "listener");

        if (isSimple(pattern)) {
            return subscribeSimple(pattern, listener);
        } else {
            return subscribePattern(pattern, listener);
        }
    }

    private boolean isSimple(String pattern) {
        return pattern.indexOf("*") < 0;
    }

    private Runnable subscribeSimple(String name, IConfigChangeListener listener) {
        ChangeSubscription sub = simpleSubscriptions.computeIfAbsent(name, ChangeSubscription::new);
        sub.addListener(listener);
        return () -> sub.removeListener(listener);
    }

    private Runnable subscribePattern(String pattern, IConfigChangeListener listener) {
        // 通配 pattern 订阅必须登记到 patternSubscriptions：trigger() 仅从该 map 读取
        // pattern 订阅做模糊匹配，simpleSubscriptions 的 key 是精确变量名，pattern 不可能命中
        ChangeSubscription sub = patternSubscriptions.computeIfAbsent(pattern, ChangeSubscription::new);
        sub.addListener(listener);
        return () -> sub.removeListener(listener);
    }

    public void trigger(IConfigProvider provider, Map<String, Object> oldValues) {
        Set<String> names = oldValues.keySet();

        Set<IConfigChangeListener> triggered = new HashSet<>();

        for (String name : names) {
            ChangeSubscription sub = simpleSubscriptions.get(name);
            if (sub != null) {
                triggered.addAll(sub.getListeners());
            }
        }

        for (Map.Entry<String, ChangeSubscription> entry : patternSubscriptions.entrySet()) {
            String pattern = entry.getKey();
            ChangeSubscription sub = entry.getValue();
            if (matchPattern(pattern, names)) {
                triggered.addAll(sub.getListeners());
            }
        }

        for (IConfigChangeListener listener : triggered) {
            try {
                listener.onConfigChange(provider, oldValues);
            } catch (Exception e) {
                // 单个监听器异常只记录并继续：不得中断其余监听器与后续配置传播
                //（属性重绑定失败不得导致本轮其余 bean 的配置更新静默丢失）
                LOG.error("nop.config.trigger-change-listener-fail:listener={}",
                        listener.getClass().getName(), e);
            }
        }
    }

    private boolean matchPattern(String pattern, Set<String> names) {
        for (String name : names) {
            if (StringHelper.matchSimplePattern(name, pattern))
                return true;
        }
        return false;
    }
}
