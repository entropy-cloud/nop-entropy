/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.exec;

import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.CallExpression;
import io.nop.xlang.ast.Expression;

/**
 * 宏函数在编译期执行，具有固定的参数形式。可以对抽象语法树进行变换。
 */
public interface IMacroFunction {
    Expression call(IXLangCompileScope scope, CallExpression expr);
}
