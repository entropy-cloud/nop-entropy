/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.compile;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.commons.util.CollectionHelper;
import io.nop.commons.util.objects.OptionalValue;
import io.nop.core.lang.eval.EvalRuntime;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.eval.global.EvalGlobalRegistry;
import io.nop.core.lang.eval.global.IGlobalVariableDefinition;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.core.type.impl.PredefinedGenericType;
import io.nop.core.type.utils.GenericTypeHelper;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.api.XLang;
import io.nop.xlang.ast.ArrayBinding;
import io.nop.xlang.ast.ArrayElementBinding;
import io.nop.xlang.ast.ArrayExpression;
import io.nop.xlang.ast.ArrowFunctionExpression;
import io.nop.xlang.ast.AssignmentExpression;
import io.nop.xlang.ast.BlockStatement;
import io.nop.xlang.ast.BreakStatement;
import io.nop.xlang.ast.CallExpression;
import io.nop.xlang.ast.ContinueStatement;
import io.nop.xlang.ast.DoWhileStatement;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.ForInStatement;
import io.nop.xlang.ast.ForOfStatement;
import io.nop.xlang.ast.ForRangeStatement;
import io.nop.xlang.ast.ForStatement;
import io.nop.xlang.ast.FunctionDeclaration;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.IdentifierKind;
import io.nop.xlang.ast.ImportAsDeclaration;
import io.nop.xlang.ast.Literal;
import io.nop.xlang.ast.MacroExpression;
import io.nop.xlang.ast.MemberExpression;
import io.nop.xlang.ast.NamedTypeNode;
import io.nop.xlang.ast.ObjectBinding;
import io.nop.xlang.ast.ObjectExpression;
import io.nop.xlang.ast.ParameterDeclaration;
import io.nop.xlang.ast.ParameterizedTypeNode;
import io.nop.xlang.ast.Program;
import io.nop.xlang.ast.PropertyAssignment;
import io.nop.xlang.ast.PropertyBinding;
import io.nop.xlang.ast.TemplateStringExpression;
import io.nop.xlang.ast.ThisExpression;
import io.nop.xlang.ast.TypeNameNode;
import io.nop.xlang.ast.UpdateExpression;
import io.nop.xlang.ast.VariableDeclaration;
import io.nop.xlang.ast.VariableDeclarator;
import io.nop.xlang.ast.VariableKind;
import io.nop.xlang.ast.WhileStatement;
import io.nop.xlang.ast.XLangASTBuilder;
import io.nop.xlang.ast.XLangASTKind;
import io.nop.xlang.ast.XLangASTNode;
import io.nop.xlang.ast.XLangASTVisitor;
import io.nop.xlang.ast.XLangIdentifierDefinition;
import io.nop.xlang.ast.definition.ClosureRefDefinition;
import io.nop.xlang.ast.definition.GlobalVarDefinition;
import io.nop.xlang.ast.definition.ImportClassDefinition;
import io.nop.xlang.ast.definition.LocalVarDeclaration;
import io.nop.xlang.ast.definition.ResolvedFuncDefinition;
import io.nop.xlang.ast.definition.ScopeVarDefinition;
import io.nop.xlang.scope.LexicalScope;
import io.nop.xlang.xpl.utils.XplParseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.xlang.XLangErrors.ARG_EXPR;
import static io.nop.xlang.XLangErrors.ARG_FUNC_NAME;
import static io.nop.xlang.XLangErrors.ARG_NAME;
import static io.nop.xlang.XLangErrors.ARG_PARAM_NAME;
import static io.nop.xlang.XLangErrors.ARG_TYPE_NAME;
import static io.nop.xlang.XLangErrors.ARG_VAR_NAME;
import static io.nop.xlang.XLangErrors.ERR_XLANG_BREAK_STATEMENT_NOT_IN_LOOP;
import static io.nop.xlang.XLangErrors.ERR_XLANG_CONST_DECL_NO_INITIALIZER;
import static io.nop.xlang.XLangErrors.ERR_XLANG_CONTINUE_STATEMENT_NOT_IN_LOOP;
import static io.nop.xlang.XLangErrors.ERR_XLANG_FUNC_DECL_CONFLICT_WITH_GLOBAL_FUNC;
import static io.nop.xlang.XLangErrors.ERR_XLANG_FUNC_DECL_NOT_ALLOW_PREFIX_G;
import static io.nop.xlang.XLangErrors.ERR_XLANG_GLOBAL_VAR_NOT_ALLOW_CHANGE;
import static io.nop.xlang.XLangErrors.ERR_XLANG_IDENTIFIER_NOT_ALLOW_CHANGE;
import static io.nop.xlang.XLangErrors.ERR_XLANG_IDENTIFIER_NOT_FUNCTION;
import static io.nop.xlang.XLangErrors.ERR_XLANG_INITIALIZER_ONLY_ALLOW_LITERAL;
import static io.nop.xlang.XLangErrors.ERR_XLANG_PARAM_NAME_CONFLICTED;
import static io.nop.xlang.XLangErrors.ERR_XLANG_TEMPLATE_EXPR_ID_MUST_BE_MACRO_FUNCTION;
import static io.nop.xlang.XLangErrors.ERR_XLANG_UNRESOLVED_IDENTIFIER;
import static io.nop.xlang.XLangErrors.ERR_XLANG_UNRESOLVED_IMPLICIT_VAR;
import static io.nop.xlang.XLangErrors.ERR_XLANG_UNRESOLVED_TYPE;
import static io.nop.xlang.XLangErrors.ERR_XLANG_VAR_DECL_NO_ALLOW_BINDING;
import static io.nop.xlang.ast.XLangASTBuilder.deepClone;
import static io.nop.xlang.ast.XLangASTBuilder.identifier;
import static io.nop.xlang.ast.XLangASTBuilder.prependAll;

