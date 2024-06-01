/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.config.impl;

import io.nop.api.core.ApiConfigs;
import io.nop.api.core.config.AbstractConfigProvider;
import io.nop.api.core.config.DefaultConfigReference;
import io.nop.api.core.config.IConfigChangeListener;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.config.IConfigValue;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.util.StaticValue;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.config.ConfigConstants;
import io.nop.config.enhancer.IConfigValueEnhancer;
import io.nop.config.model.ConfigModel;
import io.nop.config.model.ConfigModelLoader;
import io.nop.config.model.ConfigVarModel;
import io.nop.config.source.IConfigSource;
import io.nop.core.unittest.BaseTestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import static io.nop.config.ConfigErrors.ARG_CONFIG_NAME;
import static io.nop.config.ConfigErrors.ARG_DEFINE_TYPE;
import static io.nop.config.ConfigErrors.ARG_VALUE_TYPE;
import static io.nop.config.ConfigErrors.ERR_CONFIG_VALUE_TYPE_NOT_SAME_AS_DEFINED;

@SuppressWarnings({"unchecked", "rawtypes", "CastCanBeRemovedNarrowingVariableType"})
public class DefaultConfigProvider extends AbstractConfigProvider {
    static final Logger LOG = LoggerFactory.getLogger(DefaultConfigProvider.class);

    private final IConfigValueEnhancer configValueEnhancer;

    private IConfigSource configSource;
    private volatile Map<String, DefaultConfigReference<?>> configRefs; //NOSONAR
    private volatile Map<String, ValueWithLocation> configValues; //NOSONAR
    private ConfigModel configModel;

    private final ChangeSubscriptions subscriptions = new ChangeSubscriptions();

    public DefaultConfigProvider(IConfigSource configSource, IConfigValueEnhancer configValueEnhancer,
                                 Map<String, DefaultConfigReference<?>> refs, Map<String, StaticValue<?>> staticValues) {
        super(refs, staticValues);
        this.configSource = configSource;
        this.configValueEnhancer = configValueEnhancer;
        this.configRefs = (Map) refs;
        this.configValues = configSource.getConfigValues();
    }

