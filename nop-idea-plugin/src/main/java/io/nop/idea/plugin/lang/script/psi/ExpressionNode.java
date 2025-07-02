package io.nop.idea.plugin.lang.script.psi;

import java.util.Collection;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.util.PsiTreeUtil;
import io.nop.idea.plugin.utils.PsiClassHelper;
import org.jetbrains.annotations.NotNull;

/**
 * 表达式节点
 * <p/>
 * {@link ObjectMemberNode 对象成员访问表达式}
 * <code>a.b.c</code>、<code>a.b().c()</code>，
 * 以及{@link ObjectDeclarationNode 对象声明表达式}
 * <code>{a, b: 2}</code> 均从属于该类型节点
 * <p/>
 * 其叶子节点可以为引用的变量名（identifier 类型），也可以为字面量（literal 类型）。
 * 其可以多层嵌套，例如，表达式 a.b.c(1, 2, 3) 包含以下子表达式：
 * <pre>
 * - a
 * - a.b
 * - a.b.c
 * - 1
 * - 2
 * - 3
 * </pre>
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-30
 */
public class ExpressionNode extends RuleSpecNode {

    public ExpressionNode(@NotNull ASTNode node) {
        super(node);
    }

    /** 是否为标识符 */
    public boolean isIdentifier() {
        return getFirstChild() instanceof IdentifierNode;
    }

    /** 是否为字面量 */
    public boolean isLiteral() {
        return getFirstChild() instanceof LiteralNode;
    }

    /** 是否为对象成员（成员变量或方法）表达式 */
    public boolean isObjectMember() {
        return getFirstChild() instanceof ObjectMemberNode;
    }

    /**
     * 是否为对象声明表达式
     * <p/>
     * {@link ObjectDeclarationNode} 的直接父节点为 {@link ExpressionNode}，
     * 因此，在构造 {@link ObjectMemberNode} 的成员引用时，
     * 一般不为对象声明中的属性构造引用
     */
    public boolean isObjectDeclaration() {
        return getFirstChild() instanceof ObjectDeclarationNode;
    }

    /** 是否为对象方法调用表达式 */
    public boolean isObjectMethodCall() {
        return getLastChild() instanceof CalleeArgumentsNode;
    }

    @Override
    public PsiReference @NotNull [] doGetReferences() {
        if (isObjectDeclaration()) {
            return PsiReference.EMPTY_ARRAY;
        } //
        else if (isObjectMethodCall()) {
//            // Note: 需加上相对于当前表达式的对象偏移量
//            TextRange calleeMethodTextRange = callee.getObjectMemberTextRange().shiftLeft(callee.getStartOffsetInParent());
            return PsiReference.EMPTY_ARRAY;
        }

        return PsiReference.EMPTY_ARRAY;
    }

    /**
     * 获取调用方的方法
     * <p/>
     * 注意，只有通过参数列表才能唯一确定调用方的方法
     */
    protected PsiMethod getCalleeMethod() {
        ExpressionNode callee = (ExpressionNode) getFirstChild();

        /* 函数调用，如 a(1)：
        ExpressionNode(expression_single)
          ExpressionNode(expression_single)
            IdentifierNode(identifier)
              PsiElement(Identifier)('a')
          CalleeArgumentsNode(arguments_)
            PsiElement('(')('(')
            ExpressionNode(expression_single)
              LiteralNode(literal)
                RuleSpecNode(literal_numeric)
                  PsiElement(DecimalIntegerLiteral)('1')
            PsiElement(')')(')')
        */
        if (callee.isIdentifier()) {
            return null;
        }

        /* 对象方法调用，如 a.b(1)：
        ExpressionNode(expression_single)
          ExpressionNode(expression_single)
            ExpressionNode(expression_single)
              IdentifierNode(identifier)
                PsiElement(Identifier)('a')
            PsiElement('.')('.')
            ObjectMemberNode(identifier_ex)
              RuleSpecNode(identifierOrKeyword_)
                IdentifierNode(identifier)
                  PsiElement(Identifier)('b')
          CalleeArgumentsNode(arguments_)
            PsiElement('(')('(')
            ExpressionNode(expression_single)
              LiteralNode(literal)
                RuleSpecNode(literal_numeric)
                  PsiElement(DecimalIntegerLiteral)('1')
            PsiElement(')')(')')
        */
        if (!callee.isObjectMember()) {
            return null;
        }

        PsiMethod[] calleeMethods = callee.getObjectMethods();
        PsiClass[] calleeMethodArgTypes = getCalleeArgumentTypes();

        for (PsiMethod method : calleeMethods) {
            if (matchMethodArgs(method, calleeMethodArgTypes)) {
                return method;
            }
        }
        return null;
    }

