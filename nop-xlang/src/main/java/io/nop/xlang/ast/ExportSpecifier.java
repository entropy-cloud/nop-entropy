/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.ast;

import io.nop.api.core.util.Guard;
import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.ast._gen._ExportSpecifier;

public class ExportSpecifier extends _ExportSpecifier {
    public static ExportSpecifier valueOf(SourceLocation loc, Identifier local, Identifier exported) {
        Guard.notNull(local, "local is null");
        ExportSpecifier node = new ExportSpecifier();
        node.setLocation(loc);
        node.setLocal(local);
        node.setExported(exported);
        return node;
    }
}