package io.nop.idea.plugin.lang.script.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClass;
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
public class IdentifierNode extends RuleSpecNode {

    public IdentifierNode(@NotNull ASTNode node) {
        super(node);
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