    /** 获取对象成员在当前表达式中的 {@link TextRange} */
    protected TextRange getObjectMemberTextRange() {
        ObjectMemberNode member = (ObjectMemberNode) getLastChild();

        return member.getTextRangeInParent();
    }

    /** 获取对象方法的引用 */
    protected PsiMethod @NotNull [] getObjectMethods() {
        /* 对象成员，如 a.b.c：
        ExpressionNode(expression_single)
          ExpressionNode(expression_single)
            ExpressionNode(expression_single)
              IdentifierNode(identifier)
                PsiElement(Identifier)('a')
            PsiElement('.')('.')
            ObjectMemberNode(identifier_ex)
              RuleSpecNode(identifierOrKeyword_)
                IdentifierNode(identifier)
                  PsiElement(Identifier)('b')
          PsiElement('.')('.')
          ObjectMemberNode(identifier_ex)
            RuleSpecNode(identifierOrKeyword_)
              IdentifierNode(identifier)
                PsiElement(Identifier)('c')
        */
        ExpressionNode source = (ExpressionNode) getFirstChild();
        ObjectMemberNode method = (ObjectMemberNode) getLastChild();

        PsiClass sourceClass = source.getResultType();
        if (sourceClass == null) {
            return PsiMethod.EMPTY_ARRAY;
        }

        String methodName = method.getText();
        return PsiClassImplUtil.findMethodsByName(sourceClass, methodName, true);
    }

    /** 获取调用参数类型列表 */
    protected PsiClass[] getCalleeArgumentTypes() {
        CalleeArgumentsNode node = (CalleeArgumentsNode) getLastChild();
        /*
        CalleeArgumentsNode(arguments_)
          PsiElement('(')('(')
          ExpressionNode(expression_single)
            LiteralNode(literal)
              RuleSpecNode(literal_numeric)
                PsiElement(DecimalIntegerLiteral)('1')
          PsiElement(',')(',')
          ExpressionNode(expression_single)
            LiteralNode(literal)
              RuleSpecNode(literal_numeric)
                PsiElement(DecimalIntegerLiteral)('2')
          PsiElement(')')(')')
        */
        Collection<ExpressionNode> argNodeList = PsiTreeUtil.findChildrenOfType(node, ExpressionNode.class);

        return argNodeList.stream().map(ExpressionNode::getResultType).toArray(PsiClass[]::new);
    }

    /**
     * 获取表达式结果的类型
     * <p/>
     * <code>null</code> 为有效值，可能是字面量即为 <code>null</code>
     */
    protected PsiClass getResultType() {
        PsiElement first = getFirstChild();
        if (first instanceof LiteralNode literal) {
            return literal.getDataType();
        } //
        else if (first instanceof IdentifierNode identifier) {
            return identifier.getDataType();
        } //
        else if (isObjectMethodCall()) {
            PsiMethod method = getCalleeMethod();
            PsiType returnType = method != null ? method.getReturnType() : null;

            if (returnType != null) {
                String typeName = returnType.getCanonicalText(false);
                return PsiClassHelper.findClass(getProject(), typeName);
            }
            return null;
        }

        /* TODO 复杂运算，如 a + b
        RuleSpecNode(expression_single)
          RuleSpecNode(expression_single)
            RuleSpecNode(identifier)
              PsiElement(Identifier)('a')
          PsiElement('+')('+')
          RuleSpecNode(expression_single)
            RuleSpecNode(identifier)
              PsiElement(Identifier)('b')
        */
        return null;
    }

    protected boolean matchMethodArgs(PsiMethod method, PsiClass[] args) {
        // TODO 依次比较方法的参数类型
        return method.getParameterList().getParametersCount() == args.length;
    }
}
