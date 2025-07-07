package io.nop.idea.plugin.lang.script.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulator;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.util.IncorrectOperationException;
import io.nop.commons.util.StringHelper;
import io.nop.idea.plugin.lang.XLangVarDecl;
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
public class IdentifierNode extends RuleSpecNode implements PsiNamedElement {

    public IdentifierNode(@NotNull ASTNode node) {
        super(node);
    }

    /** Note: 只有返回非 <code>null</code> 值，才支持在 idea 中重命名该节点 */
    @Override
    public String getName() {
        return getText();
    }

    @Override
    public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
        ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(this);

        TextRange textRange = getTextRangeInParent().shiftLeft(getStartOffsetInParent());

        return manipulator.handleContentChange(this, textRange, name);
    }

    /** 获取变量的类型 */
    public PsiClass getVarType() {
        XLangVarDecl varDecl = getVarDecl();

        return varDecl != null ? varDecl.type() : null;
    }

    /** 获取变量的定义信息 */
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
