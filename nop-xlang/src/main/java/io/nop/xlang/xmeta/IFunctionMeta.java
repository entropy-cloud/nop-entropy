/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xmeta;

import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.commons.collections.IKeyedElement;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

public interface IFunctionMeta extends IKeyedElement, ISourceLocationGetter {
    default String key() {
        return getName();
    }

    String getName();

    String getDisplayName();

    String getDescription();

    IGenericType getReturnType();

    ISchema getReturnSchema();

    List<? extends IFunctionArgMeta> getArgs();

    default List<String> getArgNames() {
        return getArgs().stream().map(IFunctionArgMeta::getName).collect(Collectors.toList());
    }

    default boolean isReturnVoid() {
        return getReturnType() == PredefinedGenericTypes.VOID_TYPE;
    }

    /**
     * 异步函数总是返回CompletionStage类型
     */
    default boolean isAsync() {
        return getReturnType().isAssignableTo(CompletionStage.class);
    }

    default int getArgCount() {
        return getArgs().size();
    }
}