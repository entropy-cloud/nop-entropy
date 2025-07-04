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
    private PsiClass[] clazz;

    public ImportDeclarationNode(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public @NotNull Map<String, XLangVarDecl> getVars() {
        ImportSourceNode imp = findChildByClass(ImportSourceNode.class);
        if (imp == null) {
            return Map.of();
        }

        if (clazz == null) {
            String classFQN = imp.getFullyQualifiedName();

            clazz = new PsiClass[] {
                    PsiClassHelper.findClass(getProject(), classFQN)
            };
        }
        if (clazz[0] == null) {
            return Map.of();
        }

        String varName = imp.getLastQualifiedName();
        XLangVarDecl varDecl = new XLangVarDecl(clazz[0], clazz[0]);

        return Map.of(varName, varDecl);
    }
}