/**
 * 分析语法树，确定每一个变量引用的原始定义，构建LexicalScope对象，为局部变量和闭包变量分配slot(对应于运行时堆栈的位置)
 */
public class LexicalScopeAnalysis extends XLangASTVisitor {
    static final Logger LOG = LoggerFactory.getLogger(LexicalScopeAnalysis.class);

    private final IXLangCompileScope scope;

    private Expression rootNode;

    public LexicalScopeAnalysis(IXLangCompileScope scope) {
        this.scope = scope;
    }

    public Expression analyze(Expression node) {
        rootNode = node;
        visit(rootNode);

        new XLangASTVisitor() {
            @Override
            public void visitProgram(Program node) {
                if (node.getLexicalScope() != null)
                    node.getLexicalScope().hoistClosureVars();
                super.visitProgram(node);
            }

            @Override
            public void visitFunctionDeclaration(FunctionDeclaration node) {
                node.getLexicalScope().hoistClosureVars();
                super.visitFunctionDeclaration(node);
            }

            @Override
            public void visitArrowFunctionExpression(ArrowFunctionExpression node) {
                node.getLexicalScope().hoistClosureVars();
                super.visitArrowFunctionExpression(node);
            }
        }.visit(rootNode);

        return rootNode;
    }

    @Override
    public void visitProgram(Program node) {
        scope.enterBlock(node.getASTParent() == null);
        try {
            // 先收集函数定义。Identifier可以引用后定义的函数，但是不能引用后定义的变量。
            collectFunctions(node.getBody());

            super.visitProgram(node);
        } finally {
            LexicalScope fnScope = scope.leaveBlock(node.getASTParent() == null);
            node.setLexicalScope(fnScope);
        }
    }

    @Override
    public void visitBlockStatement(BlockStatement node) {
        scope.enterBlock(false);

        try {
            // 先收集函数定义。Identifier可以引用后定义的函数，但是不能引用后定义的变量。
            collectFunctions(node.getBody());

            super.visitBlockStatement(node);
        } finally {
            scope.leaveBlock(false);
        }
    }

    // 先收集函数定义
    private void collectFunctions(List<? extends XLangASTNode> body) {
        if (body != null) {
            for (XLangASTNode expr : body) {
                if (expr instanceof FunctionDeclaration) {
                    FunctionDeclaration decl = (FunctionDeclaration) expr;
                    decl.getName().setFunction(true);
                    String funcName = decl.getName().getName();
                    if (funcName.startsWith(XLangConstants.GLOBAL_FUNC_PREFIX))
                        throw new NopEvalException(ERR_XLANG_FUNC_DECL_NOT_ALLOW_PREFIX_G).source(decl)
                                .param(ARG_FUNC_NAME, funcName);

                    IFunctionModel globalFunc = EvalGlobalRegistry.instance().getRegisteredFunction(funcName);
                    if (globalFunc != null)
                        throw new NopEvalException(ERR_XLANG_FUNC_DECL_CONFLICT_WITH_GLOBAL_FUNC).source(decl)
                                .param(ARG_FUNC_NAME, funcName);

                    scope.addVarDeclaration(makeVarDeclaration(decl.getName(), IdentifierKind.FUNC_DECL, false),
                            scope.isInMacro());
                }
            }
        }
    }

