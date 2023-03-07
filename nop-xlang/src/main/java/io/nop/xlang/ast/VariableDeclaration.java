/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.ast;

import io.nop.api.core.util.SourceLocation;
import io.nop.xlang.ast._gen._VariableDeclaration;
import io.nop.xlang.ast.definition.LocalVarDeclaration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VariableDeclaration extends _VariableDeclaration {
    public static VariableDeclaration valueOf(SourceLocation loc, VariableKind kind, XLangASTNode id,
                                              NamedTypeNode type, Expression init) {
        VariableDeclaration node = new VariableDeclaration();
        node.setLocation(loc);

        VariableDeclarator decl = VariableDeclarator.valueOf(loc, id, type, init);
        node.setKind(kind);
        node.setDeclarators(Arrays.asList(decl));
        return node;
    }

    public static VariableDeclaration valueOf(SourceLocation loc, VariableKind kind,
                                              List<VariableDeclarator> declarations) {
        VariableDeclaration node = new VariableDeclaration();
        node.setLocation(loc);
        node.setKind(kind);
        node.setDeclarators(declarations);
        return node;
    }

    public List<LocalVarDeclaration> getIdentifiers() {
        List<LocalVarDeclaration> ret = new ArrayList<>();
        for (VariableDeclarator declarator : getDeclarators()) {
            XLangASTNode id = declarator.getId();
            if (id.getASTKind() == XLangASTKind.Identifier) {
                ret.add(((Identifier) id).getVarDeclaration());
            } else if (id.getASTKind() == XLangASTKind.ObjectBinding) {
                ObjectBinding binding = (ObjectBinding) id;
                List<PropertyBinding> props = binding.getProperties();
                for (PropertyBinding prop : props) {
                    Identifier propName = prop.getIdentifier();
                    ret.add(propName.getVarDeclaration());
                }
            } else if (id.getASTKind() == XLangASTKind.ArrayBinding) {
                ArrayBinding binding = (ArrayBinding) id;
                List<ArrayElementBinding> elms = binding.getElements();
                for (ArrayElementBinding elm : elms) {
                    Identifier elmName = elm.getIdentifier();
                    ret.add(elmName.getVarDeclaration());
                }
            }
        }
        return ret;
    }
}