    public DefaultConfigProvider(IConfigSource configSource, IConfigValueEnhancer configValueEnhancer) {
        this(configSource, configValueEnhancer, new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
    }

    @Override
    protected DefaultConfigReference<?> getConfigRef(String varName) {
        DefaultConfigReference<?> ref = usedRefs.get(varName);
        if (ref == null) {
            ref = configRefs.get(varName);
        }
        return ref;
    }

    public void loadConfigModel() {
        ConfigModel configModel = new ConfigModelLoader().loadConfigModel();
        if (configModel != null) {
            setConfigModel(configModel);
        }
    }

    public void setConfigModel(ConfigModel configModel) {
        this.configModel = configModel;

        if (configModel != null) {
            for (DefaultConfigReference ref : usedRefs.values()) {
                ConfigVarModel varModel = configModel.getVar(ref.getName());
                if (varModel != null) {
                    String varName = ref.getName();
                    if (varModel.getValueType() != ref.getValueType()) {
                        throw new NopException(ERR_CONFIG_VALUE_TYPE_NOT_SAME_AS_DEFINED)
                                .param(ARG_CONFIG_NAME, varName).param(ARG_VALUE_TYPE, ref.getValueType())
                                .param(ARG_DEFINE_TYPE, ref.getDefaultValue());
                    }
                    if (!Objects.equals(varModel.getDefaultValue(), ref.getDefaultValue())) {
                        LOG.warn(
                                "nop.config.var-model-default-value-is-different-from-used-ref:refDefault={},modelDefault={}",
                                ref.getDefaultValue(), varModel.getDefaultValue());
                        ref.setDefaultValue(varModel.getDefaultValue());
                    }
                }
            }
        }
    }

    public void changeConfigSource(IConfigSource configSource) {
        this.configSource = configSource;
        this.configValues = configSource.getConfigValues();
        this.configRefs = buildConfigRefs(configValues);
        updateRefs(this.configRefs);

        traceConfigVars();
    }

    private void updateRefs(Map<String, DefaultConfigReference<?>> refs) {
        for (Map.Entry<String, DefaultConfigReference<?>> entry : refs.entrySet()) {
            String name = entry.getKey();

            DefaultConfigReference used = usedRefs.get(name);
            if (used != null) {
                IConfigReference<?> var = refs.get(name);
                if (var == used)
                    continue;

                if (var == null) {
                    used.updateValue(null, StaticValue.nullValue());
                } else {
                    try {
                        used.updateValue(var.getLocation(), castType(var, used.getValueType()));
                    } catch (Exception e) {
                        LOG.error("nop.config.update-var-value-fail", e);
                    }
                }
            }
        }

        boolean testRunning = BaseTestCase.isTestRunning();
        // 如果强行设置了测试配置，则以测试配置为准
        if (testRunning) {
            Map<String, Object> testConfigs = BaseTestCase.getTestConfigs();
            testConfigs.forEach((name, value) -> {
                if (value == null)
                    return;
                Class clazz = value.getClass();
                DefaultConfigReference<StaticValue<?>> ref = makeConfigRef(null, name, clazz);
                try {
                    StaticValue staticValue = StaticValue.build(ref.getName(), ref.getValueType(), value);
                    ref.updateValue(ref.getLocation(), staticValue);
                } catch (Exception e) {
                    LOG.error("nop.config.update-var-value-fail", e);
                }
            });
        }
    }

    protected void traceConfigVars() {
        boolean debug = getConfigValue(ApiConfigs.CFG_DEBUG.getName(), false);
        boolean trace = getConfigValue(ConfigConstants.CFG_CONFIG_TRACE, debug);
        if (trace) {
            Map<String, IConfigReference> vars = new TreeMap<>(this.configRefs);
            vars.putAll(this.usedRefs);

            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, IConfigReference> entry : vars.entrySet()) {
                // 跳过properties中的某些配置变量
                if (entry.getKey().startsWith("java."))
                    continue;

                if (entry.getKey().indexOf('.') < 0)
                    continue;

                IConfigReference vl = entry.getValue();
                if (vl.getLocation() != null) {
                    sb.append("# ").append(vl.getLocation()).append('\n');
                }

                Object value;
                if (vl.isDynamic()) {
                    value = vl.getDefaultValue();
                    value = StringHelper.maskSecretVar(entry.getKey(), value);
                    sb.append(entry.getKey()).append('=').append("@dynamic:" + value).append("\n");
                } else {
                    value = vl.get();
                    value = StringHelper.maskSecretVar(entry.getKey(), value);
                    sb.append(entry.getKey()).append('=').append(ConvertHelper.toString(value, "")).append("\n");
                }
            }
            LOG.info("nop.config.vars=\n{}", sb);
        }
    }

    @Override
    protected <T> DefaultConfigReference<T> makeConfigRef(SourceLocation loc, String varName, Class<T> clazz) {
        return (DefaultConfigReference<T>) usedRefs.computeIfAbsent(varName, key -> {
            IConfigReference<?> defined = configRefs.get(varName);
            if (defined == null) {
                return createRef(loc, varName, clazz);
            } else {
                return buildFromDefined(varName, defined);
            }
        });
    }

    private DefaultConfigReference<?> buildFromDefined(String varName, IConfigReference<?> ref) {
        Class valueType = ref.getValueType();
        Object defaultValue = ref.getDefaultValue();
        ConfigVarModel varModel = configModel == null ? null : configModel.getVar(varName);
        if (varModel != null) {
            valueType = varModel.getValueType();
            defaultValue = varModel.getDefaultValue();
        }

        return new DefaultConfigReference<Object>(ref.getLocation(), varName, valueType, defaultValue,
                castType(ref, valueType));
    }

    private <T> DefaultConfigReference<T> createRef(SourceLocation loc, String varName, Class<T> clazz) {
        ConfigVarModel varModel = configModel == null ? null : configModel.getVar(varName);
        T defaultValue = null;
        if (varModel != null) {
            clazz = (Class<T>) varModel.getValueType();
            defaultValue = (T) varModel.getDefaultValue();
        }
        DefaultConfigReference<T> valueRef = new DefaultConfigReference<>(loc, varName, clazz, defaultValue,
                StaticValue.nullValue());
        return valueRef;
    }

