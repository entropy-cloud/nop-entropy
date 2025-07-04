package io.nop.idea.plugin.lang.script.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import io.nop.idea.plugin.lang.XLangVarDecl;
import io.nop.idea.plugin.lang.script.reference.ClassReference;
import io.nop.idea.plugin.lang.script.reference.VariableDeclarationReference;
import org.jetbrains.annotations.NotNull;

/**
 * {@link Identifier} 节点
 * <p/>
 * 该节点 AST 树为：
 * <pre>
 * IdentifierNode(identifier)
 *   PsiElement(Identifier)('b')
 * </pre>
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-02
 */
public class IdentifierNode extends RuleSpecNode {
    private XLangVarDecl[] varDecl;

    public IdentifierNode(@NotNull ASTNode node) {
        super(node);
    }

    public PsiReference @NotNull [] createReferences(PsiElement source, TextRange textRange) {
        XLangVarDecl varDecl = getVarDecl();
        PsiElement element = varDecl.element();

        if (element instanceof IdentifierNode id) {
            VariableDeclarationReference ref = new VariableDeclarationReference(source, id, textRange);

            return new PsiReference[] { ref };
        } else if (element instanceof PsiClass clazz) {
            ClassReference ref = new ClassReference(source, clazz, textRange);

            return new PsiReference[] { ref };
        }
        return PsiReference.EMPTY_ARRAY;
    }

    /**
     * 获取变量的数据类型
     * <p/>
     * 若标识符为函数名，则返回函数的返回值类型
     */
    public PsiClass getDataType() {
        XLangVarDecl varDecl = getVarDecl();

        return varDecl != null ? varDecl.type() : null;
    }

    public XLangVarDecl getVarDecl() {
        if (varDecl == null) {
            String varName = getText();

            varDecl = new XLangVarDecl[] {
                    findVisibleVar(varName)
            };
        }
        return varDecl[0];
    }
}