    LocalVarDeclaration makeVarDeclaration(Identifier identifier, IdentifierKind kind, boolean allowAssignment) {
        return LocalVarDeclaration.makeVarDeclaration(identifier, kind, allowAssignment);
    }

    @Override
    public void visitImportAsDeclaration(ImportAsDeclaration node) {
        XplParseHelper.runImportExpr(scope, node);
    }

    @Override
    public void visitVariableDeclaration(VariableDeclaration node) {
        if (node.getKind() == VariableKind.VAR) {
            // 如果是var声明，则先检查变量是否已经定义。
            for (VariableDeclarator declarator : node.getDeclarators()) {
                if (!(declarator.getId() instanceof Identifier)) {
                    throw new NopEvalException(ERR_XLANG_VAR_DECL_NO_ALLOW_BINDING).param(ARG_EXPR, declarator);
                }
                Identifier id = (Identifier) declarator.getId();

                XLangIdentifierDefinition resolved = scope.resolveVarInFunctionScope(id.getLocation(), id.getName(),
                        scope.isInMacro());

                if (declarator.getInit() == null) {
                    if (resolved != null)
                        continue;
                }

                if (resolved == null) {
                    makeVarDeclaration((Identifier) declarator.getId(), IdentifierKind.VAR_DECL, true);
                } else {
                    if (!resolved.isAllowAssignment()) {
                        throw new NopEvalException(ERR_XLANG_IDENTIFIER_NOT_ALLOW_CHANGE).source(id).param(ARG_NAME,
                                id.getName());
                    }
                }
            }

            super.visitVariableDeclaration(node);
            return;
        }

        boolean allowAssignment = node.getKind() != VariableKind.CONST;
        // 这里仅对Identifier变量声明进行标记，在visitIdentifier再进行变量注册。变量的注册和访问顺序需要按照语法树结构进行
        for (VariableDeclarator declarator : node.getDeclarators()) {
            if (!allowAssignment) {
                if (declarator.getInit() == null)
                    throw new NopEvalException(ERR_XLANG_CONST_DECL_NO_INITIALIZER).param(ARG_EXPR, declarator);
            }

            if (declarator.getId() instanceof Identifier) {
                LocalVarDeclaration var = makeVarDeclaration((Identifier) declarator.getId(), IdentifierKind.VAR_DECL,
                        allowAssignment);
                if (!allowAssignment) {
                    OptionalValue constValue = XplParseHelper.staticValue(declarator.getInit());
                    var.setConstValue(constValue);
                }
            } else {
                visitBinding(declarator.getId(), false, allowAssignment);
            }
        }
        super.visitVariableDeclaration(node);
    }

    private void visitBinding(XLangASTNode id, boolean param, boolean allowAssignment) {
        IdentifierKind kind = param ? IdentifierKind.PARAM_DECL : IdentifierKind.VAR_DECL;
        if (id instanceof ArrayBinding) {
            ArrayBinding binding = (ArrayBinding) id;
            List<ArrayElementBinding> elements = binding.getElements();
            if (elements != null) {
                for (ArrayElementBinding element : elements) {
                    makeVarDeclaration(element.getIdentifier(), kind, allowAssignment);
                }
                if (binding.getRestBinding() != null) {
                    makeVarDeclaration(binding.getRestBinding().getIdentifier(), kind, allowAssignment);
                }
            }
        } else if (id instanceof ObjectBinding) {
            ObjectBinding binding = (ObjectBinding) id;
            List<PropertyBinding> elements = binding.getProperties();
            if (elements != null) {
                for (PropertyBinding element : elements) {
                    makeVarDeclaration(element.makeIdentifier(), kind, allowAssignment);
                }
                if (binding.getRestBinding() != null) {
                    makeVarDeclaration(binding.getRestBinding().getIdentifier(), kind, allowAssignment);
                }
            }
        }
    }

    @Override
    public void visitFunctionDeclaration(FunctionDeclaration node) {
        checkParamNameUnique(node.getParams());
        List<VariableDeclaration> decls = normalizeParams(node.getParams());
        if (!decls.isEmpty()) {
            node.setBody(prependAll(node.getBody(), decls));
        }

        scope.enterBlock(true);
        try {
            super.visitFunctionDeclaration(node);
        } finally {
            LexicalScope fnScope = scope.leaveBlock(true);
            node.setLexicalScope(fnScope);
        }
    }