    @Override
    public Runnable subscribeChange(String pattern, IConfigChangeListener listener) {
        return subscriptions.subscribe(pattern, listener);
    }

    /**
     * 检查ConfigSource是否发生变化，并触发注册的监听器
     */
    public void applyChange() {
        Map<String, ValueWithLocation> lastVars = this.configValues;

        // 当存在灰度配置时，getConfigVars返回的IConfigReference.getName()可能与entry.getKey()不同
        Map<String, ValueWithLocation> vars = configSource.getConfigValues();

        this.configValues = vars;

        Map<String, Object> changed = new HashMap<>();

        // 新增或者更新的配置项
        for (Map.Entry<String, ValueWithLocation> entry : vars.entrySet()) {
            String name = entry.getKey();
            ValueWithLocation var = entry.getValue();

            ValueWithLocation lastVar = lastVars.get(name);

            Object oldValue = lastVar == null ? null : lastVar.getValue();

            if (Objects.equals(var.getValue(), oldValue))
                continue;

            changed.put(name, oldValue);
        }

        // 删除的配置项
        for (Map.Entry<String, ValueWithLocation> entry : lastVars.entrySet()) {
            String name = entry.getKey();
            if (vars.containsKey(name))
                continue;

            changed.put(name, entry.getValue().getValue());
        }

        Map<String, DefaultConfigReference<?>> refs = buildConfigRefs(vars);
        this.configRefs = refs;

        // 更新配置值到当前正在使用ConfigReference对象
        applyChangeToUsed(changed, refs);
        subscriptions.trigger(this, changed);
    }

    private void applyChangeToUsed(Map<String, Object> changed, Map<String, DefaultConfigReference<?>> refs) {

        for (Map.Entry<String, Object> entry : changed.entrySet()) {
            String name = entry.getKey();
            IConfigReference<?> var = refs.get(name);
            DefaultConfigReference used = usedRefs.get(name);
            if (used != null) {
                if (var == null) {
                    used.updateValue(null, StaticValue.nullValue());
                } else {
                    try {
                        used.updateValue(var.getLocation(), castType(var, used.getValueType()));
                    } catch (Exception e) {
                        LOG.error("nop.config.update-var-value-fail", e);
                    }
                }
            }
        }
    }

    <T> IConfigValue<T> castType(IConfigReference<?> ref, Class<T> targetValueType) {
        if (ref.getValueType() == targetValueType || targetValueType.isAssignableFrom(ref.getValueType()))
            return (IConfigValue<T>) ref.getProvider();

        IConfigValue<?> provider = ref.getProvider();
        if (provider instanceof StaticValue)
            return ((StaticValue) provider).cast(ref.getName(), targetValueType);

        return new IConfigValue<T>() {
            @Override
            public boolean isDynamic() {
                return provider.isDynamic();
            }

            @Override
            public T get() {
                return StaticValue.castValue(ref.getName(), targetValueType, provider.get());
            }
        };
    }

    Map<String, DefaultConfigReference<?>> buildConfigRefs(Map<String, ValueWithLocation> vars) {
        ConfigModel model = this.configModel;

        Map<String, DefaultConfigReference<?>> ret = new HashMap<>();

        for (Map.Entry<String, ValueWithLocation> entry : vars.entrySet()) {
            String name = entry.getKey();
            ConfigVarModel varModel = model == null ? null : model.getVar(name);
            Object defaultValue = null;
            Class targetClass = Object.class;
            if (varModel != null) {
                targetClass = varModel.getValueType();
                defaultValue = varModel.getDefaultValue();
            }

            SourceLocation loc = entry.getValue().getLocation();
            Object value = entry.getValue().getValue();

            try {
                IConfigValue configValue = configValueEnhancer.enhance(value, targetClass);
                DefaultConfigReference<Object> ref = new DefaultConfigReference<>(loc, name, targetClass, defaultValue,
                        configValue);
                ret.put(name, ref);
            } catch (Exception e) {
                LOG.error("nop.err.config.enhance-config-value-fail:name={},value={},targetClass={}", name, value,
                        targetClass, e);
            }
        }
        return ret;
    }
}