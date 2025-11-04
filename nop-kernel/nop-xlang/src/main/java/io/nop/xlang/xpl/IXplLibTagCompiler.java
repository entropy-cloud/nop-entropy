/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpl;

import io.nop.core.reflect.IFunctionModel;
import io.nop.xlang.ast.XLangOutputMode;

public interface IXplLibTagCompiler extends IXplTagCompiler {
    IFunctionModel getFunctionModel(XLangOutputMode outputMode);
}