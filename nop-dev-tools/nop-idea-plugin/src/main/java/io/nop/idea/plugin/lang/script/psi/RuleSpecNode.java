/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.script.psi;

import java.util.regex.Pattern;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import io.nop.idea.plugin.lang.XLangVarDecl;
import io.nop.idea.plugin.lang.XLangVarScope;
import io.nop.idea.plugin.utils.PsiClassHelper;
import org.antlr.intellij.adaptor.lexer.RuleIElementType;
import org.antlr.intellij.adaptor.lexer.TokenIElementType;
import org.antlr.intellij.adaptor.psi.Trees;
import org.jetbrains.annotations.NotNull;

import static io.nop.idea.plugin.lang.script.XLangScriptTokenTypes.TOKEN_literal_boolean;
import static io.nop.idea.plugin.lang.script.XLangScriptTokenTypes.TOKEN_literal_decimal;
import static io.nop.idea.plugin.lang.script.XLangScriptTokenTypes.TOKEN_literal_integer;
import static io.nop.idea.plugin.lang.script.XLangScriptTokenTypes.TOKEN_literal_regex;
import static io.nop.idea.plugin.lang.script.XLangScriptTokenTypes.TOKEN_literal_string;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-29
 */
public class RuleSpecNode extends ASTWrapperPsiElement implements XLangVarScope {

    public RuleSpecNode(@NotNull ASTNode node) {
        super(node);
    }

    /** @return 不含注释和空白节点 */
    @Override
    public PsiElement @NotNull [] getChildren() {
        return Trees.getChildren(this);
    }

    @Override
    public PsiReference @NotNull [] getReferences() {
        // Note: 不能直接缓存 PsiReference，否则，容易造成数据不一致

        // 在没有写入动作时，才执行函数并返回结果，从而避免阻塞编辑操作
        return ReadAction.compute(this::doGetReferences);
    }

    protected PsiReference @NotNull [] doGetReferences() {
        return PsiReference.EMPTY_ARRAY;
    }

    public boolean isRuleType(RuleIElementType type) {
        return getNode().getElementType() == type;
    }

    /** 获取在当前节点上可见的指定变量 */
    public XLangVarDecl findVisibleVar(String varName) {
        PsiElement node = this;

        while (node instanceof RuleSpecNode) {
            PsiElement parent = node.getParent();

            // Note: 下层的变量优先于上层的变量
            if (parent instanceof RuleSpecNode) {
                PsiElement prev = node;

                // 从当前节点开始往前查找，并选择靠得最近的变量
                while (prev != null) {
                    prev = prev.getPrevSibling();
                    if (!(prev instanceof XLangVarScope scope)) {
                        continue;
                    }

                    XLangVarDecl var = scope.getVars().get(varName);
                    if (var != null) {
                        return var;
                    }
                }
            }
            // 从所在的 <c:script/> 标签中获取 xlib 函数的参数列表以及内置变量列表
            else if (parent instanceof XLangVarScope scope) {
                XLangVarDecl var = scope.getVars().get(varName);
                if (var != null) {
                    return var;
                }
            }

            node = parent;
        }
        return null;
    }

    protected PsiClass getPsiClassByPsiType(PsiType type) {
        return PsiClassHelper.getTypeClass(this, type);
    }

    protected PsiClass getPsiClassByToken(TokenIElementType token) {
        Class<?> clazz = null;

        if (token == TOKEN_literal_boolean) {
            clazz = Boolean.class;
        } else if (token == TOKEN_literal_decimal) {
            clazz = Float.class;
        } else if (token == TOKEN_literal_regex) {
            clazz = Pattern.class;
        } else if (TOKEN_literal_integer.contains(token)) {
            clazz = Integer.class;
        } else if (TOKEN_literal_string.contains(token)) {
            clazz = String.class;
        }
        return clazz != null ? PsiClassHelper.findClass(this, clazz.getName()) : null;
    }
}
