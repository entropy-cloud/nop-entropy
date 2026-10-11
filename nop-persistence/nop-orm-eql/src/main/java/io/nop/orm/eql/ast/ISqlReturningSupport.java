/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical_entropy
 * Gitee:  https://gitee.com/nop-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.eql.ast;

import io.nop.api.core.util.ISourceLocationGetter;

import java.util.List;

/**
 * Plan 2380：带 {@code returning} 子句的写语句。
 *
 * <p>UPDATE / INSERT / DELETE 三者的 returning 语义完全同构（AST 上是同名同型的
 * {@code returnAll} + {@code returnProjections}），因此门控、{@code *} 展开、投影类型解析
 * 与打印只需要一条共享路径——本接口就是那条路径的类型载体。
 *
 * <p>方法体由 codegen 生成的 {@code _SqlXxx} 基类提供，三个手写 wrapper 直接继承满足，
 * 本接口不引入任何字段。
 *
 * <p>⚠️ {@code SqlUpsert} <b>不</b>实现本接口：v1 的 upsert 不带 returning 子句。
 */
public interface ISqlReturningSupport extends ISourceLocationGetter {

    boolean getReturnAll();

    void setReturnAll(boolean returnAll);

    List<SqlProjection> getReturnProjections();

    void setReturnProjections(List<SqlProjection> returnProjections);
}
