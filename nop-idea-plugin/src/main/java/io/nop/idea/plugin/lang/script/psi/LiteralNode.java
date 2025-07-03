package io.nop.idea.plugin.lang.script.psi;

import java.util.regex.Pattern;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import io.nop.idea.plugin.utils.PsiClassHelper;
import io.nop.xlang.parse.antlr.XLangLexer;
import org.antlr.intellij.adaptor.lexer.TokenIElementType;
import org.jetbrains.annotations.NotNull;

/**
 * 字面量节点
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-02
 */
public class LiteralNode extends RuleSpecNode {

    public LiteralNode(@NotNull ASTNode node) {
        super(node);
    }

    /** 获取字面量的数据类型 */
    public PsiClass getDataType() {
        LeafPsiElement target = (LeafPsiElement) PsiTreeUtil.getDeepestLast(this);

        String typeName = switch (((TokenIElementType) target.getElementType()).getANTLRTokenType()) {
            case XLangLexer.NullLiteral  //
                    -> null;
            case XLangLexer.BooleanLiteral //
                    -> Boolean.class.getName();
            case XLangLexer.DecimalLiteral //
                    -> Float.class.getName();
            case XLangLexer.BinaryIntegerLiteral, XLangLexer.DecimalIntegerLiteral, //
                 XLangLexer.HexIntegerLiteral //
                    -> Integer.class.getName();
            case XLangLexer.StringLiteral, XLangLexer.TemplateStringLiteral //
                    -> String.class.getName();
            case XLangLexer.RegularExpressionLiteral //
                    -> Pattern.class.getName();
            default -> null;
        };

        return typeName != null ? PsiClassHelper.findClass(getProject(), typeName) : null;
    }
}
