/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.model;

import io.nop.api.core.exceptions.NopException;

/**
 * ORM 模型扩展校验器：对初始化完成的模型做语义级扩展校验（如可检索加密声明、分片绑定等）。
 *
 * <p>扩展校验不属于模型内核初始化（{@link OrmModel#init()} 的内置检查只保留通用结构约束），
 * 通过 {@link OrmModelValidatorRegistry} 注册后由模型初始化末尾统一驱动。注册途径：
 * <ul>
 *   <li>META-INF/services 声明（跨模块自动发现，见 OrmModelValidatorRegistry 的 ServiceLoader 懒加载）</li>
 *   <li>{@link OrmModelValidatorRegistry#register(IOrmModelValidator)} 显式注册（IoC bean / 测试）</li>
 * </ul>
 * 校验失败抛出 {@link NopException}（携带 source 定位与上下文参数），由模型加载路径向外传播——
 * 启动即失败，不推迟到首次查询。
 */
public interface IOrmModelValidator {
    /**
     * @param model 已完成内核初始化的模型（实体、列、关系等结构已就绪）
     */
    void validate(OrmModel model);
}