    // 获取静态json值
    Object getJsonValue(XLangASTNode expr) {
        switch (expr.getASTKind()) {
            case Literal:
                return ((Literal) expr).getValue();
            case ObjectExpression:
                return getObjectJsonValue((ObjectExpression) expr);
            case ArrayExpression:
                return getArrayJsonValue((ArrayExpression) expr);
            default:
                throw new NopEvalException(ERR_XLANG_INITIALIZER_ONLY_ALLOW_LITERAL).source(expr);
        }
    }

    Object getObjectJsonValue(ObjectExpression expr) {
        Map<String, Object> map = CollectionHelper.newLinkedHashMap(expr.getProperties().size());
        for (XLangASTNode prop : expr.getProperties()) {
            if (prop.getASTKind() == XLangASTKind.PropertyAssignment) {
                PropertyAssignment assign = (PropertyAssignment) prop;
                if (assign.getKey().getASTKind() != XLangASTKind.Identifier)
                    throw new NopEvalException(ERR_XLANG_INITIALIZER_ONLY_ALLOW_LITERAL).source(expr);
                String propName = ((Identifier) assign.getKey()).getName();
                Object value = getJsonValue(assign.getValue());
                map.put(propName, value);
            } else {
                throw new NopEvalException(ERR_XLANG_INITIALIZER_ONLY_ALLOW_LITERAL).source(expr);
            }
        }
        return map;
    }

    Object getArrayJsonValue(ArrayExpression expr) {
        List<Object> list = new ArrayList<>(expr.getElements().size());
        for (XLangASTNode elm : expr.getElements()) {
            Object value = getJsonValue(elm);
            list.add(value);
        }
        return list;
    }

    @Override
    public void visitArrowFunctionExpression(ArrowFunctionExpression node) {
        checkParamNameUnique(node.getParams());
        List<VariableDeclaration> decls = normalizeParams(node.getParams());
        if (!decls.isEmpty()) {
            node.setBody(prependAll(node.getBody(), decls));
        }

        scope.enterBlock(true);

        try {
            super.visitArrowFunctionExpression(node);
        } finally {
            LexicalScope fnScope = scope.leaveBlock(true);
            node.setLexicalScope(fnScope);
        }
    }

    @Override
    public void visitParameterDeclaration(ParameterDeclaration param) {
        if (param.getName() instanceof Identifier) {
            makeVarDeclaration((Identifier) param.getName(), IdentifierKind.PARAM_DECL, true);
        } else {
            visitBinding(param.getName(), true, true);
        }
        super.visitParameterDeclaration(param);
    }

    // 规范化参数形式，将参数模式转换为赋值语句
    private List<VariableDeclaration> normalizeParams(List<ParameterDeclaration> params) {
        List<VariableDeclaration> decls = Collections.emptyList();

        for (ParameterDeclaration param : params) {
            if (param.getInitializer() != null) {
                Object defaultValue = getJsonValue(param.getInitializer());
                param.setDefaultValue(defaultValue);
            }
            XLangASTNode name = param.getName();
            if (name instanceof Identifier)
                continue;

            Identifier paramId = Identifier.valueOf(param.getLocation(),
                    scope.generateVarName(XLangConstants.GEN_VAR_PREFIX));
            param.setName(paramId);

            name.setASTParent(null);
            VariableDeclaration decl = XLangASTBuilder.let(param.getLocation(), name, deepClone(param.getType()),
                    paramId.deepClone());

            if (decls.isEmpty())
                decls = new ArrayList<>();
            decls.add(decl);
        }
        return decls;
    }

    // 检查函数的参数名不能重名
    private void checkParamNameUnique(List<ParameterDeclaration> params) {
        Set<String> set = new HashSet<>();
        for (ParameterDeclaration param : params) {
            XLangASTNode name = param.getName();
            if (name instanceof Identifier) {
                checkParamName((Identifier) name, set);
            } else if (name instanceof ArrayBinding) {
                ArrayBinding binding = (ArrayBinding) name;
                List<ArrayElementBinding> elements = binding.getElements();
                if (elements != null) {
                    for (ArrayElementBinding element : elements) {
                        checkParamName(element.getIdentifier(), set);
                    }
                    if (binding.getRestBinding() != null) {
                        checkParamName(binding.getRestBinding().getIdentifier(), set);
                    }
                }
            } else if (name instanceof ObjectBinding) {
                ObjectBinding binding = (ObjectBinding) name;
                List<PropertyBinding> elements = binding.getProperties();
                if (elements != null) {
                    for (PropertyBinding element : elements) {
                        checkParamName(element.makeIdentifier(), set);
                    }
                    if (binding.getRestBinding() != null) {
                        checkParamName(binding.getRestBinding().getIdentifier(), set);
                    }
                }
            }
        }
    }

