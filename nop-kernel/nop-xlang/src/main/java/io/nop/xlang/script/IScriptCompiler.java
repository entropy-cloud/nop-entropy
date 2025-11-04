/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.script;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.reflect.IFunctionArgument;
import io.nop.core.type.IGenericType;
import io.nop.xlang.api.IXLangCompileScope;

import java.util.List;

/**
 * 外部脚本引擎，例如groovy等
 */
public interface IScriptCompiler {
    IEvalFunction compile(SourceLocation loc, String text,
                          List<? extends IFunctionArgument> args, IGenericType returnType,
                          IXLangCompileScope scope);
}