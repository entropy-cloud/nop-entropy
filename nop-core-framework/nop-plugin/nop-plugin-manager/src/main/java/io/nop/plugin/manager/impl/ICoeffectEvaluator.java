package io.nop.plugin.manager.impl;

import java.util.Set;

/**
 * 插件级 coeffect 评估器回引（数据流缝裁定：manager 在 load 时注入本接口给
 * {@link VfsPluginDefinition}，门控评估统一经该回引）——实现侧持有 manager 级状态
 * （其他定义的激活状态、全局配置），{@code VfsPluginDefinition} 自身无 manager 引用。
 */
public interface ICoeffectEvaluator {

    /**
     * requires 依赖集是否满足：每个依赖名（按定义 @name 解析）对应的 plugin 存在且
     * 处于 ACTIVATED（定义级激活态判定）。
     */
    boolean isRequiresSatisfied(Set<String> requires);

    /**
     * 定义级 if-property 是否满足：全局配置中 propName 对应值等于 expectedValue（宽松比较）。
     */
    boolean isGlobalConfigMatched(String propName, Object expectedValue);
}