    private void checkParamName(Identifier name, Set<String> existingNames) {
        String paramName = name.getName();
        if (!existingNames.add(paramName))
            throw new NopEvalException(ERR_XLANG_PARAM_NAME_CONFLICTED).param(ARG_PARAM_NAME, paramName).source(name);
    }

    @Override
    public void visitForStatement(ForStatement node) {
        scope.enterLoop();
        try {
            super.visitForStatement(node);
        } finally {
            scope.leaveLoop();
        }
    }

    @Override
    public void visitForOfStatement(ForOfStatement node) {
        scope.enterLoop();
        try {
            Identifier index = node.getIndex();
            if (index != null) {
                makeVarDeclaration(index, IdentifierKind.VAR_DECL, false);
            }

            super.visitForOfStatement(node);
        } finally {
            scope.leaveLoop();
        }
    }

    @Override
    public void visitForInStatement(ForInStatement node) {
        scope.enterLoop();
        try {
            super.visitForInStatement(node);
        } finally {
            scope.leaveLoop();
        }
    }

    @Override
    public void visitForRangeStatement(ForRangeStatement node) {
        Identifier id = node.getVar();
        if (id == null) {
            id = identifier(node.getLocation(), scope.generateVarName(XLangConstants.GEN_VAR_PREFIX));
            node.setVar(id);
        }

        scope.enterLoop();

        makeVarDeclaration(id, IdentifierKind.VAR_DECL, false);
        Identifier index = node.getIndex();
        if (index != null) {
            makeVarDeclaration(index, IdentifierKind.VAR_DECL, false);
        }

        try {
            super.visitForRangeStatement(node);
        } finally {
            scope.leaveLoop();
        }
    }

    @Override
    public void visitDoWhileStatement(DoWhileStatement node) {
        scope.enterLoop();
        try {
            super.visitDoWhileStatement(node);
        } finally {
            scope.leaveLoop();
        }
    }

    @Override
    public void visitWhileStatement(WhileStatement node) {
        scope.enterLoop();
        try {
            super.visitWhileStatement(node);
        } finally {
            scope.leaveLoop();
        }
    }

    @Override
    public void visitMemberExpression(MemberExpression node) {
        Expression prop = node.getProperty();
        replaceThis(node);

        if (!node.getComputed()) {
            ((Identifier) prop).setMember(true);
        }
        super.visitMemberExpression(node);
    }

    @Override
    public void visitThisExpression(ThisExpression node) {
        Identifier id = Identifier.valueOf(node.getLocation(), "this");
        replaceNode(node, id);
        this.visitIdentifier(id);
    }

    @Override
    public void visitCallExpression(CallExpression node) {
        Expression callee = node.getCallee();
        replaceThis(node);

        if (callee.getASTKind() == XLangASTKind.Identifier) {
            ((Identifier) callee).setFunction(true);
            visit(callee);

            Identifier id = (Identifier) callee;
            if (id.getResolvedDefinition() instanceof ResolvedFuncDefinition) {
                IFunctionModel fn = ((ResolvedFuncDefinition) id.getResolvedDefinition()).getFunctionModel();
                if (fn.isMacro()) {
                    Expression ret = (Expression) fn.call2(null, scope, node, scope);
                    if (ret == node) {
                        visitChildren(node.getArguments());
                        return;
                    }

                    replaceNode(node, ret);

                    if (ret != null)
                        visit(ret);
                    return;
                }
            }
            visitChildren(node.getArguments());
            return;
        }

        super.visitCallExpression(node);
    }

    void replaceThis(MemberExpression node) {
        if (node.getObject().getASTKind() == XLangASTKind.ThisExpression) {
            Identifier id = Identifier.valueOf(node.getLocation(), "this");
            node.setObject(id);
        }
    }

    void replaceThis(CallExpression node) {
        if (node.getCallee().getASTKind() == XLangASTKind.ThisExpression) {
            Identifier id = Identifier.valueOf(node.getLocation(), "this");
            node.setCallee(id);
        }
    }

