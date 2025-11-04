/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpl.impl;

import io.nop.xlang.api.IXLangProvider;
import io.nop.xlang.xpl.IXplCompiler;

public class DefaultXLangProvider implements IXLangProvider {
    public DefaultXLangProvider() {

    }

    @Override
    public IXplCompiler newXplCompiler() {
        return new XplCompiler();
    }
}
