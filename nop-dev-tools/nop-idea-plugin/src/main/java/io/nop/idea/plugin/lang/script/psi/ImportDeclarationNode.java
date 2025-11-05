/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.script.psi;

import java.util.Map;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClass;
import io.nop.idea.plugin.lang.XLangVarDecl;
import io.nop.idea.plugin.utils.PsiClassHelper;
import org.jetbrains.annotations.NotNull;

/**
 * <code>import xxx;</code>：
 * <pre>
 * ImportDeclarationNode(importAsDeclaration)
 *   PsiElement('import')('import')
 *   ImportSourceNode(ast_importSource)
 *     RuleSpecNode(qualifiedName)
 *       RuleSpecNode(qualifiedName_name_)
 *         IdentifierNode(identifier)
 *           PsiElement(Identifier)('java')
 *       PsiElement('.')('.')
 *       RuleSpecNode(qualifiedName)
 *         RuleSpecNode(qualifiedName_name_)
 *           IdentifierNode(identifier)
 *             PsiElement(Identifier)('lang')
 *         PsiElement('.')('.')
 *         RuleSpecNode(qualifiedName)
 *           RuleSpecNode(qualifiedName_name_)
 *             IdentifierNode(identifier)
 *               PsiElement(Identifier)('String')
 *   RuleSpecNode(eos__)
 *     PsiElement(';')(';')
 * </pre>
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-29
 */
public class ImportDeclarationNode extends RuleSpecNode {

    public ImportDeclarationNode(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public @NotNull Map<String, XLangVarDecl> getVars() {
        ImportSourceNode imp = findChildByClass(ImportSourceNode.class);

        QualifiedNameNode qualifiedName = imp != null ? imp.getQualifiedName() : null;
        if (qualifiedName == null) {
            return Map.of();
        }

        String classFQN = qualifiedName.getFullyName();
        PsiClass clazz = PsiClassHelper.findClass(this, classFQN);
        if (clazz == null) {
            return Map.of();
        }

        String varName = qualifiedName.getLastName();
        XLangVarDecl varDecl = new XLangVarDecl(clazz, clazz);

        return Map.of(varName, varDecl);
    }
}