    @Override
    public void visitIdentifier(Identifier node) {
        // 如果是变量定义，则此前visitVariableDeclarator等函数中已经进行了处理，varDeclaration属性已经被初始化
        if (node.getVarDeclaration() != null) {
            // 函数在visitFunctionDeclaration中已经得到处理
            if (node.getVarDeclaration().getIdentifierKind() == IdentifierKind.FUNC_DECL)
                return;
            LOG.trace("nop.xlang.define-var:{},loc={},macro={}", node.getName(), node.getLocation(), scope.isInMacro());
            scope.addVarDeclaration(node.getVarDeclaration(), scope.isInMacro());
            return;
        }

        if (node.getResolvedDefinition() == null) {
            // 变量引用
            if (!node.isMember()) {
                XLangIdentifierDefinition varDef = resolveIdentifier(node);
                if (varDef == null) {
                    throw new NopEvalException(
                            node.isImplicit() ? ERR_XLANG_UNRESOLVED_IMPLICIT_VAR : ERR_XLANG_UNRESOLVED_IDENTIFIER)
                            .param(ARG_VAR_NAME, node.getName()).source(node);
                }

                if (node.isFunction()) {
                    if (node.getIdentifierKind() == IdentifierKind.GLOBAL_VAR_REF) {
                        throw new NopEvalException(ERR_XLANG_IDENTIFIER_NOT_FUNCTION)
                                .param(ARG_VAR_NAME, node.getName()).source(node);
                    }
                }
                node.setResolvedDefinition(varDef);
            }
        }
    }

    XLangIdentifierDefinition resolveIdentifier(Identifier identifier) {
        String name = identifier.getName();

        if (!identifier.isFunction()) {
            // 全局变量以$为前缀
            if (XLang.isGlobalVarName(name)) {
                LOG.trace("nop.xlang.resolve-global-var:name={},loc={},macro={}", name, identifier.getLocation(),
                        scope.isInMacro());
                IGlobalVariableDefinition varDef = EvalGlobalRegistry.instance().getRegisteredVariable(name);
                if (varDef == null)
                    throw new NopEvalException(ERR_XLANG_UNRESOLVED_IDENTIFIER).param(ARG_VAR_NAME, name)
                            .source(identifier);
                identifier.setIdentifierKind(IdentifierKind.GLOBAL_VAR_REF);
                return new GlobalVarDefinition(varDef);
            }
        } else {
            IFunctionModel globalFunc = EvalGlobalRegistry.instance().getRegisteredFunction(name);
            if (globalFunc != null) {
                LOG.trace("nop.xlang.resolve-global-func:name={},loc={},macro={}", name, identifier.getLocation(),
                        scope.isInMacro());
                identifier.setIdentifierKind(IdentifierKind.GLOBAL_FUNC_REF);
                return new ResolvedFuncDefinition(globalFunc);
            }
        }

        LOG.trace("nop.xlang.try-resolve-var:name={},loc={},macro={}", name, identifier.getLocation(),
                scope.isInMacro());

        XLangIdentifierDefinition resolved = scope.resolveVar(identifier.getLocation(), name, scope.isInMacro());
        if (resolved != null) {
            boolean closure = resolved instanceof ClosureRefDefinition;
            LocalVarDeclaration varDecl = closure ? ((ClosureRefDefinition) resolved).getVarDeclaration()
                    : (LocalVarDeclaration) resolved;
            varDecl.setUsed(true);

            IdentifierKind kind;
            if (closure) {
                varDecl.setUsedInClosure(true);
                kind = varDecl.getIdentifierKind().getClosureRefKind();
            } else {
                kind = varDecl.getIdentifierKind().getRefKind();
            }

            LOG.trace("nop.xlang.resolve-var:name={},kind={},loc={},macro={}", name, kind, identifier.getLocation(),
                    scope.isInMacro());
            identifier.setIdentifierKind(kind);
            return resolved;
        }

        // 先检查导入的类名。全局函数名不应该与导入的类名重名
        ImportClassDefinition def = scope.getImportedClass(name);
        if (def != null) {
            LOG.trace("nop.xlang.resolve-class:name={},class={},loc={},macro={}", name, def.getClassName(),
                    identifier.getLocation(), scope.isInMacro());
            identifier.setIdentifierKind(IdentifierKind.IMPORT_CLASS_REF);
            return def;
        }

        IFunctionModel func = scope.getRegisteredFunction(name);
        if (func != null) {
            LOG.trace("nop.xlang.resolve-scope-func:name={},loc={},macro={}", name, identifier.getLocation(),
                    scope.isInMacro());
            identifier.setIdentifierKind(IdentifierKind.SCOPE_FUNC_REF);
            return new ResolvedFuncDefinition(func);
        }

        ScopeVarDefinition var = scope.resolveScopeVarDefinition(name, scope.isInMacro());
        if (var != null) {
            LOG.trace("nop.xlang.resolve-scope-var:name={},loc={},macro={}", name, identifier.getLocation(),
                    scope.isInMacro());
            identifier.setIdentifierKind(IdentifierKind.SCOPE_VAR_REF);
            return var;
        }

        // 宏表达式所使用的所有变量都必须事先注册
        if (scope.isAllowUnregisteredScopeVar() && !scope.isInMacro()) {
            LOG.trace("nop.xlang.register-scope-var:name={},loc={},macro={}", name, identifier.getLocation(),
                    scope.isInMacro());
            var = new ScopeVarDefinition(name);
            var.setLocation(identifier.getLocation());
            // var.setAllowAssignment(true); 缺省不允许更新scope变量，避免拼写错误导致的bug。使用assign函数来更新scope变量
            scope.registerScopeVarDefinition(var, scope.isInMacro());
            identifier.setIdentifierKind(IdentifierKind.SCOPE_VAR_REF);
            return var;
        }

        return null;
    }

