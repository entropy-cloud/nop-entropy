package io.nop.idea.plugin.lang.script.psi;

import java.util.Collection;
import java.util.function.BiFunction;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.PsiClassImplUtil;
import com.intellij.psi.util.PsiTreeUtil;
import io.nop.idea.plugin.lang.script.reference.ClassMethodReference;
import io.nop.idea.plugin.lang.script.reference.ClassPropertyReference;
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

    @Override
    public PsiReference @NotNull [] doGetReferences() {
        // 对象声明：{a, b: 1}
        if (isObjectDeclaration()) {
            return PsiReference.EMPTY_ARRAY;
        }
        // 对象方法调用：a.b.c(1, 2)
        else if (isObjectMethodCall()) {
            ExpressionNode obj = (ExpressionNode) getFirstChild();
            PsiMethod method = getObjectMethod();

            if (method != null) {
                // Note: 需加上相对于当前表达式的对象偏移量
                TextRange methodTextRange = obj.getObjectMemberTextRange().shiftLeft(obj.getStartOffsetInParent());
                ClassMethodReference ref = new ClassMethodReference(this, method, methodTextRange);

                return new PsiReference[] { ref };
            }
        }
        // 函数调用：fn1(1, 2, 3)
        else if (isFunctionCall()) {
            // TODO 构造函数引用
            return PsiReference.EMPTY_ARRAY;
        }
        // 对象属性访问：a.b.c
        else if (isObjectMember()) {
            PsiField prop = getObjectProperty();
            if (prop != null) {
                TextRange propTextRange = getObjectMemberTextRange();
                ClassPropertyReference ref = new ClassPropertyReference(this, prop, propTextRange);

                return new PsiReference[] { ref };
            }
        }

        return PsiReference.EMPTY_ARRAY;
    }

    /** 当前表达式是否为标识符 */
    public boolean isIdentifier() {
        return getFirstChild() instanceof IdentifierNode;
    }

    /** 当前表达式是否为字面量 */
    public boolean isLiteral() {
        return getFirstChild() instanceof LiteralNode;
    }

    /** 当前表达式是否为访问对象成员（成员变量或方法） */
    public boolean isObjectMember() {
        /* 对象成员访问，如 a.b:
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
        */
        if (getFirstChild() instanceof ExpressionNode obj) {
            return getLastChild() instanceof ObjectMemberNode;
        }
        return false;
    }

    /**
     * 当前表达式是否为对象声明
     * <p/>
     * {@link ObjectDeclarationNode} 的直接父节点为 {@link ExpressionNode}，
     * 因此，在构造 {@link ObjectMemberNode} 的成员引用时，
     * 一般不为对象声明中的属性构造引用
     */
    public boolean isObjectDeclaration() {
        /* 对象声明，如 {a, b: 1}:
        ExpressionNode(expression_single)
          ObjectDeclarationNode(objectExpression)
            PsiElement('{')('{')
            RuleSpecNode(objectProperties_)
              ObjectPropertyDeclarationNode(ast_objectProperty)
                ObjectPropertyAssignmentNode(propertyAssignment)
                  ObjectMemberNode(identifier_ex)
                    RuleSpecNode(identifierOrKeyword_)
                      IdentifierNode(identifier)
                        PsiElement(Identifier)('a')
              PsiElement(',')(',')
              ObjectPropertyDeclarationNode(ast_objectProperty)
                ObjectPropertyAssignmentNode(propertyAssignment)
                  RuleSpecNode(expression_propName)
                    ObjectMemberNode(identifier_ex)
                      RuleSpecNode(identifierOrKeyword_)
                        IdentifierNode(identifier)
                          PsiElement(Identifier)('b')
                  PsiElement(':')(':')
                  ExpressionNode(expression_single)
                    LiteralNode(literal)
                      RuleSpecNode(literal_numeric)
                        PsiElement(DecimalIntegerLiteral)('1')
            PsiElement('}')('}')
        */
        return getFirstChild() instanceof ObjectDeclarationNode;
    }

    /** 当前表达式是否为对象方法调用 */
    public boolean isObjectMethodCall() {
        /* 对象方法调用，如 a.b(1):
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
        if (getFirstChild() instanceof ExpressionNode obj) {
            return obj.isObjectMember() && getLastChild() instanceof CalleeArgumentsNode;
        }
        return false;
    }

    /** 当前表达式是否为对象构造函数调用 */
    public boolean isObjectConstructorCall() {
        /* 对象构造方法调用，如 new String("abc"):
        ExpressionNode(expression_single)
          PsiElement('new')('new')
          ParameterizedTypeNode(parameterizedTypeNode)
            RuleSpecNode(qualifiedName_)
              RuleSpecNode(qualifiedName)
                RuleSpecNode(qualifiedName_name_)
                  IdentifierNode(identifier)
                    PsiElement(Identifier)('String')
          CalleeArgumentsNode(arguments_)
            PsiElement('(')('(')
            ExpressionNode(expression_single)
              LiteralNode(literal)
                RuleSpecNode(literal_string)
                  PsiElement(StringLiteral)('"abc"')
            PsiElement(')')(')')
        */
        return getFirstChild().getText().equals("new") && getLastChild() instanceof CalleeArgumentsNode;
    }

    /** 当前表达式是否为函数调用 */
    public boolean isFunctionCall() {
        /* 函数调用，如 a(1):
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
        if (getFirstChild() instanceof ExpressionNode callee) {
            return callee.isIdentifier() && getLastChild() instanceof CalleeArgumentsNode;
        }
        return false;
    }

    /** 当前表达式是否为箭头函数 */
    public boolean isArrowFunction() {
        return getFirstChild() instanceof ArrowFunctionNode;
    }

    /** 获取对象的方法 */
    protected PsiMethod getObjectMethod() {
        ExpressionNode obj = (ExpressionNode) getFirstChild();

        PsiMethod[] objMethods = obj.getObjectMethods();
        PsiClass[] objMethodArgTypes = getObjectMethodArgumentTypes();

        // Note: 只有通过参数列表才能唯一确定调用方的方法
        for (PsiMethod method : objMethods) {
            if (matchMethodArgs(method, objMethodArgTypes)) {
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

    /** 获取对象的方法 */
    protected PsiMethod @NotNull [] getObjectMethods() {
        return getObjectMember((objClass, memberName) -> PsiClassImplUtil.findMethodsByName(objClass, memberName, true),
                               PsiMethod.EMPTY_ARRAY);
    }

    /** 获取对象的属性 */
    protected PsiField getObjectProperty() {
        return getObjectMember((objClass, memberName) -> PsiClassImplUtil.findFieldByName(objClass, memberName, true),
                               null);
    }

    protected <T> T getObjectMember(BiFunction<PsiClass, String, T> consumer, T defaultValue) {
        ExpressionNode obj = (ExpressionNode) getFirstChild();
        ObjectMemberNode member = (ObjectMemberNode) getLastChild();

        PsiClass objClass = obj.getResultType();
        if (objClass == null) {
            return defaultValue;
        }

        String memberName = member.getText();
        return consumer.apply(objClass, memberName);
    }

    /** 获取调用参数类型列表 */
    protected PsiClass[] getObjectMethodArgumentTypes() {
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
        PsiElement firstChild = getFirstChild();

        if (firstChild instanceof LiteralNode literal) {
            return literal.getDataType();
        } //
        else if (firstChild instanceof IdentifierNode identifier) {
            return identifier.getDataType();
        } //
        else if (isObjectMethodCall()) {
            PsiMethod method = getObjectMethod();
            PsiType returnType = method != null ? method.getReturnType() : null;

            return PsiClassHelper.getTypeClass(getProject(), returnType);
        } //
        else if (isObjectMember()) {
            PsiField prop = getObjectProperty();
            PsiType propType = prop != null ? prop.getType() : null;

            return PsiClassHelper.getTypeClass(getProject(), propType);
        } //
        else if (isObjectConstructorCall()) {
            ParameterizedTypeNode cst = PsiTreeUtil.findChildOfType(this, ParameterizedTypeNode.class);
            IdentifierNode typeNode = (IdentifierNode) PsiTreeUtil.getDeepestLast(cst).getParent();

            return typeNode.getDataType();
        } //
        else if (isFunctionCall()) {
            // TODO 分析 return 表达式，得到返回类型
            return null;
        } //
        else if (isArrowFunction()) {
            ArrowFunctionNode fn = (ArrowFunctionNode) getFirstChild();

            return fn.getReturnType();
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
