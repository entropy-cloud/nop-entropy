/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.backend;

import io.nop.core.lang.eval.IExecutableExpression;

/**
 * 静态生成物后端契约（java 后端形态）：扫描清单成员资格判定 + 生成类绑定查找。
 *
 * <p>扫描清单是构建期扫描任务的独立产物（resourcePath should-set，生产产物归构建集成阶段）；
 * 本契约提供清单成员资格的可查询接口与绑定查找。生成类加载与树指纹一致性校验的生产实现归
 * 后续 java 生成类加载集成（I10），本契约先行承载绑定接入缝。
 */
public interface IEvalStaticBackend extends IEvalExecutionBackend {

    /**
     * 扫描清单成员资格判定（静态路径判定测试）：resourcePath 是否属于构建期扫描清单。
     * 清单为空（缺省）时恒返回 false——所有资源走动态路径。
     */
    boolean isStaticCandidate(String resourcePath);

    /**
     * 查找静态资源的生成类绑定。清单内资源必有对应生成类——返回 null 即"应有而缺失"，
     * 由裁决入口记降级观测事件并回退解释器。
     *
     * @param resourcePath 扫描清单成员资源路径
     * @param tree         该资源的 Executable 树（供一致性校验用）
     * @return 生成类绑定；不可用时返回 null
     */
    IEvalStaticBinding findStaticBinding(String resourcePath, IExecutableExpression tree);
}