    @Override
    public void visitAssignmentExpression(AssignmentExpression node) {
        super.visitAssignmentExpression(node);

        Expression left = node.getLeft();
        if (left instanceof Identifier) {
            Identifier id = (Identifier) left;
            markAssignmentTarget(id, node);
        }
    }

    @Override
    public void visitUpdateExpression(UpdateExpression node) {
        super.visitUpdateExpression(node);
        Expression expr = node.getArgument();
        if (expr instanceof Identifier) {
            Identifier id = (Identifier) expr;
            markAssignmentTarget(id, node);
        }
    }

    void markAssignmentTarget(Identifier id, XLangASTNode node) {
        if (XLang.isGlobalVarName(id.getName()))
            throw new NopEvalException(ERR_XLANG_GLOBAL_VAR_NOT_ALLOW_CHANGE).param(ARG_NAME, id.getName())
                    .source(node);

        XLangIdentifierDefinition def = id.getResolvedDefinition();
        if (def == null) {
            throw new NopEvalException(ERR_XLANG_UNRESOLVED_IDENTIFIER).param(ARG_VAR_NAME, id.getName()).source(id);
        }

        if (!def.isAllowAssignment())
            throw new NopEvalException(ERR_XLANG_IDENTIFIER_NOT_ALLOW_CHANGE).param(ARG_NAME, id.getName()).source(id);

        if (def instanceof LocalVarDeclaration) {
            LocalVarDeclaration varDecl = (LocalVarDeclaration) def;
            varDecl.setChanged(true);
        } else if (def instanceof ScopeVarDefinition) {
            ScopeVarDefinition varDecl = (ScopeVarDefinition) def;
            varDecl.setChanged(true);
        } else if (def instanceof ClosureRefDefinition) {
            LocalVarDeclaration varDecl = ((ClosureRefDefinition) def).getVarDeclaration();
            varDecl.setChanged(true);
        } else {
            throw new NopEvalException(ERR_XLANG_IDENTIFIER_NOT_ALLOW_CHANGE).param(ARG_NAME, id.getName()).source(id);
        }
    }

    @Override
    public void visitParameterizedTypeNode(ParameterizedTypeNode node) {
        resolveType(node.getTypeName(), node);
        if (node.getTypeArgs() != null) {
            List<IGenericType> argTypes = new ArrayList<>(node.getTypeArgs().size());
            for (NamedTypeNode arg : node.getTypeArgs()) {
                if (arg instanceof ParameterizedTypeNode) {
                    visitParameterizedTypeNode((ParameterizedTypeNode) arg);
                } else {
                    visitTypeNameNode((TypeNameNode) arg);
                }
                argTypes.add(arg.getTypeInfo());
            }
            IGenericType type = GenericTypeHelper.buildParameterizedType(node.getTypeInfo(), argTypes);
            node.setTypeInfo(type);
        }
    }

    @Override
    public void visitTypeNameNode(TypeNameNode node) {
        String typeName = node.getTypeName();
        resolveType(typeName, node);
    }

