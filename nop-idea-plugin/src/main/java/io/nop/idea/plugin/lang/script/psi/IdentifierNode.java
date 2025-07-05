package io.nop.idea.plugin.lang.script.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import io.nop.commons.util.StringHelper;
import io.nop.idea.plugin.lang.XLangVarDecl;
import io.nop.idea.plugin.lang.script.reference.PsiClassReference;
import io.nop.idea.plugin.lang.script.reference.VariableDeclarationReference;
import io.nop.idea.plugin.utils.PsiClassHelper;
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
            PsiClassReference ref = new PsiClassReference(source, clazz, textRange);

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
        String varName = getText();
        XLangVarDecl decl = findVisibleVar(varName);

        if (decl == null //
            && varName.indexOf('.') < 0 //
            && varName.indexOf('$') < 0 //
            && StringHelper.isValidClassName(varName) //
        ) {
            // Note: java.lang 中的类不需要显式导入
            PsiClass clazz = PsiClassHelper.findClass(getProject(), "java.lang." + varName);
            if (clazz != null) {
                decl = new XLangVarDecl(clazz, clazz);
            }
        }

        return decl;
    }
}
