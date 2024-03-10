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
import io.nop.xlang.ast._gen._ImportSpecifier;

public class ImportSpecifier extends _ImportSpecifier {
    public static ImportSpecifier valueOf(SourceLocation loc, Identifier local, Identifier imported) {
        Guard.notNull(imported, "imported is null");
        ImportSpecifier node = new ImportSpecifier();
        node.setLocation(loc);
        node.setLocal(local);
        node.setImported(imported);
        return node;
    }
}