/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.wf.core.biz;

import io.nop.core.context.IServiceContext;

/**
 * 可审批实体的 mixin 接口。提供 5 个标准审批方法但不绑定 ICrudBiz 或 IOrmEntity。
 * <p>
 * 这是编译占位接口——所有方法的 Java default 实现仅为满足 codegen 生成的 BizModel 编译通过。
 * 运行时 dispatch 入口唯一来自 {@code approval-support.xbiz} 的 action source（经 x:extends 合并注册），
 * 因为无 {@code @BizAction} 注解的 Java default 方法对 dispatch 层不可见。
 * <p>
 * default 方法体 fast-fail 抛异常，禁止直接 Java 调用。
 *
 * @param <T> 业务实体类型（无上界约束）
 */
public interface IApprovableBiz<T> {

    default T submitForApproval(String id, IServiceContext context) {
        throw new UnsupportedOperationException(
                "submitForApproval is provided by xbiz action source, not by Java implementation");
    }

    default T withdrawApproval(String id, IServiceContext context) {
        throw new UnsupportedOperationException(
                "withdrawApproval is provided by xbiz action source, not by Java implementation");
    }

    default T approve(String id, IServiceContext context) {
        throw new UnsupportedOperationException(
                "approve is provided by xbiz action source, not by Java implementation");
    }

    default T reject(String id, IServiceContext context) {
        throw new UnsupportedOperationException(
                "reject is provided by xbiz action source, not by Java implementation");
    }

    default T reverseApprove(String id, IServiceContext context) {
        throw new UnsupportedOperationException(
                "reverseApprove is provided by xbiz action source, not by Java implementation");
    }
}
