package io.nop.plugin.manager.impl;

import io.nop.api.core.config.CastTypeConfigReference;
import io.nop.api.core.config.DefaultConfigReference;
import io.nop.api.core.config.IConfigChangeListener;
import io.nop.api.core.config.IConfigProvider;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.config.SimpleConfigProvider;
import io.nop.api.core.util.ApiStringHelper;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.util.StaticValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 实例配置域 provider——委托包装器（设计文档 01-architecture-baseline.md §四"实例配置域的实现路径"）。
 *
 * <p>读路径 + {@code subscribeChange} 均委托全局 provider（{@link SimpleConfigProvider} 子类的
 * {@code subscribeChange} 返回 null，实例容器内 {@code @r-cfg} 式响应式 bean 装配会触发空订阅清理——
 * 本类经本地监听表 + 委托返回非 null cleanup，杜绝该陷阱）。实例键（合并视图 = 定义默认 + 实例配置，
 * 实例覆盖定义）优先于全局值；实例键变更时经本地监听表触发变更通知（P2-C 热应用依赖此路径）。
 *
 * <p>合并视图由实例持有方（{@link PluginInstanceImpl}）维护，经 {@link #setMergedView} /
 * {@link #updateMergedView} 下发；本类只做读取解析与变更通知，不写全局配置
 * （{@link #assignConfigValue} 只写入实例视图，绝不落全局 provider）。
 */
public class InstanceConfigProvider implements IConfigProvider {
    static final Logger LOG = LoggerFactory.getLogger(InstanceConfigProvider.class);

    private final IConfigProvider globalProvider;

    /**
     * 实例合并视图（定义默认 + 实例配置，实例覆盖定义）。所有读写均在锁内进行。
     */
    private final Map<String, Object> mergedView = new LinkedHashMap<>();

    /**
     * 实例键变更监听（pattern → listeners）。
     */
    private final Map<String, CopyOnWriteArrayList<IConfigChangeListener>> listeners = new ConcurrentHashMap<>();

    public InstanceConfigProvider(IConfigProvider globalProvider) {
        this.globalProvider = globalProvider;
    }

    /**
     * 全量设置合并视图（首次激活时调用；旧视图为空的 diff 通知为空操作）。
     */
    public void setMergedView(Map<String, Object> view) {
        applyMergedView(view);
    }

    /**
     * 热应用合并视图（P2-C ACTIVATED 实例）：diff 出变更键，更新值并触发变更通知（非仅改快照）。
     */
    public void updateMergedView(Map<String, Object> view) {
        applyMergedView(view);
    }

    private void applyMergedView(Map<String, Object> view) {
        Map<String, Object> oldValues = new LinkedHashMap<>();
        synchronized (mergedView) {
            for (Map.Entry<String, Object> entry : view.entrySet()) {
                Object old = mergedView.get(entry.getKey());
                if (!Objects.equals(old, entry.getValue())) {
                    oldValues.put(entry.getKey(), old);
                }
            }
            for (String key : mergedView.keySet()) {
                if (!view.containsKey(key)) {
                    oldValues.put(key, mergedView.get(key));
                }
            }
            mergedView.clear();
            mergedView.putAll(view);
        }
        if (!oldValues.isEmpty()) {
            for (Map.Entry<String, Object> entry : oldValues.entrySet()) {
                notifyListeners(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * 当前合并视图快照（供 instance.getConfig() 使用，任何态可读）。
     */
    public Map<String, Object> getMergedViewSnapshot() {
        synchronized (mergedView) {
            return new LinkedHashMap<>(mergedView);
        }
    }

    public boolean containsKey(String varName) {
        synchronized (mergedView) {
            return mergedView.containsKey(varName);
        }
    }

    private void notifyListeners(String varName, Object oldValue) {
        List<IConfigChangeListener> matched = new ArrayList<>();
        for (Map.Entry<String, CopyOnWriteArrayList<IConfigChangeListener>> entry : listeners.entrySet()) {
            if (patternMatches(entry.getKey(), varName)) {
                matched.addAll(entry.getValue());
            }
        }
        for (IConfigChangeListener listener : matched) {
            try {
                listener.onConfigChange(this, Map.of(varName, oldValue));
            } catch (Exception e) {
                LOG.error("nop.plugin.config-listener-fail:var={}", varName, e);
            }
        }
    }

    static boolean patternMatches(String pattern, String varName) {
        if (pattern.equals(varName))
            return true;
        if (pattern.endsWith(".*")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            if (varName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void setInstanceValue(String varName, Object value) {
        Map<String, Object> oldValues = new LinkedHashMap<>();
        synchronized (mergedView) {
            oldValues.put(varName, mergedView.get(varName));
            if (value == null) {
                mergedView.remove(varName);
            } else {
                mergedView.put(varName, value);
            }
        }
        notifyListeners(varName, oldValues.get(varName));
    }

    @Override
    public Map<String, DefaultConfigReference<?>> getConfigReferences() {
        return globalProvider.getConfigReferences();
    }

    @Override
    public Map<String, StaticValue<?>> getStaticConfigValues() {
        return globalProvider.getStaticConfigValues();
    }

    @Override
    public <T> IConfigReference<T> getConfigReference(String varName, Class<T> clazz, T defaultValue,
                                                      SourceLocation loc) {
        synchronized (mergedView) {
            if (mergedView.containsKey(varName)) {
                Object value = mergedView.get(varName);
                Class<T> valueType = value == null ? clazz : (Class<T>) value.getClass();
                IConfigReference<T> ref = new DefaultConfigReference<>(loc, varName, valueType, defaultValue,
                        StaticValue.build(varName, valueType, value));
                if (ref.getValueType() != clazz) {
                    ref = new CastTypeConfigReference<>(ref, clazz);
                }
                if (defaultValue != null) {
                    ref = DefaultConfigReference.makeDefault(ref, defaultValue);
                }
                return ref;
            }
        }
        return globalProvider.getConfigReference(varName, clazz, defaultValue, loc);
    }

    @Override
    public <T> IConfigReference<T> getStaticConfigReference(String varName, Class<T> clazz, T defaultValue,
                                                            SourceLocation loc) {
        return globalProvider.getStaticConfigReference(varName, clazz, defaultValue, loc);
    }

    @Override
    public void reset() {
        globalProvider.reset();
    }

    @Override
    public <T> void updateConfigValue(IConfigReference<T> ref, T value) {
        if (containsKey(ref.getName())) {
            setInstanceValue(ref.getName(), value);
        } else {
            globalProvider.updateConfigValue(ref, value);
        }
    }

    @Override
    public void assignConfigValue(String name, Object value) {
        // 只写实例视图，绝不写全局配置（不调 AppConfig.assignConfigValue）
        setInstanceValue(name, value);
    }

    @Override
    public Map<String, Object> getConfigValueForPrefix(String prefix) {
        Map<String, Object> map = globalProvider.getConfigValueForPrefix(prefix);
        synchronized (mergedView) {
            for (Map.Entry<String, Object> entry : mergedView.entrySet()) {
                String key = entry.getKey();
                if (ApiStringHelper.startsWithConfigPrefix(key, prefix)) {
                    setIn(map, key.substring(prefix.length() + 1), entry.getValue());
                }
            }
        }
        return map;
    }

    private void setIn(Map<String, Object> map, String key, Object value) {
        int pos = key.indexOf('.');
        if (pos < 0) {
            map.put(key, value);
        } else {
            String prefix = key.substring(0, pos);
            String postfix = key.substring(pos + 1);
            Object sub = map.get(prefix);
            if (sub instanceof Map) {
                setIn((Map<String, Object>) sub, postfix, value);
            } else {
                Map<String, Object> subMap = new LinkedHashMap<>();
                map.put(prefix, subMap);
                setIn(subMap, postfix, value);
            }
        }
    }

    @Override
    public Runnable subscribeChange(String pattern, IConfigChangeListener listener) {
        CopyOnWriteArrayList<IConfigChangeListener> list =
                listeners.computeIfAbsent(pattern, k -> new CopyOnWriteArrayList<>());
        list.add(listener);
        Runnable globalCleanup = globalProvider.subscribeChange(pattern, listener);
        return () -> {
            CopyOnWriteArrayList<IConfigChangeListener> l = listeners.get(pattern);
            if (l != null) {
                l.remove(listener);
                if (l.isEmpty()) {
                    listeners.remove(pattern);
                }
            }
            if (globalCleanup != null) {
                globalCleanup.run();
            }
        };
    }

    @Override
    public <T> T getConfigValue(String varName, T defaultValue) {
        synchronized (mergedView) {
            if (mergedView.containsKey(varName)) {
                Object value = mergedView.get(varName);
                if (value == null)
                    return defaultValue;
                if (defaultValue != null) {
                    return (T) StaticValue.castValue(varName, (Class<T>) defaultValue.getClass(), value);
                }
                return (T) value;
            }
        }
        return globalProvider.getConfigValue(varName, defaultValue);
    }
}
