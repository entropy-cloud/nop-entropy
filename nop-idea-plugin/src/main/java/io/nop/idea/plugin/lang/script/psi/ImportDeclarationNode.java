package io.nop.idea.plugin.lang.script.psi;

import java.util.Map;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.PsiTreeUtil;
import io.nop.idea.plugin.utils.PsiClassHelper;
import org.jetbrains.annotations.NotNull;

/**
 * <code>import xxx;</code> 节点（含结束符）
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-29
 */
public class ImportDeclarationNode extends RuleSpecNode {

    public ImportDeclarationNode(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public @NotNull Map<String, VarDecl> getVarTypes() {
        /* 导入语句：import java.lang.Number;
        ImportDeclarationNode(importAsDeclaration)
          PsiElement('import')('import')
          ImportSourceNode(ast_importSource)
            RuleSpecNode(qualifiedName)
              RuleSpecNode(qualifiedName_name_)
                IdentifierNode(identifier)
                  PsiElement(Identifier)('java')
              PsiElement('.')('.')
              RuleSpecNode(qualifiedName)
                RuleSpecNode(qualifiedName_name_)
                  IdentifierNode(identifier)
                    PsiElement(Identifier)('lang')
                PsiElement('.')('.')
                RuleSpecNode(qualifiedName)
                  RuleSpecNode(qualifiedName_name_)
                    IdentifierNode(identifier)
                      PsiElement(Identifier)('Number')
          RuleSpecNode(eos__)
            PsiElement(';')(';')
        */
        ImportSourceNode imp = PsiTreeUtil.findChildOfType(this, ImportSourceNode.class);

        String clsName = imp.getClassName();
        String clsFqn = imp.getClassFullyQualifiedName();
        PsiClass cls = PsiClassHelper.findClass(getProject(), clsFqn);

        return Map.of(clsName, new VarDecl(cls, cls));
    }
}
