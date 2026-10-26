/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.config;

import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.util.StaticValue;

/**
 * 默认配置引用实现。
 *
 * <p>并发发布协议（plan 2507 裁定④ / C1-007，含 C1-017 族案）：location 与 provider
 * 收拢为不可变 holder {@link ConfigRefState}，本类仅持单个 volatile 引用。
 * {@link #updateValue(SourceLocation, IConfigValue)} 以「构造新 holder + 单 volatile 写」
 * 一次发布新代——读侧任意时刻经单次读拿到的 location 与 provider 恒为同代，
 * 两步写撕裂窗结构性不存在；provider 变更经 volatile 发布边对读者立即可见。
 *
 * <p>defaultValue 不入 holder（W7 setConfigModel 为启动期 main 线程唯一写点，
 * 运行期只读；{@code get()} 在 assigned 为 null 时回落 defaultValue 的读边与 holder
 * 正交），仍由 {@link AbstractConfigReference#setDefaultValue} 独立承载。
 *
 * <p>location 的发布唯一入口为 {@link #updateValue}：本类覆写
 * {@link #getLocation()} 直接读 holder，{@code setLocation} 不再影响本类读路径。
 * 零接口签名变更（IConfigReference/IConfigProvider 契约不变）。
 */
public final class DefaultConfigReference<T> extends AbstractConfigReference<T> {

    /**
     * 不可变发布快照：location 与 provider 恒同代（final 字段安全发布）。
     * 仅供本类与同包发布原子性回归测试观测使用，不属于 SPI 公共面。
     */
    static final class ConfigRefState<T> {
        final SourceLocation location;
        final IConfigValue<T> provider;

        ConfigRefState(SourceLocation location, IConfigValue<T> provider) {
            this.location = location;
            this.provider = provider;
        }
    }

    private volatile ConfigRefState<T> state;

    public DefaultConfigReference(SourceLocation loc, String name, Class<T> valueType, T defaultValue, IConfigValue<T> provider) {
        super(loc, name, valueType, defaultValue);
        this.state = new ConfigRefState<>(loc, Guard.notNull(provider, "provider"));
    }

    public DefaultConfigReference(SourceLocation loc, String name, Class<T> valueType, T defaultValue) {
        super(loc, name, valueType, defaultValue);
        this.state = new ConfigRefState<>(loc, StaticValue.valueOf(defaultValue));
    }

    public static <T> DefaultConfigReference<T> staticRef(String name, T value) {
        Class valueType = value == null ? Object.class : value.getClass();
        return new DefaultConfigReference<>(null, name, valueType, value);
    }

    public static <T> DefaultConfigReference<T> makeDefault(IConfigReference<T> ref, T defaultValue) {
        return new DefaultConfigReference<>(ref.getLocation(), ref.getName(), ref.getValueType(), defaultValue, ref);
    }

    /**
     * 内部观测缝：单次 volatile 读返回当前发布快照，供同包并发回归测试断言
     * location/provider 代际一致性。非 SPI 公共面。
     */
    ConfigRefState<T> stateSnapshot() {
        return state;
    }

    public IConfigValue<T> getProvider() {
        return state.provider;
    }

    @Override
    public SourceLocation getLocation() {
        return state.location;
    }

    @Override
    public boolean isDynamic() {
        return state.provider.isDynamic();
    }

    public T getAssignedValue() {
        return state.provider.get();
    }

    public void updateValue(SourceLocation loc, IConfigValue<T> provider) {
        // 单 volatile 写发布：location 与 provider 一次换代，无中间态（C1-007 修复）
        this.state = new ConfigRefState<>(loc, Guard.notNull(provider, "provider"));
    }
}
