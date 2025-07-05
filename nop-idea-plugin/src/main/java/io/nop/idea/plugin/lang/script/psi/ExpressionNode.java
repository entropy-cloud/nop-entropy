package io.nop.idea.plugin.lang.script.psi;

import java.util.function.BiFunction;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.PsiClassImplUtil;
import io.nop.idea.plugin.lang.script.reference.PsiFieldReference;
import io.nop.idea.plugin.lang.script.reference.PsiMethodReference;
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
 * <p/>
 * 对象方法调用 <code>a.b(c, d)</code>：
 * <pre>
 * ExpressionNode(expression_single)
 *   ExpressionNode(expression_single)
 *     ExpressionNode(expression_single)
 *       IdentifierNode(identifier)
 *         PsiElement(Identifier)('a')
 *     PsiElement('.')('.')
 *     ObjectMemberNode(identifier_ex)
 *       RuleSpecNode(identifierOrKeyword_)
 *         IdentifierNode(identifier)
 *           PsiElement(Identifier)('b')
 *   CalleeArgumentsNode(arguments_)
 *     PsiElement('(')('(')
 *     ExpressionNode(expression_single)
 *       IdentifierNode(identifier)
 *         PsiElement(Identifier)('c')
 *     PsiElement(',')(',')
 *     PsiWhiteSpace(' ')
 *     ExpressionNode(expression_single)
 *       IdentifierNode(identifier)
 *         PsiElement(Identifier)('d')
 *     PsiElement(')')(')')
 * </pre>
 *
 * 函数调用 <code>a(1, 2)</code>：
 * <pre>
 * ExpressionNode(expression_single)
 *   ExpressionNode(expression_single)
 *     IdentifierNode(identifier)
 *       PsiElement(Identifier)('a')
 *   CalleeArgumentsNode(arguments_)
 *     PsiElement('(')('(')
 *     ExpressionNode(expression_single)
 *       LiteralNode(literal)
 *         RuleSpecNode(literal_numeric)
 *           PsiElement(DecimalIntegerLiteral)('1')
 *     PsiElement(',')(',')
 *     PsiWhiteSpace(' ')
 *     ExpressionNode(expression_single)
 *       LiteralNode(literal)
 *         RuleSpecNode(literal_numeric)
 *           PsiElement(DecimalIntegerLiteral)('2')
 *     PsiElement(')')(')')
 * </pre>
 *
 * 构造函数调用 <code>new String("abc")</code>：
 * <pre>
 * ExpressionNode(expression_single)
 *   PsiElement('new')('new')
 *   PsiWhiteSpace(' ')
 *   ParameterizedTypeNode(parameterizedTypeNode)
 *     RuleSpecNode(qualifiedName_)
 *       RuleSpecNode(qualifiedName)
 *         RuleSpecNode(qualifiedName_name_)
 *           IdentifierNode(identifier)
 *             PsiElement(Identifier)('String')
 *   CalleeArgumentsNode(arguments_)
 *     PsiElement('(')('(')
 *     ExpressionNode(expression_single)
 *       LiteralNode(literal)
 *         RuleSpecNode(literal_string)
 *           PsiElement(StringLiteral)('"abc"')
 *     PsiElement(')')(')')
 * </pre>
 *
 * 访问对象的成员变量 <code>a.b.c</code>：
 * <pre>
 * ExpressionNode(expression_single)
 *   ExpressionNode(expression_single)
 *     ExpressionNode(expression_single)
 *       IdentifierNode(identifier)
 *         PsiElement(Identifier)('a')
 *     PsiElement('.')('.')
 *     ObjectMemberNode(identifier_ex)
 *       RuleSpecNode(identifierOrKeyword_)
 *         IdentifierNode(identifier)
 *           PsiElement(Identifier)('b')
 *   PsiElement('.')('.')
 *   ObjectMemberNode(identifier_ex)
 *     RuleSpecNode(identifierOrKeyword_)
 *       IdentifierNode(identifier)
 *         PsiElement(Identifier)('c')
 * </pre>
 *
 * 对象声明 <code>{a, b: 1}</code>：
 * <pre>
 * ExpressionNode(expression_single)
 *   ObjectDeclarationNode(objectExpression)
 *     PsiElement('{')('{')
 *     RuleSpecNode(objectProperties_)
 *       ObjectPropertyDeclarationNode(ast_objectProperty)
 *         ObjectPropertyAssignmentNode(propertyAssignment)
 *           ObjectMemberNode(identifier_ex)
 *             RuleSpecNode(identifierOrKeyword_)
 *               IdentifierNode(identifier)
 *                 PsiElement(Identifier)('a')
 *       PsiElement(',')(',')
 *       PsiWhiteSpace(' ')
 *       ObjectPropertyDeclarationNode(ast_objectProperty)
 *         ObjectPropertyAssignmentNode(propertyAssignment)
 *           RuleSpecNode(expression_propName)
 *             ObjectMemberNode(identifier_ex)
 *               RuleSpecNode(identifierOrKeyword_)
 *                 IdentifierNode(identifier)
 *                   PsiElement(Identifier)('b')
 *           PsiElement(':')(':')
 *           PsiWhiteSpace(' ')
 *           ExpressionNode(expression_single)
 *             LiteralNode(literal)
 *               RuleSpecNode(literal_numeric)
 *                 PsiElement(DecimalIntegerLiteral)('1')
 *     PsiElement('}')('}')
 * </pre>
 *
 * 箭头函数声明 <code>(a, b) => a + b</code>：
 * <pre>
 * ExpressionNode(expression_single)
 *   ArrowFunctionNode(arrowFunctionExpression)
 *     PsiElement('(')('(')
 *     RuleSpecNode(parameterList_)
 *       FunctionParameterDeclarationNode(parameterDeclaration)
 *         RuleSpecNode(ast_identifierOrPattern)
 *           IdentifierNode(identifier)
 *             PsiElement(Identifier)('a')
 *       PsiElement(',')(',')
 *       PsiWhiteSpace(' ')
 *       FunctionParameterDeclarationNode(parameterDeclaration)
 *         RuleSpecNode(ast_identifierOrPattern)
 *           IdentifierNode(identifier)
 *             PsiElement(Identifier)('b')
 *     PsiElement(')')(')')
 *     PsiWhiteSpace(' ')
 *     PsiElement('=>')('=>')
 *     PsiWhiteSpace(' ')
 *     ArrowFunctionBodyNode(expression_functionBody)
 *       ExpressionNode(expression_single)
 *         ExpressionNode(expression_single)
 *           IdentifierNode(identifier)
 *             PsiElement(Identifier)('a')
 *         PsiWhiteSpace(' ')
 *         PsiElement('+')('+')
 *         PsiWhiteSpace(' ')
 *         ExpressionNode(expression_single)
 *           IdentifierNode(identifier)
 *             PsiElement(Identifier)('b')
 * </pre>
 *
 * 变量运算 <code>a + b</code>：
 * <pre>
 * ExpressionNode(expression_single)
 *   ExpressionNode(expression_single)
 *     IdentifierNode(identifier)
 *       PsiElement(Identifier)('a')
 *   PsiWhiteSpace(' ')
 *   PsiElement('+')('+')
 *   PsiWhiteSpace(' ')
 *   ExpressionNode(expression_single)
 *     IdentifierNode(identifier)
 *       PsiElement(Identifier)('b')
 * </pre>
 *
 * 变量运算 <code>a > 2</code>：
 * <pre>
 * ExpressionNode(expression_single)
 *   ExpressionNode(expression_single)
 *     IdentifierNode(identifier)
 *       PsiElement(Identifier)('a')
 *   PsiWhiteSpace(' ')
 *   PsiElement('>')('>')
 *   PsiWhiteSpace(' ')
 *   ExpressionNode(expression_single)
 *     LiteralNode(literal)
 *       RuleSpecNode(literal_numeric)
 *         PsiElement(DecimalIntegerLiteral)('2')
 * </pre>
 *
 * 构造函数及其方法的调用 <code>new String("def").trim()</code>：
 * <pre>
 * ExpressionNode(expression_single)
 *   ExpressionNode(expression_single)
 *     ExpressionNode(expression_single)
 *       PsiElement('new')('new')
 *       PsiWhiteSpace(' ')
 *       ParameterizedTypeNode(parameterizedTypeNode)
 *         RuleSpecNode(qualifiedName_)
 *           RuleSpecNode(qualifiedName)
 *             RuleSpecNode(qualifiedName_name_)
 *               IdentifierNode(identifier)
 *                 PsiElement(Identifier)('String')
 *       CalleeArgumentsNode(arguments_)
 *         PsiElement('(')('(')
 *         ExpressionNode(expression_single)
 *           LiteralNode(literal)
 *             RuleSpecNode(literal_string)
 *               PsiElement(StringLiteral)('"def"')
 *         PsiElement(')')(')')
 *     PsiElement('.')('.')
 *     ObjectMemberNode(identifier_ex)
 *       RuleSpecNode(identifierOrKeyword_)
 *         IdentifierNode(identifier)
 *           PsiElement(Identifier)('trim')
 *   CalleeArgumentsNode(arguments_)
 *     PsiElement('(')('(')
 *     PsiElement(')')(')')
 * </pre>
 *
 * 数组声明 <code>[a, b, c]</code>：
 * <pre>
 * ExpressionNode(expression_single)
 *   RuleSpecNode(arrayExpression)
 *     PsiElement('[')('[')
 *     RuleSpecNode(elementList_)
 *       RuleSpecNode(ast_arrayElement)
 *         ExpressionNode(expression_single)
 *           IdentifierNode(identifier)
 *             PsiElement(Identifier)('a')
 *       PsiElement(',')(',')
 *       PsiWhiteSpace(' ')
 *       RuleSpecNode(ast_arrayElement)
 *         ExpressionNode(expression_single)
 *           IdentifierNode(identifier)
 *             PsiElement(Identifier)('b')
 *       PsiElement(',')(',')
 *       PsiWhiteSpace(' ')
 *       RuleSpecNode(ast_arrayElement)
 *         ExpressionNode(expression_single)
 *           IdentifierNode(identifier)
 *             PsiElement(Identifier)('c')
 *     PsiElement(']')(']')
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
        // Note:
        // - 仅识别当前表达式的最后一个有效元素的引用，其余部分，由其子表达式做识别处理
        // - 对象声明节点 ObjectDeclarationNode 的相关引用，由其自身负责构造
        // - 对构造函数 ParameterizedTypeNode 的相关引用，由其自身负责构造

        PsiElement firstChild = getFirstChild();
        // 变量引用：abc
        if (firstChild instanceof IdentifierNode i) {
            TextRange textRange = i.getTextRangeInParent();

            return i.createReferences(this, textRange);
        }
        // 对象方法调用：a.b.c(1, 2)
        else if (isObjectMethodCall()) {
            ExpressionNode obj = (ExpressionNode) firstChild;
            PsiMethod method = getObjectMethod();

            if (method != null) {
                // Note: 需加上相对于当前表达式的对象偏移量
                TextRange methodTextRange = obj.getObjectMemberTextRange().shiftLeft(obj.getStartOffsetInParent());
                PsiMethodReference ref = new PsiMethodReference(this, method, methodTextRange);

                return new PsiReference[] { ref };
            }
        }
        // 对象属性访问：a.b.c
        else if (isObjectMemberAccess()) {
            PsiField prop = getObjectProperty();

            if (prop != null) {
                TextRange propTextRange = getObjectMemberTextRange();
                PsiFieldReference ref = new PsiFieldReference(this, prop, propTextRange);

                return new PsiReference[] { ref };
            }
        }
        // 函数调用：fn1(1, 2, 3)
        else if (isFunctionCall()) {
            ExpressionNode callee = (ExpressionNode) firstChild;
            TextRange textRange = callee.getTextRangeInParent();

            IdentifierNode fn = (IdentifierNode) callee.getFirstChild();

            return fn.createReferences(this, textRange);
        }

        return PsiReference.EMPTY_ARRAY;
    }

    /**
     * 获取表达式结果的类型
     *
     * @return <code>null</code> 为有效值
     */
    public PsiClass getResultType() {
        PsiElement firstChild = getFirstChild();

        if (firstChild instanceof LiteralNode l) {
            return l.getDataType();
        } //
        else if (firstChild instanceof IdentifierNode i) {
            return i.getDataType();
        } //
        else if (isObjectConstructorCall()) {
            ParameterizedTypeNode cons = findChildByClass(ParameterizedTypeNode.class);

            return cons != null ? cons.getParameterizedType() : null;
        } //
        else if (isObjectMethodCall()) {
            PsiMethod method = getObjectMethod();
            PsiType returnType = method != null ? method.getReturnType() : null;

            return getPsiClassByPsiType(returnType);
        }
        // 若不是对象的方法调用，便是对象的属性访问
        else if (isObjectMemberAccess()) {
            PsiField prop = getObjectProperty();
            PsiType propType = prop != null ? prop.getType() : null;

            return getPsiClassByPsiType(propType);
        } //
        else if (isFunctionCall()) {
            ExpressionNode callee = (ExpressionNode) firstChild;
            IdentifierNode fn = (IdentifierNode) callee.getFirstChild();

            // Note: 对应的是函数的返回值类型
            return fn.getDataType();
        } //
        else if (isArrowFunction()) {
            ArrowFunctionNode fn = (ArrowFunctionNode) firstChild;

            return fn.getReturnType();
        } //
        else if (isArrayInit()) {
            ArrayExpressionNode array = (ArrayExpressionNode) firstChild;

            return array.getElementType();
        }

        // TODO 运算表达式，如 a + b
        return null;
    }

    /** 当前表达式是否为对象成员（成员变量或方法）访问 */
    public boolean isObjectMemberAccess() {
        // a.b.c
        if (getFirstChild() instanceof ExpressionNode) {
            return getLastChild() instanceof ObjectMemberNode;
        }
        return false;
    }

    /** 当前表达式是否为对象方法调用 */
    public boolean isObjectMethodCall() {
        // a.b.c()
        if (getFirstChild() instanceof ExpressionNode obj) {
            return obj.isObjectMemberAccess() && getLastChild() instanceof CalleeArgumentsNode;
        }
        return false;
    }

    /** 当前表达式是否为对象构造函数调用 */
    public boolean isObjectConstructorCall() {
        // new String("abc")
        return getFirstChild().getText().equals("new") && getLastChild() instanceof CalleeArgumentsNode;
    }

    /** 当前表达式是否为函数调用 */
    public boolean isFunctionCall() {
        // a(1)
        if (getFirstChild() instanceof ExpressionNode callee) {
            return callee.getFirstChild() instanceof IdentifierNode //
                   && getLastChild() instanceof CalleeArgumentsNode;
        }
        return false;
    }

    /** 当前表达式是否为箭头函数 */
    public boolean isArrowFunction() {
        // (a, b) => a + b
        return getFirstChild() instanceof ArrowFunctionNode;
    }

    /** 当前表达式是否为数组初始化 */
    public boolean isArrayInit() {
        // [1, 2, 3]
        return getFirstChild() instanceof ArrayExpressionNode;
    }

    /** 获取对象的方法 */
    protected PsiMethod getObjectMethod() {
        ExpressionNode obj = (ExpressionNode) getFirstChild();

        PsiMethod[] methods = obj.getObjectMethods();
        PsiClass[] argTypes = getObjectMethodArgumentTypes();

        return filterMethodByArgs(methods, argTypes);
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

    /** 获取调用参数的类型列表 */
    protected PsiClass[] getObjectMethodArgumentTypes() {
        CalleeArgumentsNode calleeArgs = (CalleeArgumentsNode) getLastChild();

        return calleeArgs.getArgumentTypes();
    }

    protected <T> T getObjectMember(BiFunction<PsiClass, String, T> consumer, T defaultValue) {
        ExpressionNode obj = (ExpressionNode) getFirstChild();
        PsiClass objClass = obj.getResultType();

        if (objClass == null) {
            return defaultValue;
        }

        ObjectMemberNode member = (ObjectMemberNode) getLastChild();
        String memberName = member.getText();

        return consumer.apply(objClass, memberName);
    }

    protected PsiMethod filterMethodByArgs(PsiMethod[] methods, PsiClass[] args) {
        // 只有唯一的方法，则直接返回
        if (methods.length == 1) {
            return methods[0];
        }

        // 优先查找参数列表完全匹配的方法
        for (PsiMethod method : methods) {
            PsiParameter[] params = method.getParameterList().getParameters();

            if (matchMethodParams(params, args)) {
                return method;
            }
        }

        // 再查找参数数量一致的方法
        for (PsiMethod method : methods) {
            if (method.getParameterList().getParametersCount() == args.length) {
                return method;
            }
        }

        return null;
    }

    protected boolean matchMethodParams(PsiParameter[] params, PsiClass[] args) {
        if (params.length != args.length) {
            return false;
        }

        for (int i = 0; i < params.length; i++) {
            PsiClass arg = args[i];
            PsiClass param = getPsiClassByPsiType(params[i].getType());

            if (arg == param) {
                continue;
            }

            if (arg == null || param == null //
                || !arg.isInheritor(param, true) //
            ) {
                return false;
            }
        }
        return true;
    }
}
