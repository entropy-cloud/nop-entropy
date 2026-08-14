package io.nop.plugin.manager.impl;

import io.nop.plugin.api.IPluginInstance;

import java.util.Set;

/**
 * 定义级 coeffect 评估器回引（W5 数据流缝裁定：manager 在 load 时注入本接口给
 * {@link VfsPluginDefinition}，门控与级联评估统一经该回引）——实现侧持有 manager 级状态
 * （其他定义的实例状态、全局配置），{@code VfsPluginDefinition} 自身无 manager 引用。
 *
 * <p>父链健康（父实例存在且 ACTIVATED）为 W6 扩展点：W6 将父链逻辑纳入实例激活条件，
 * 经本接口承载，不写死进 reconcile 引擎。
 */
public interface ICoeffectEvaluator {

    /**
     * requires 依赖集是否满足：每个依赖 pluginId（按定义 @name 解析）存在且至少一个实例 ACTIVATED。
     */
    boolean isRequiresSatisfied(Set<String> requires);

    /**
     * 定义级 if-property 是否满足：全局配置中 propName 对应值等于 expectedValue（宽松比较）。
     */
    boolean isGlobalConfigMatched(String propName, Object expectedValue);

    /**
     * 实例级 if-property 是否满足：实例合并视图优先、未命中回退全局配置
     * （与 {@link InstanceConfigProvider#getConfigValue} 合并视图语义一致）。
     */
    boolean isInstanceConfigMatched(IPluginInstance instance, String propName, Object expectedValue);
}
