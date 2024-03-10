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
import io.nop.xlang.ast._gen._ImportAsDeclaration;
import io.nop.xlang.xpl.xlib.XplLibHelper;

public class ImportAsDeclaration extends _ImportAsDeclaration {
    public static ImportAsDeclaration valueOf(SourceLocation loc, XLangASTNode source, Identifier local) {
        Guard.notNull(source, "source");
        ImportAsDeclaration node = new ImportAsDeclaration();
        node.setLocation(loc);
        node.setSource(source);
        node.setLocal(local);
        return node;
    }

    public boolean isImportClass() {
        return getSource() instanceof QualifiedName;
    }

    public boolean isImportLib() {
        return getSource() instanceof Literal;
    }

    public String getImportClassName() {
        return ((QualifiedName) getSource()).getFullName();
    }

    public String getImportLibPath() {
        return ((Literal) getSource()).getStringValue();
    }

    public Identifier makeLocal() {
        Identifier local = getLocal();
        if (local != null)
            return local;

        XLangASTNode source = getSource();
        String name;
        if (source instanceof QualifiedName) {
            name = ((QualifiedName) source).getSimpleName();
        } else {
            name = XplLibHelper.getNamespaceFromLibPath(((Literal) source).getStringValue());
        }
        local = Identifier.valueOf(source.getLocation(), name);
        setLocal(local);
        return local;
    }
}