/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.java.backend;

import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.xlang.backend.IEvalStaticBinding;

/**
 * 生成类绑定供给方契约：resourcePath + Executable 树 → 生成类绑定。
 *
 * <p>生产实现（生成类加载、树指纹一致性校验、解释器兜底绑定）归 java 生成类加载集成（I10）；
 * 本契约先行承载接入缝，测试域以合成绑定承载。
 */
@FunctionalInterface
public interface IEvalStaticBindingBinder {

    /**
     * 查找生成类绑定；返回 null 表示绑定缺失（清单内资源应有而缺失，由裁决入口记降级观测）。
     */
    IEvalStaticBinding findStaticBinding(String resourcePath, IExecutableExpression tree);
}
