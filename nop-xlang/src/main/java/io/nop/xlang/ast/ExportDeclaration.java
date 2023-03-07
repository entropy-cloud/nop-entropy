/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.ast;

import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.ast._gen._ExportDeclaration;

public class ExportDeclaration extends _ExportDeclaration {
    public static ExportDeclaration valueOf(SourceLocation loc, Declaration decl) {
        ExportDeclaration ret = new ExportDeclaration();
        ret.setLocation(loc);
        ret.setDeclaration(decl);
        return ret;
    }
}