package io.nop.idea.plugin.lang.script.psi;

import java.util.HashMap;
import java.util.Map;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.antlr.intellij.adaptor.lexer.RuleIElementType;
import org.antlr.intellij.adaptor.psi.Trees;
import org.jetbrains.annotations.NotNull;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-29
 */
public class RuleSpecNode extends ASTWrapperPsiElement {

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
        // 在没有写入动作时，才执行函数并返回结果，从而避免阻塞编辑操作
        return ReadAction.compute(this::doGetReferences);
    }

    public IdentifierNode getIdentifier() {
        return findChildByClass(IdentifierNode.class);
    }

    public boolean isRuleNode(int ruleIndex) {
        return ((RuleIElementType) getNode().getElementType()).getRuleIndex() == ruleIndex;
    }

    /** 获取当前节点可访问到的变量及其类型 */
    public @NotNull Map<String, VarDecl> getVisibleVarTypes() {
        Map<String, VarDecl> types = new HashMap<>();

        PsiElement node = this;
        while (node instanceof RuleSpecNode) {
            PsiElement parent = node.getParent();

            // Note: 下层的变量优先于上层的变量
            if (parent instanceof RuleSpecNode) {
                for (PsiElement child : parent.getChildren()) {
                    if (child == node) {
                        break; // 只取当前节点之前定义的变量
                    }

                    if (child instanceof RuleSpecNode c) {
                        c.getVarTypes().forEach(types::putIfAbsent);
                    }
                }
            } else {
                // TODO 从所在的 <c:script/> 标签中获取 xlib 函数的参数列表以及内置变量列表
            }

            node = parent;
        }
        return types;
    }

    /** 获取当前节点所定义的变量及其类型 */
    public @NotNull Map<String, VarDecl> getVarTypes() {
        return Map.of();
    }

    protected PsiReference @NotNull [] doGetReferences() {
        return PsiReference.EMPTY_ARRAY;
    }

    public static class VarDecl {
        public final PsiElement element;
        public final PsiClass type;

        VarDecl(PsiElement element, PsiClass type) {
            this.element = element;
            this.type = type;
        }
    }
}