    void resolveType(String typeName, NamedTypeNode node) {
        PredefinedGenericType type = PredefinedGenericTypes.getPredefinedType(typeName);
        if (type != null) {
            node.setTypeInfo(type);
            node.setClassModel(ReflectionManager.instance().getClassModelForType(type));
        } else if (typeName.indexOf('.') > 0) {
            if (node.getTypeInfo() != null) {
                IClassModel classModel = scope.getClassModelLoader().loadClassModel(node.getTypeInfo().getClassName());
                node.setClassModel(classModel);
            } else {
                IClassModel classModel = scope.getClassModelLoader().loadClassModel(typeName);
                node.setClassModel(classModel);
                node.setTypeInfo(classModel.getType());
            }
        } else {
            ImportClassDefinition def = scope.getImportedClass(typeName);
            if (def == null) {
                throw new NopEvalException(ERR_XLANG_UNRESOLVED_TYPE).param(ARG_TYPE_NAME, typeName);
            }
            node.setClassModel(def.getClassModel());
            node.setTypeInfo(def.getResolvedType());
        }
    }

    @Override
    public void visitTemplateStringExpression(TemplateStringExpression node) {
        node.getId().setFunction(true);
        visit(node.getId());

        XLangIdentifierDefinition resolved = node.getId().getResolvedDefinition();
        if (!(resolved instanceof ResolvedFuncDefinition)) {
            throw new NopEvalException(ERR_XLANG_TEMPLATE_EXPR_ID_MUST_BE_MACRO_FUNCTION).param(ARG_EXPR, node.getId())
                    .param(ARG_NAME, node.getId().getName());

        } else {
            IFunctionModel fn = ((ResolvedFuncDefinition) resolved).getFunctionModel();
            if (!fn.isMacro()) {
                throw new NopEvalException(ERR_XLANG_TEMPLATE_EXPR_ID_MUST_BE_MACRO_FUNCTION)
                        .param(ARG_EXPR, node.getId()).param(ARG_NAME, node.getId().getName());
            } else {
                CallExpression call = new CallExpression();
                call.setLocation(node.getLocation());
                call.setCallee(node.getId().deepClone());
                call.setArguments(Arrays.asList(node.getValue().deepClone()));
                Expression ret = (Expression) fn.call2(null, scope, call, scope);

                replaceNode(node, ret);

                if (ret != null)
                    visit(ret);
            }
        }

    }

    private void replaceNode(Expression node, Expression ret) {
        if (node.getASTParent() != null) {
            node.getASTParent().replaceChild(node, ret);
        } else {
            if (rootNode == node) {
                rootNode = ret;
            }
        }
    }

    //
    // @Override
    // public void visitXplNodeExpression(XplNodeExpression node) {
    // super.visitXplNodeExpression(node);
    //
    // XNode xNode = XNode.make(node.getTagName());
    // xNode.setLocation(node.getLocation());
    // if (node.getAttrs() != null) {
    // for (XplAttrExpression attrExpr : node.getAttrs()) {
    // Expression expr = attrExpr.getValue();
    // if (expr.getASTKind() == XLangASTKind.Literal) {
    // xNode.setAttr(attrExpr.getLocation(), attrExpr.getName(), ((Literal) expr).getValue());
    // } else {
    // xNode.setAttr(attrExpr.getLocation(), attrExpr.getName(), expr);
    // }
    // }
    // }
    //
    // Expression compiled = scope.getCompiler().parseTag(xNode, scope);
    // if (compiled == null) {
    // compiled = Literal.nullValue(node.getLocation());
    // }
    // node.getASTParent().replaceChild(node, compiled);
    // }

    @Override
    public void visitMacroExpression(MacroExpression node) {
        scope.enterMacro();
        scope.enterBlock(false);

        Expression expr;
        try {
            expr = new LexicalScopeAnalysis(scope).analyze(node.getExpr());
        } finally {
            scope.leaveBlock(false);
            scope.leaveMacro();
        }
        IExecutableExpression executable = scope.getCompiler().buildExecutable(expr, false, scope);
        Object value = executable == null ? null : XLang.execute(executable, new EvalRuntime(scope));

        if (value instanceof Expression) {
            expr = (Expression) value;
        } else {
            expr = Literal.valueOf(node.getLocation(), value);
        }
        node.getASTParent().replaceChild(node, expr);
    }

    @Override
    public void visitBreakStatement(BreakStatement node) {
        if (!scope.isInLoop())
            throw new NopEvalException(ERR_XLANG_BREAK_STATEMENT_NOT_IN_LOOP).source(node);
    }

    @Override
    public void visitContinueStatement(ContinueStatement node) {
        if (!scope.isInLoop())
            throw new NopEvalException(ERR_XLANG_CONTINUE_STATEMENT_NOT_IN_LOOP).source(node);
    }
}