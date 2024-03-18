/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.compile;

import io.nop.api.core.convert.SysConverterRegistry;
import io.nop.api.core.convert.TargetTypeConverter;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.util.Symbol;
import io.nop.commons.text.regex.RegexHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.model.query.FilterOp;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFieldModel;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.IMethodModelCollection;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.type.IGenericType;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.api.XLang;
import io.nop.xlang.ast.ArrayBinding;
import io.nop.xlang.ast.ArrayElementBinding;
import io.nop.xlang.ast.ArrayExpression;
import io.nop.xlang.ast.ArrowFunctionExpression;
import io.nop.xlang.ast.AssertOpExpression;
import io.nop.xlang.ast.AssignmentExpression;
import io.nop.xlang.ast.AwaitExpression;
import io.nop.xlang.ast.BetweenOpExpression;
import io.nop.xlang.ast.BinaryExpression;
import io.nop.xlang.ast.BlockStatement;
import io.nop.xlang.ast.BraceExpression;
import io.nop.xlang.ast.BreakStatement;
import io.nop.xlang.ast.CallExpression;
import io.nop.xlang.ast.CastExpression;
import io.nop.xlang.ast.CatchClause;
import io.nop.xlang.ast.ChainExpression;
import io.nop.xlang.ast.CollectOutputExpression;
import io.nop.xlang.ast.CompareOpExpression;
import io.nop.xlang.ast.ConcatExpression;
import io.nop.xlang.ast.ContinueStatement;
import io.nop.xlang.ast.CustomExpression;
import io.nop.xlang.ast.Declaration;
import io.nop.xlang.ast.Decorator;
import io.nop.xlang.ast.Decorators;
import io.nop.xlang.ast.DeleteStatement;
import io.nop.xlang.ast.DoWhileStatement;
import io.nop.xlang.ast.EmptyStatement;
import io.nop.xlang.ast.EscapeOutputExpression;
import io.nop.xlang.ast.ExportAllDeclaration;
import io.nop.xlang.ast.ExportDeclaration;
import io.nop.xlang.ast.ExportNamedDeclaration;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.ExpressionStatement;
import io.nop.xlang.ast.ForInStatement;
import io.nop.xlang.ast.ForOfStatement;
import io.nop.xlang.ast.ForRangeStatement;
import io.nop.xlang.ast.ForStatement;
import io.nop.xlang.ast.FunctionDeclaration;
import io.nop.xlang.ast.FunctionExpression;
import io.nop.xlang.ast.GenNodeAttrExpression;
import io.nop.xlang.ast.GenNodeExpression;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.IdentifierKind;
import io.nop.xlang.ast.IfStatement;
import io.nop.xlang.ast.ImportAsDeclaration;
import io.nop.xlang.ast.ImportDeclaration;
import io.nop.xlang.ast.InExpression;
import io.nop.xlang.ast.InstanceOfExpression;
import io.nop.xlang.ast.Literal;
import io.nop.xlang.ast.LogicalExpression;
import io.nop.xlang.ast.MacroExpression;
import io.nop.xlang.ast.MemberExpression;
import io.nop.xlang.ast.NewExpression;
import io.nop.xlang.ast.ObjectBinding;
import io.nop.xlang.ast.ObjectExpression;
import io.nop.xlang.ast.OutputXmlAttrExpression;
import io.nop.xlang.ast.OutputXmlExtAttrsExpression;
import io.nop.xlang.ast.ParameterDeclaration;
import io.nop.xlang.ast.Program;
import io.nop.xlang.ast.PropertyAssignment;
import io.nop.xlang.ast.PropertyBinding;
import io.nop.xlang.ast.QualifiedName;
import io.nop.xlang.ast.RegExpLiteral;
import io.nop.xlang.ast.RestBinding;
import io.nop.xlang.ast.ReturnStatement;
import io.nop.xlang.ast.SequenceExpression;
import io.nop.xlang.ast.SpreadElement;
import io.nop.xlang.ast.SuperExpression;
import io.nop.xlang.ast.SwitchCase;
import io.nop.xlang.ast.SwitchStatement;
import io.nop.xlang.ast.TemplateExpression;
import io.nop.xlang.ast.TemplateStringExpression;
import io.nop.xlang.ast.TemplateStringLiteral;
import io.nop.xlang.ast.TextOutputExpression;
import io.nop.xlang.ast.ThisExpression;
import io.nop.xlang.ast.ThrowStatement;
import io.nop.xlang.ast.TryStatement;
import io.nop.xlang.ast.TypeOfExpression;
import io.nop.xlang.ast.UnaryExpression;
import io.nop.xlang.ast.UpdateExpression;
import io.nop.xlang.ast.UsingStatement;
import io.nop.xlang.ast.VariableDeclaration;
import io.nop.xlang.ast.VariableDeclarator;
import io.nop.xlang.ast.WhileStatement;
import io.nop.xlang.ast.XLangASTKind;
import io.nop.xlang.ast.XLangASTNode;
import io.nop.xlang.ast.XLangASTProcessor;
import io.nop.xlang.ast.XLangIdentifierDefinition;
import io.nop.xlang.ast.XLangOperator;
import io.nop.xlang.ast.XLangOutputMode;
import io.nop.xlang.ast.definition.ClosureRefDefinition;
import io.nop.xlang.ast.definition.GlobalVarDefinition;
import io.nop.xlang.ast.definition.ImportClassDefinition;
import io.nop.xlang.ast.definition.LocalVarDeclaration;
import io.nop.xlang.ast.definition.ResolvedFuncDefinition;
import io.nop.xlang.exec.ArrayBindingAssignExecutable;
import io.nop.xlang.exec.AssertOpExecutable;
import io.nop.xlang.exec.AssignIdentifier;
import io.nop.xlang.exec.BetweenOpExecutable;
import io.nop.xlang.exec.BinaryExecutable;
import io.nop.xlang.exec.BitNotExecutable;
import io.nop.xlang.exec.BlockExecutable;
import io.nop.xlang.exec.BreakExecutable;
import io.nop.xlang.exec.BuildFuncRefExecutable;
import io.nop.xlang.exec.CallFuncExecutable;
import io.nop.xlang.exec.CallFuncWithClosureExecutable;
import io.nop.xlang.exec.CloneLiteralExecutable;
import io.nop.xlang.exec.CollectNodeExecutable;
import io.nop.xlang.exec.CollectSqlExecutable;
import io.nop.xlang.exec.CollectTextExecutable;
import io.nop.xlang.exec.CompareOpExecutable;
import io.nop.xlang.exec.ConcatExecutable;
import io.nop.xlang.exec.ContinueExecutable;
import io.nop.xlang.exec.ConvertExecutable;
import io.nop.xlang.exec.ConvertWithDefaultExecutable;
import io.nop.xlang.exec.DebugExecutable;
import io.nop.xlang.exec.DoWhileExecutable;
import io.nop.xlang.exec.EnhanceRefSlotExecutable;
import io.nop.xlang.exec.EscapeOutputExecutable;
import io.nop.xlang.exec.ExecutableFunction;
import io.nop.xlang.exec.ExecutableHelper;
import io.nop.xlang.exec.ForExecutable;
import io.nop.xlang.exec.ForInExecutable;
import io.nop.xlang.exec.ForOfExecutable;
import io.nop.xlang.exec.FunctionExecutable;
import io.nop.xlang.exec.GenNodeAttrExecutable;
import io.nop.xlang.exec.GenNodeExecutable;
import io.nop.xlang.exec.GenXJsonExecutable;
import io.nop.xlang.exec.GetAttrExecutable;
import io.nop.xlang.exec.GetPropertyExecutable;
import io.nop.xlang.exec.GlobalVarExecutable;
import io.nop.xlang.exec.GuardNotEmptyExecutable;
import io.nop.xlang.exec.GuardNotNullExecutable;
import io.nop.xlang.exec.ISeqExecutable;
import io.nop.xlang.exec.IfExecutable;
import io.nop.xlang.exec.InstanceOfExecutable;
import io.nop.xlang.exec.LazyCompiledExecutableFunction;
import io.nop.xlang.exec.ListItemExecutable;
import io.nop.xlang.exec.LiteralExecutable;
import io.nop.xlang.exec.MapItemExecutable;
import io.nop.xlang.exec.NegExecutable;
import io.nop.xlang.exec.NewListExecutable;
import io.nop.xlang.exec.NewMapExecutable;
import io.nop.xlang.exec.NewObjectExecutable;
import io.nop.xlang.exec.NotExecutable;
import io.nop.xlang.exec.NullExecutable;
import io.nop.xlang.exec.ObjFunctionExecutable;
import io.nop.xlang.exec.ObjectBindingAssignExecutable;
import io.nop.xlang.exec.OutputTextExecutable;
import io.nop.xlang.exec.OutputValueExecutable;
import io.nop.xlang.exec.OutputXmlAttrExecutable;
import io.nop.xlang.exec.OutputXmlExtAttrsExecutable;
import io.nop.xlang.exec.PropBinding;
import io.nop.xlang.exec.PropInExecutable;
import io.nop.xlang.exec.RangeExecutable;
import io.nop.xlang.exec.ReferenceAssignExecutable;
import io.nop.xlang.exec.ReferenceIdentifierExecutable;
import io.nop.xlang.exec.ReferenceSelfAssignExecutable;
import io.nop.xlang.exec.ReferenceSelfDecExecutable;
import io.nop.xlang.exec.ReferenceSelfIncExecutable;
import io.nop.xlang.exec.RenewReferenceExecutable;
import io.nop.xlang.exec.ReturnExecutable;
import io.nop.xlang.exec.ReturnScopeValuesExecutable;
import io.nop.xlang.exec.ScopeAssignExecutable;
import io.nop.xlang.exec.ScopeIdentifierExecutable;
import io.nop.xlang.exec.ScopeSelfAssignExecutable;
import io.nop.xlang.exec.ScopeSelfDecExecutable;
import io.nop.xlang.exec.ScopeSelfIncExecutable;
import io.nop.xlang.exec.SelfAssignAttrExecutable;
import io.nop.xlang.exec.SelfAssignExecutable;
import io.nop.xlang.exec.SelfAssignPropertyExecutable;
import io.nop.xlang.exec.SelfDecExecutable;
import io.nop.xlang.exec.SelfIncExecutable;
import io.nop.xlang.exec.SeqExecutable;
import io.nop.xlang.exec.SetAttrExecutable;
import io.nop.xlang.exec.SetPropertyExecutable;
import io.nop.xlang.exec.SlotAssignExecutable;
import io.nop.xlang.exec.SlotIdentifierExecutable;
import io.nop.xlang.exec.StaticFunctionExecutable;
import io.nop.xlang.exec.StaticGetterGetPropertyExecutable;
import io.nop.xlang.exec.SwitchExecutable;
import io.nop.xlang.exec.ThrowExceptionExecutable;
import io.nop.xlang.exec.TypeOfExecutable;
import io.nop.xlang.exec.VarFunctionExecutable;
import io.nop.xlang.exec.WhileExecutable;
import io.nop.xlang.scope.LexicalScope;
import io.nop.xlang.xpl.output.OutputParseHelper;
import io.nop.xlang.xpl.xlib.XplLibTagCompiler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static io.nop.xlang.XLangErrors.ARG_ARG_COUNT;
import static io.nop.xlang.XLangErrors.ARG_AST_NODE;
import static io.nop.xlang.XLangErrors.ARG_CLASS_NAME;
import static io.nop.xlang.XLangErrors.ARG_EXPR;
import static io.nop.xlang.XLangErrors.ARG_FUNC_NAME;
import static io.nop.xlang.XLangErrors.ARG_IDENTIFIER_KIND;
import static io.nop.xlang.XLangErrors.ARG_MAX_COUNT;
import static io.nop.xlang.XLangErrors.ARG_METHOD_NAME;
import static io.nop.xlang.XLangErrors.ARG_MIN_COUNT;
import static io.nop.xlang.XLangErrors.ARG_OP;
import static io.nop.xlang.XLangErrors.ARG_PROP_NAME;
import static io.nop.xlang.XLangErrors.ARG_VAR_NAME;
import static io.nop.xlang.XLangErrors.ERR_EXEC_CLASS_NOT_ALLOW_ATTR_EXPR;
import static io.nop.xlang.XLangErrors.ERR_EXEC_CLASS_NO_CONSTRUCTOR;
import static io.nop.xlang.XLangErrors.ERR_EXEC_CLASS_NO_STATIC_FIELD;
import static io.nop.xlang.XLangErrors.ERR_EXEC_CLASS_NO_STATIC_METHOD;
import static io.nop.xlang.XLangErrors.ERR_EXEC_CONVERT_FUNC_ONLY_ALLOW_ONE_ARG;
import static io.nop.xlang.XLangErrors.ERR_EXEC_NOT_SUPPORTED_AST_NODE;
import static io.nop.xlang.XLangErrors.ERR_EXEC_PROGRAM_SHOULD_NOT_USE_EXTERNAL_CLOSURE_VAR;
import static io.nop.xlang.XLangErrors.ERR_EXEC_TOO_FEW_ARGS;
import static io.nop.xlang.XLangErrors.ERR_EXEC_TOO_MANY_ARGS;
import static io.nop.xlang.XLangErrors.ERR_XPL_UNKNOWN_FILTER_OP;

/**
 * 将抽象语法树转换为可执行对象
 */
public class BuildExecutableProcessor extends XLangASTProcessor<IExecutableExpression, IXLangCompileScope> {

    protected Map<Symbol, FuncData> executableFuncs = new IdentityHashMap<>();

    protected static class FuncData {
        final ExecutableFunction fn;
        List<Consumer<IExecutableExpression>> lazyBodyConsumers;

        public FuncData(ExecutableFunction fn) {
            this.fn = fn;
        }

        public ExecutableFunction getFunction() {
            return fn;
        }

        public void setBody(IExecutableExpression body) {
            fn.setBody(body);
            if (lazyBodyConsumers != null) {
                for (Consumer<IExecutableExpression> consumer : lazyBodyConsumers) {
                    consumer.accept(body);
                }
            }
        }

        public void addLazyConsumer(Consumer<IExecutableExpression> consumer) {
            if (lazyBodyConsumers == null) {
                lazyBodyConsumers = new ArrayList<>();
            }
            lazyBodyConsumers.add(consumer);
        }
    }

    @Override
    public IExecutableExpression defaultProcess(XLangASTNode node, IXLangCompileScope context) {
        throw new NopEvalException(ERR_EXEC_NOT_SUPPORTED_AST_NODE).param(ARG_AST_NODE, node);
    }

    @Override
    public IExecutableExpression processProgram(Program node, IXLangCompileScope context) {
        IExecutableExpression expr = buildSeq(node.getBody(), context);
        if (expr == null)
            return null;

        if (!node.hasLexicalScope())
            return expr;

        LexicalScope fnScope = node.getLexicalScope();

        if (fnScope.getClosureCount() > 0)
            throw new NopEvalException(ERR_EXEC_PROGRAM_SHOULD_NOT_USE_EXTERNAL_CLOSURE_VAR).source(node)
                    .param(ARG_VAR_NAME, fnScope.getAllClosureVars().get(0).getToken());

        if (node.isMacroScript()) {
            ReturnScopeValuesExecutable executable = buildReturnScopeValues(node);
            expr = SeqExecutable.valueOf(null, new IExecutableExpression[]{expr, executable});
        }
        CallFuncExecutable func = new CallFuncExecutable(node.getLocation(),
                context.generateVarName(XLangConstants.GEN_FUNC_PREFIX), fnScope.getSlotNames(),
                IExecutableExpression.EMPTY_EXPRS, expr);
        func.setAllowBreakpoint(false);
        return func;
    }

    private ReturnScopeValuesExecutable buildReturnScopeValues(Program node) {
        List<LocalVarDeclaration> decls = collectRootIdentifiers(node);
        List<IExecutableExpression> exprs = new ArrayList<>(decls.size());

        for (LocalVarDeclaration decl : decls) {
            if (decl.isFuncDecl()) {
                ExecutableFunction fn = executableFuncs.get(decl.getToken()).fn;
                LexicalScope fnScope = decl.getFunctionScope();
                if (fnScope.getClosureCount() > 0) {
                    exprs.add(BuildFuncRefExecutable.build(node.getLocation(), fn,
                            node.getLexicalScope().getClosureSlots(fnScope.getAllClosureVars()),
                            fnScope.getClosureTargetSlots()));
                } else {
                    exprs.add(FunctionExecutable.build(decl.getLocation(), decl.getIdentifierName(), fn,
                            IExecutableExpression.EMPTY_EXPRS));
                }
            } else if (decl.isUseRef()) {
                exprs.add(new ReferenceIdentifierExecutable(decl.getLocation(), decl.getIdentifierName(),
                        decl.getVarSlot()));
            } else {
                exprs.add(
                        new SlotIdentifierExecutable(decl.getLocation(), decl.getIdentifierName(), decl.getVarSlot()));
            }
        }

        return new ReturnScopeValuesExecutable(null, decls, exprs);
    }

    // 收集所有变量声明和函数声明
    private List<LocalVarDeclaration> collectRootIdentifiers(Program node) {
        List<LocalVarDeclaration> ret = new ArrayList<>();
        for (XLangASTNode child : node.getBody()) {
            if (child.getASTKind() == XLangASTKind.ExportDeclaration) {
                child = ((ExportDeclaration) child).getDeclaration();
            }
            switch (child.getASTKind()) {
                case VariableDeclaration: {
                    VariableDeclaration decl = (VariableDeclaration) child;
                    ret.addAll(decl.getIdentifiers());
                    break;
                }
                case FunctionDeclaration: {
                    FunctionDeclaration decl = (FunctionDeclaration) child;
                    ret.add(decl.getName().getVarDeclaration());
                    break;
                }
                default:
            }
        }
        return ret;
    }

    protected IExecutableExpression buildBlock(List<? extends XLangASTNode> list, IXLangCompileScope scope) {
        List<IExecutableExpression> exprs = buildExprs(list, scope);
        if (exprs == null || exprs.isEmpty())
            return null;

        return BlockExecutable.valueOf(null, toArray(exprs));
    }

    protected List<IExecutableExpression> buildExprs(List<? extends XLangASTNode> list, IXLangCompileScope scope) {
        if (list == null)
            return null;

        List<IExecutableExpression> exprs = new ArrayList<>(list.size());
        // 先编译函数定义
        for (XLangASTNode child : list) {
            child = getNormalOrExported(child);

            if (child.getASTKind() == XLangASTKind.FunctionDeclaration) {
                FunctionDeclaration decl = (FunctionDeclaration) child;
                ExecutableFunction fn = compileFuncDecl(decl, scope);
                executableFuncs.put(decl.getName().getToken(), new FuncData(fn));
            }
        }

        for (XLangASTNode child : list) {
            child = getNormalOrExported(child);

            if (child.getASTKind() == XLangASTKind.FunctionDeclaration) {
                FunctionDeclaration decl = (FunctionDeclaration) child;
                IExecutableExpression body = buildFunctionBody(decl, scope);
                FuncData fn = executableFuncs.get(decl.getName().getToken());
                fn.setBody(body);
                continue;
            }

            IExecutableExpression expr = this.processAST(child, scope);
            if (expr != null) {
                if (expr instanceof ISeqExecutable) {
                    for (IExecutableExpression subExpr : ((ISeqExecutable) expr).getExprs()) {
                        exprs.add(subExpr);
                    }
                } else {
                    exprs.add(expr);
                }
            }
        }
        if (exprs.isEmpty())
            return null;
        return exprs;
    }

    protected XLangASTNode getNormalOrExported(XLangASTNode node) {
        if (node.getASTKind() == XLangASTKind.ExportDeclaration) {
            ExportDeclaration decl = (ExportDeclaration) node;
            return decl.getDeclaration();
        }
        return node;
    }

    private IExecutableExpression buildSeq(List<? extends XLangASTNode> list, IXLangCompileScope scope) {
        List<IExecutableExpression> exprs = buildExprs(list, scope);
        if (exprs == null)
            return null;

        if (exprs.isEmpty())
            return NullExecutable.NULL;

        if (exprs.size() == 1)
            return exprs.get(0);

        return SeqExecutable.valueOf(null, toArray(exprs));
    }

    private IExecutableExpression[] toArray(List<IExecutableExpression> list) {
        return list.toArray(new IExecutableExpression[list.size()]);
    }

    private MapItemExecutable[] mapItemsToArray(List<MapItemExecutable> list) {
        return list.toArray(new MapItemExecutable[list.size()]);
    }

    private ListItemExecutable[] listItemsToArray(List<ListItemExecutable> list) {
        return list.toArray(new ListItemExecutable[list.size()]);
    }

    @Override
    public IExecutableExpression processIdentifier(Identifier node, IXLangCompileScope context) {
        XLangIdentifierDefinition def = node.getResolvedDefinition();
        switch (node.getIdentifierKind()) {
            case GLOBAL_VAR_REF:
                return new GlobalVarExecutable(node.getLocation(), node.getName(),
                        ((GlobalVarDefinition) def).getVarDefinition());
            case SCOPE_VAR_REF:
                return new ScopeIdentifierExecutable(node.getLocation(), node.getName());
            case VAR_REF:
            case CLOSURE_VAR_REF: {
                if (def.isUseRef()) {
                    // 如果闭包变量有可能被修改，则按照引用传递
                    return new ReferenceIdentifierExecutable(node.getLocation(), node.getName(), def.getVarSlot());
                } else {
                    LocalVarDeclaration var;
                    if (node.getIdentifierKind() == IdentifierKind.VAR_REF) {
                        var = (LocalVarDeclaration) def;
                    } else {
                        var = ((ClosureRefDefinition) def).getVarDeclaration();
                    }
                    // 如果是可以内联的常量，则直接返回常量值
                    if (var.isInlineVar()) {
                        return LiteralExecutable.build(node.getLocation(), var.getConstValue().getValue());
                    }
                    return new SlotIdentifierExecutable(node.getLocation(), node.getName(), def.getVarSlot());
                }
            }
            case CLOSURE_FUNC_REF: {
                ClosureRefDefinition closureRef = (ClosureRefDefinition) def;
                LocalVarDeclaration fnVar = closureRef.getVarDeclaration();
                LexicalScope fnScope = fnVar.getFunctionScope();
                ExecutableFunction exec = executableFuncs.get(fnVar.getToken()).fn;
                if (fnScope.getClosureCount() <= 0) {
                    return LiteralExecutable.build(node.getLocation(), exec);
                } else {
                    /**
                     * 例如 function f(){ return g(); }
                     *
                     * function g(){ return x; } 在函数f内部发现调用了闭包函数g时，需要从f的closureSlot中获取值，作为g的closureSlot传递。
                     * f的allClosureVars中包含了自身使用的闭包变量以及所有闭包函数所使用到的闭包变量
                     */
                    LexicalScope currentScope = node.getLexicalScope();
                    return BuildFuncRefExecutable.build(node.getLocation(), exec,
                            currentScope.getClosureSlots(fnScope.getAllClosureVars()), fnScope.getClosureTargetSlots());
                }
            }
            case FUNC_REF: {
                LocalVarDeclaration var = (LocalVarDeclaration) node.getResolvedDefinition();
                ExecutableFunction exec = executableFuncs.get(var.getToken()).fn;
                LexicalScope fnScope = var.getFunctionScope();
                if (fnScope.getClosureCount() <= 0) {
                    return LiteralExecutable.build(node.getLocation(), exec);
                } else {
                    LexicalScope currentScope = node.getLexicalScope();
                    return BuildFuncRefExecutable.build(node.getLocation(), exec,
                            currentScope.getClosureSlots(fnScope.getAllClosureVars()), fnScope.getClosureTargetSlots());
                }
            }
            case IMPORT_CLASS_REF: {
                IClassModel classModel = getRefClass(node);
                return LiteralExecutable.build(node.getLocation(), classModel.getRawClass());
            }
            default:
        }
        throw new NopEvalException(ERR_EXEC_NOT_SUPPORTED_AST_NODE).param(ARG_AST_NODE, node).param(ARG_IDENTIFIER_KIND,
                node.getIdentifierKind());
    }

    @Override
    public IExecutableExpression processLiteral(Literal node, IXLangCompileScope context) {
        return LiteralExecutable.build(node.getLocation(), node.getValue());
    }

    @Override
    public IExecutableExpression processTemplateStringLiteral(TemplateStringLiteral node, IXLangCompileScope context) {
        return LiteralExecutable.build(node.getLocation(), node.getValue());
    }

    @Override
    public IExecutableExpression processRegExpLiteral(RegExpLiteral node, IXLangCompileScope context) {
        return LiteralExecutable.build(node.getLocation(), RegexHelper.compileRegex(node.getPattern()));
    }

    @Override
    public IExecutableExpression processBlockStatement(BlockStatement node, IXLangCompileScope context) {
        return buildBlock(node.getBody(), context);
    }

    @Override
    public IExecutableExpression processEmptyStatement(EmptyStatement node, IXLangCompileScope context) {
        return null;
    }

    @Override
    public IExecutableExpression processReturnStatement(ReturnStatement node, IXLangCompileScope context) {
        IExecutableExpression value = buildValueExpr(node.getArgument(), context);
        return new ReturnExecutable(node.getLocation(), value);
    }

    @Override
    public IExecutableExpression processBreakStatement(BreakStatement node, IXLangCompileScope context) {
        return new BreakExecutable(node.getLocation());
    }

    @Override
    public IExecutableExpression processContinueStatement(ContinueStatement node, IXLangCompileScope context) {
        return new ContinueExecutable(node.getLocation());
    }

    @Override
    public IExecutableExpression processIfStatement(IfStatement node, IXLangCompileScope context) {
        IExecutableExpression testExpr = buildValueExpr(node.getTest(), context);
        IExecutableExpression consequentExpr = processNotNullAST(node.getConsequent(), context);
        IExecutableExpression alternateExpr = processAST(node.getAlternate(), context);
        return new IfExecutable(node.getLocation(), testExpr, consequentExpr, alternateExpr);
    }

    @Override
    public IExecutableExpression processSwitchStatement(SwitchStatement node, IXLangCompileScope context) {
        IExecutableExpression discriminant = buildValueExpr(node.getDiscriminant(), context);
        int n = node.getCases().size();
        IExecutableExpression[] tests = new IExecutableExpression[n];
        IExecutableExpression[] consequences = new IExecutableExpression[n];
        boolean[] fallthroughs = new boolean[n];

        for (int i = 0; i < n; i++) {
            SwitchCase caseExpr = node.getCases().get(i);
            fallthroughs[i] = caseExpr.getFallthrough();
            tests[i] = buildValueExpr(caseExpr.getTest(), context);
            consequences[i] = processNotNullAST(caseExpr.getConsequent(), context);
        }

        IExecutableExpression defaultCase = null;
        if (node.getDefaultCase() != null) {
            defaultCase = processAST(node.getDefaultCase(), context);
        }
        return new SwitchExecutable(node.getLocation(), node.getAsExpr(),
                discriminant, tests, consequences, fallthroughs, defaultCase);
    }

    @Override
    public IExecutableExpression processThrowStatement(ThrowStatement node, IXLangCompileScope context) {
        IExecutableExpression valueExpr = processNotNullAST(node.getArgument(), context);
        return new ThrowExceptionExecutable(node.getLocation(), valueExpr);
    }

    @Override
    public IExecutableExpression processTryStatement(TryStatement node, IXLangCompileScope context) {
        return super.processTryStatement(node, context);
    }

    @Override
    public IExecutableExpression processCatchClause(CatchClause node, IXLangCompileScope context) {
        return super.processCatchClause(node, context);
    }

    @Override
    public IExecutableExpression processWhileStatement(WhileStatement node, IXLangCompileScope context) {
        IExecutableExpression testExpr = buildValueExpr(node.getTest(), context);
        IExecutableExpression bodyExpr = processNotNullAST(node.getBody(), context);
        return WhileExecutable.valueOf(node.getLocation(), testExpr, bodyExpr);
    }

    @Override
    public IExecutableExpression processDoWhileStatement(DoWhileStatement node, IXLangCompileScope context) {
        IExecutableExpression testExpr = buildValueExpr(node.getTest(), context);
        IExecutableExpression bodyExpr = processNotNullAST(node.getBody(), context);
        return new DoWhileExecutable(node.getLocation(), testExpr, bodyExpr);
    }

    @Override
    public IExecutableExpression processVariableDeclarator(VariableDeclarator node, IXLangCompileScope context) {
        IExecutableExpression expr = processNotNullAST(node.getInit(), context);

        switch (node.getId().getASTKind()) {
            case Identifier:
                return buildIdentifierAssign(node, (Identifier) node.getId(), XLangOperator.ASSIGN, expr);
            case ArrayBinding:
                return buildArrayBindingAssign(node, (ArrayBinding) node.getId(), expr, context);
            case ObjectBinding:
                return buildObjectBindingAssign(node, (ObjectBinding) node.getId(), expr, context);
            default:
                throw new NopEvalException(ERR_EXEC_NOT_SUPPORTED_AST_NODE).param(ARG_AST_NODE, node.getId());
        }
    }

    @Override
    public IExecutableExpression processVariableDeclaration(VariableDeclaration node, IXLangCompileScope context) {
        return buildBlock(node.getDeclarators(), context);
    }

    @Override
    public IExecutableExpression processForStatement(ForStatement node, IXLangCompileScope context) {
        IExecutableExpression initExpr = processAST(node.getInit(), context);
        IExecutableExpression testExpr = buildValueExpr(node.getTest(), context);
        IExecutableExpression updateExpr = processAST(node.getUpdate(), context);
        IExecutableExpression bodyExpr = processNotNullAST(node.getBody(), context);

        List<LocalVarDeclaration> vars = getRefDeclVars(node.getInit());
        if (vars != null) {
            List<IExecutableExpression> renewExprs = vars.stream().map(
                            var -> new RenewReferenceExecutable(var.getLocation(), var.getIdentifierName(), var.getVarSlot()))
                    .collect(Collectors.toList());
            bodyExpr = ExecutableHelper.prepend(renewExprs, bodyExpr);
        }

        return ForExecutable.valueOf(node.getLocation(), initExpr, testExpr, updateExpr, bodyExpr);
    }

    private List<LocalVarDeclaration> getRefDeclVars(Expression expr) {
        if (expr == null)
            return null;
        if (expr.getASTKind() != XLangASTKind.VariableDeclaration) {
            return null;
        }

        List<LocalVarDeclaration> ret = new ArrayList<>();
        VariableDeclaration decl = (VariableDeclaration) expr;
        List<VariableDeclarator> vars = decl.getDeclarators();
        for (VariableDeclarator var : vars) {
            XLangASTKind kind = var.getId().getASTKind();
            switch (kind) {
                case Identifier: {
                    Identifier id = (Identifier) var.getId();
                    addRefVar(id.getVarDeclaration(), ret);
                    break;
                }
                case ArrayBinding: {
                    collectArrayBindingVar((ArrayBinding) var.getId(), ret);
                    break;
                }
                case ObjectBinding: {
                    collectObjectBindingVar((ObjectBinding) var.getId(), ret);
                    break;
                }
            }
        }
        return ret;
    }

    private void collectArrayBindingVar(ArrayBinding node, List<LocalVarDeclaration> decls) {
        if (node.getElements() != null) {
            for (ArrayElementBinding elm : node.getElements()) {
                addRefVar(elm.getIdentifier().getVarDeclaration(), decls);
            }
        }
        if (node.getRestBinding() != null) {
            addRefVar(node.getRestBinding().getIdentifier().getVarDeclaration(), decls);
        }
    }

    private void collectObjectBindingVar(ObjectBinding node, List<LocalVarDeclaration> decls) {
        if (node.getProperties() != null) {
            for (PropertyBinding prop : node.getProperties()) {
                addRefVar(prop.getIdentifier().getVarDeclaration(), decls);
            }
        }
        if (node.getRestBinding() != null) {
            addRefVar(node.getRestBinding().getIdentifier().getVarDeclaration(), decls);
        }
    }

    private void addRefVar(LocalVarDeclaration var, List<LocalVarDeclaration> decls) {
        if (var.isUseRef())
            decls.add(var);
    }

    @Override
    public IExecutableExpression processForOfStatement(ForOfStatement node, IXLangCompileScope context) {
        IExecutableExpression itemsExpr = buildValueExpr(node.getRight(), context);
        int indexSlot = node.getIndex() != null ? node.getIndex().getVarDeclaration().getVarSlot() : -1;
        Identifier var = getIdentifier(node.getLeft());
        IExecutableExpression bodyExpr = processNotNullAST(node.getBody(), context);

        XLangIdentifierDefinition resolved = var.getResolvedDefinition();
        if (resolved == null)
            resolved = var.getVarDeclaration();
        return ForOfExecutable.valueOf(node.getLocation(), resolved.getVarSlot(), resolved.isUseRef(), indexSlot,
                itemsExpr, bodyExpr);
    }

    Identifier getIdentifier(Expression expr) {
        if (expr instanceof Identifier)
            return ((Identifier) expr);
        if (expr instanceof VariableDeclaration) {
            VariableDeclarator decl = ((VariableDeclaration) expr).getDeclarators().get(0);
            return (Identifier) decl.getId();
        }
        return null;
    }

    @Override
    public IExecutableExpression processForInStatement(ForInStatement node, IXLangCompileScope context) {
        IExecutableExpression itemsExpr = buildValueExpr(node.getRight(), context);
        Identifier var = getIdentifier(node.getLeft());
        IExecutableExpression bodyExpr = processNotNullAST(node.getBody(), context);
        return ForInExecutable.valueOf(node.getLocation(), var.getVarDeclaration().getVarSlot(), itemsExpr, bodyExpr);
    }

    @Override
    public IExecutableExpression processForRangeStatement(ForRangeStatement node, IXLangCompileScope context) {
        Identifier var = getIdentifier(node.getVar());
        IExecutableExpression beginExpr = buildValueExpr(node.getBegin(), context);
        IExecutableExpression endExpr = buildValueExpr(node.getEnd(), context);
        IExecutableExpression stepExpr = buildValueExpr(node.getStep(), context);
        IExecutableExpression bodyExpr = processNotNullAST(node.getBody(), context);

        IExecutableExpression itemsExpr = new RangeExecutable(node.getLocation(), beginExpr, endExpr, stepExpr);
        int indexSlot = node.getIndex() != null ? node.getIndex().getVarDeclaration().getVarSlot() : -1;

        XLangIdentifierDefinition resolved = var.getResolvedDefinition();
        if (resolved == null)
            resolved = var.getVarDeclaration();
        return ForOfExecutable.valueOf(node.getLocation(), resolved.getVarSlot(), resolved.isUseRef(), indexSlot,
                itemsExpr, bodyExpr);
    }

    @Override
    public IExecutableExpression processDeleteStatement(DeleteStatement node, IXLangCompileScope context) {
        return super.processDeleteStatement(node, context);
    }

    @Override
    public IExecutableExpression processChainExpression(ChainExpression node, IXLangCompileScope context) {
        IExecutableExpression expr = processNotNullAST(node.getExpr(), context);
        if (node.getNotEmpty())
            return new GuardNotEmptyExecutable(node.getLocation(), expr, node.getTarget());
        return new GuardNotNullExecutable(node.getLocation(), expr);
    }

    protected IExecutableExpression processNotNullAST(XLangASTNode node, IXLangCompileScope context) {
        if (node == null)
            return NullExecutable.NULL;
        IExecutableExpression expr = processAST(node, context);
        if (expr == null)
            expr = NullExecutable.NULL;
        return expr;
    }

    private IExecutableExpression notNull(IExecutableExpression expr) {
        if (expr == null)
            return NullExecutable.NULL;
        return expr;
    }

    @Override
    public IExecutableExpression processThisExpression(ThisExpression node, IXLangCompileScope context) {
        return super.processThisExpression(node, context);
    }

    @Override
    public IExecutableExpression processSuperExpression(SuperExpression node, IXLangCompileScope context) {
        return super.processSuperExpression(node, context);
    }

    @Override
    public IExecutableExpression processTemplateStringExpression(TemplateStringExpression node,
                                                                 IXLangCompileScope context) {
        return super.processTemplateStringExpression(node, context);
    }

    @Override
    public IExecutableExpression processArrayExpression(ArrayExpression node, IXLangCompileScope context) {
        List<XLangASTNode> elements = node.getElements();
        List<ListItemExecutable> executables = new ArrayList<>(elements.size());
        for (XLangASTNode element : elements) {
            if (element.getASTKind() == XLangASTKind.SpreadElement) {
                IExecutableExpression expr = buildValueExpr(((SpreadElement) element).getArgument(), context);
                executables.add(new ListItemExecutable(true, expr));
            } else {
                IExecutableExpression valueExpr = buildValueExpr(element, context);
                executables.add(new ListItemExecutable(false, valueExpr));
            }
        }
        return new NewListExecutable(node.getLocation(), listItemsToArray(executables));
    }

    @Override
    public IExecutableExpression processObjectExpression(ObjectExpression node, IXLangCompileScope context) {
        List<XLangASTNode> props = node.getProperties();
        if (props == null) {
            return new NewMapExecutable(node.getLocation(), new MapItemExecutable[0]);
        }
        List<MapItemExecutable> executables = new ArrayList<>(props.size());
        for (XLangASTNode prop : props) {
            if (prop.getASTKind() == XLangASTKind.SpreadElement) {
                IExecutableExpression expr = buildValueExpr(((SpreadElement) prop).getArgument(), context);
                executables.add(new MapItemExecutable(null, expr, true));
            } else {
                PropertyAssignment assign = (PropertyAssignment) prop;
                IExecutableExpression keyExpr = buildValueExpr(assign.getKey(), context);
                IExecutableExpression valueExpr = buildValueExpr(assign.getValue(), context);
                executables.add(new MapItemExecutable(keyExpr, valueExpr, false));
            }
        }
        return new NewMapExecutable(node.getLocation(), mapItemsToArray(executables));
    }

    @Override
    public IExecutableExpression processParameterDeclaration(ParameterDeclaration node, IXLangCompileScope context) {
        return super.processParameterDeclaration(node, context);
    }

    protected ExecutableFunction compileFuncDecl(FunctionDeclaration node, IXLangCompileScope context) {
        String funcName = node.getFuncName();
        int argCount = node.getArgCount();
        String[] slotNames = node.getSlotNames();
        int demandArgCount = node.getDemandArgCount();
        IExecutableExpression[] defaultArgValues = getDefaultArgValues(node.getParams(), demandArgCount, argCount);

        // 函数嵌套调用时会出现循环依赖的情况
        ExecutableFunction fn = new ExecutableFunction(node.getLocation(), node.getLocation(), funcName, argCount,
                demandArgCount, slotNames, defaultArgValues, null);
        return fn;
    }

    private IExecutableExpression[] getDefaultArgValues(List<ParameterDeclaration> params, int demandArgCount,
                                                        int argCount) {
        if (demandArgCount >= argCount) {
            return IExecutableExpression.EMPTY_EXPRS;
        } else {
            IExecutableExpression[] ret = new IExecutableExpression[argCount - demandArgCount];
            for (int i = demandArgCount; i < argCount; i++) {
                ParameterDeclaration param = params.get(i);
                Object value = param.getDefaultValue();
                if (isCompositeValue(value)) {
                    ret[i] = CloneLiteralExecutable.build(param.getInitializer().getLocation(), value);
                } else {
                    SourceLocation loc = param.getInitializer() == null ? null : param.getInitializer().getLocation();
                    ret[i - demandArgCount] = LiteralExecutable.build(loc, value);
                }
            }
            return ret;
        }
    }

    private boolean isCompositeValue(Object value) {
        return value instanceof Collection || value instanceof Map;
    }

    @Override
    public IExecutableExpression processArrowFunctionExpression(ArrowFunctionExpression node,
                                                                IXLangCompileScope context) {
        ExecutableFunction func = buildLambdaFunction(node, context);
        if (node.getClosureVarCount() > 0) {
            LexicalScope parentScope = node.getASTParent().getLexicalScope();
            LexicalScope fnScope = node.getLexicalScope();
            int[] targetSlots = fnScope.getClosureTargetSlots();
            return new BuildFuncRefExecutable(node.getLocation(), func,
                    parentScope.getClosureSlots(fnScope.getAllClosureVars()), targetSlots);
        }
        return LiteralExecutable.build(node.getLocation(), func);
    }

    private ExecutableFunction buildLambdaFunction(ArrowFunctionExpression node, IXLangCompileScope context) {
        String funcName = node.getFuncName();
        if (funcName == null)
            funcName = context.generateVarName(XLangConstants.GEN_FUNC_PREFIX);
        int argCount = node.getArgCount();
        String[] slotNames = node.getSlotNames();
        int demandArgCount = node.getDemandArgCount();
        IExecutableExpression[] defaultArgValues = getDefaultArgValues(node.getParams(), demandArgCount, argCount);

        IExecutableExpression expr = buildFunctionBody(node, context);
        return new ExecutableFunction(node.getLocation(), node.getLocation(), funcName, argCount, demandArgCount,
                slotNames, defaultArgValues, expr);
    }

    protected IExecutableExpression buildFunctionBody(FunctionExpression node, IXLangCompileScope context) {
        List<IExecutableExpression> exprs = null;
        if (node.getParams() != null) {
            for (ParameterDeclaration param : node.getParams()) {
                // LexicalScopeAnalysis中会规范化参数格式，ObjectBinding等参数形式会被一个临时生成的参数名取代
                Identifier id = (Identifier) param.getName();
                if (id.getVarDeclaration().isUseRef()) {
                    if (exprs == null) {
                        exprs = new ArrayList<>();
                    }
                    exprs.add(new EnhanceRefSlotExecutable(id.getLocation(), id.getName(),
                            id.getVarDeclaration().getVarSlot()));
                }
            }
        }
        IExecutableExpression expr = processNotNullAST(node.getBody(), context);
        return ExecutableHelper.append(exprs, expr);
    }

    @Override
    public IExecutableExpression processUnaryExpression(UnaryExpression node, IXLangCompileScope context) {
        IExecutableExpression expr = processNotNullAST(node.getArgument(), context);

        switch (node.getOperator()) {
            case BIT_NOT:
                return new BitNotExecutable(node.getLocation(), expr);
            case NOT:
                return new NotExecutable(node.getLocation(), expr);
            case MINUS:
                return new NegExecutable(node.getLocation(), expr);
        }
        throw new NopEvalException(ERR_EXEC_NOT_SUPPORTED_AST_NODE).param(ARG_AST_NODE, node);
    }

    @Override
    public IExecutableExpression processUpdateExpression(UpdateExpression node, IXLangCompileScope context) {
        if (node.getPrefix()) {
            return null;
        } else {
            Expression arg = node.getArgument();
            switch (arg.getASTKind()) {
                case Identifier:
                    Identifier id = (Identifier) arg;
                    XLangIdentifierDefinition resolved = id.getResolvedDefinition();
                    if (id.getIdentifierKind() == IdentifierKind.SCOPE_VAR_REF) {
                        if (node.getOperator() == XLangOperator.SELF_INC) {
                            return new ScopeSelfIncExecutable(node.getLocation(), id.getName());
                        }
                        return new ScopeSelfDecExecutable(node.getLocation(), id.getName());
                    }
                    if (resolved.isUseRef()) {
                        if (node.getOperator() == XLangOperator.SELF_INC) {
                            return new ReferenceSelfIncExecutable(node.getLocation(), id.getName(), resolved.getVarSlot());
                        }
                        return new ReferenceSelfDecExecutable(node.getLocation(), id.getName(), resolved.getVarSlot());
                    }
                    if (node.getOperator() == XLangOperator.SELF_INC) {
                        return new SelfIncExecutable(node.getLocation(), id.getName(), resolved.getVarSlot());
                    }
                    return new SelfDecExecutable(node.getLocation(), id.getName(), resolved.getVarSlot());
                case MemberExpression: {
                    XLangOperator op = node.getOperator() == XLangOperator.SELF_INC ? XLangOperator.SELF_ASSIGN_ADD
                            : XLangOperator.SELF_ASSIGN_MINUS;
                    return buildMemberAssign(node, (MemberExpression) arg, op,
                            LiteralExecutable.build(node.getLocation(), 1), context);
                }
                default:
                    return defaultProcess(node, context);
            }
        }
    }

    @Override
    public IExecutableExpression processBinaryExpression(BinaryExpression node, IXLangCompileScope context) {
        IExecutableExpression leftExpr = buildValueExpr(node.getLeft(), context);
        IExecutableExpression rightExpr = buildValueExpr(node.getRight(), context);
        return BinaryExecutable.valueOf(node.getLocation(), node.getOperator(), leftExpr, rightExpr);
    }

    @Override
    public IExecutableExpression processInExpression(InExpression node, IXLangCompileScope context) {
        IExecutableExpression leftExpr = buildValueExpr(node.getLeft(), context);
        IExecutableExpression rightExpr = buildValueExpr(node.getRight(), context);
        return new PropInExecutable(node.getLocation(), leftExpr, rightExpr);
    }

    @Override
    public IExecutableExpression processExpressionStatement(ExpressionStatement node, IXLangCompileScope context) {
        return processAST(node.getExpression(), context);
    }

    @Override
    public IExecutableExpression processAssignmentExpression(AssignmentExpression node, IXLangCompileScope context) {
        IExecutableExpression expr = buildValueExpr(node.getRight(), context);
        Expression left = node.getLeft();
        switch (left.getASTKind()) {
            case Identifier: {
                return buildIdentifierAssign(node, (Identifier) left, node.getOperator(), expr);
            }
            case MemberExpression:
                return buildMemberAssign(node, (MemberExpression) left, node.getOperator(), expr, context);
            default:
                throw new NopEvalException(ERR_EXEC_NOT_SUPPORTED_AST_NODE).param(ARG_AST_NODE, left);
        }
    }

    private IExecutableExpression buildIdentifierAssign(XLangASTNode node, Identifier left, XLangOperator operator,
                                                        IExecutableExpression expr) {
        String name = left.getName();
        XLangIdentifierDefinition id = left.getDefinition();
        if (operator == XLangOperator.ASSIGN) {
            if (id.getVarSlot() >= 0) {
                if (id.isUseRef())
                    return new ReferenceAssignExecutable(node.getLocation(), name, id.getVarSlot(), notNull(expr));
                return new SlotAssignExecutable(node.getLocation(), name, id.getVarSlot(), notNull(expr));
            } else {
                return new ScopeAssignExecutable(node.getLocation(), name, notNull(expr));
            }
        } else {
            if (id.getVarSlot() >= 0) {
                if (id.isUseRef())
                    return ReferenceSelfAssignExecutable.build(node.getLocation(), name, id.getVarSlot(), operator,
                            notNull(expr));
                return SelfAssignExecutable.build(node.getLocation(), name, id.getVarSlot(), operator, notNull(expr));
            } else {
                return ScopeSelfAssignExecutable.build(node.getLocation(), name, operator, notNull(expr));
            }
        }
    }

    private IExecutableExpression buildMemberAssign(XLangASTNode node, MemberExpression left, XLangOperator operator,
                                                    IExecutableExpression expr, IXLangCompileScope context) {
        Expression owner = left.getObject();
        IExecutableExpression ownerExpr = processNotNullAST(owner, context);
        Expression id = left.getProperty();
        if (!left.getComputed()) {
            String name = ((Identifier) id).getName();
            if (operator == XLangOperator.ASSIGN) {
                return new SetPropertyExecutable(node.getLocation(), ownerExpr, name, notNull(expr));
            } else {
                return new SelfAssignPropertyExecutable(node.getLocation(), ownerExpr, name, operator, notNull(expr));
            }
        } else {
            IExecutableExpression attrExpr = processNotNullAST(id, context);
            if (operator == XLangOperator.ASSIGN) {
                return new SetAttrExecutable(node.getLocation(), ownerExpr, attrExpr, notNull(expr));
            } else {
                return new SelfAssignAttrExecutable(node.getLocation(), ownerExpr, attrExpr, operator, notNull(expr));
            }
        }

    }

    private IExecutableExpression buildArrayBindingAssign(XLangASTNode node, ArrayBinding left,
                                                          IExecutableExpression expr, IXLangCompileScope context) {
        List<ArrayElementBinding> elementBindings = left.getElements();
        AssignIdentifier[] bindings = new AssignIdentifier[elementBindings.size()];
        for (int i = 0, n = elementBindings.size(); i < n; i++) {
            ArrayElementBinding elementBinding = elementBindings.get(i);
            bindings[i] = buildAssignIdentifier(elementBinding.getIdentifier(), elementBinding.getInitializer(),
                    context);
        }

        AssignIdentifier restBinding = buildRestBinding(left.getRestBinding(), context);
        return new ArrayBindingAssignExecutable(node.getLocation(), bindings, restBinding, expr);
    }

    private IExecutableExpression buildObjectBindingAssign(XLangASTNode node, ObjectBinding left,
                                                           IExecutableExpression expr, IXLangCompileScope context) {
        List<PropertyBinding> elementBindings = left.getProperties();
        PropBinding[] bindings = new PropBinding[elementBindings.size()];
        for (int i = 0, n = elementBindings.size(); i < n; i++) {
            PropertyBinding elementBinding = elementBindings.get(i);
            bindings[i] = buildPropBinding(elementBinding.getPropName(), elementBinding.makeIdentifier(),
                    elementBinding.getInitializer(), context);
        }

        AssignIdentifier restBinding = buildRestBinding(left.getRestBinding(), context);
        return new ObjectBindingAssignExecutable(node.getLocation(), bindings, restBinding, expr);
    }

    private PropBinding buildPropBinding(String propName, Identifier id, Expression initializer,
                                         IXLangCompileScope context) {
        IExecutableExpression initExpr = processAST(initializer, context);
        return new PropBinding(id.getLocation(), id.getVarDeclaration().getVarSlot(), id.getName(),
                id.getVarDeclaration().isUseRef(), initExpr, propName);
    }

    private AssignIdentifier buildRestBinding(RestBinding binding, IXLangCompileScope context) {
        if (binding == null)
            return null;
        return buildAssignIdentifier(binding.getIdentifier(), binding.getInitializer(), context);
    }

    private AssignIdentifier buildAssignIdentifier(Identifier id, Expression initializer, IXLangCompileScope context) {
        IExecutableExpression initExpr = processAST(initializer, context);
        return new AssignIdentifier(id.getLocation(), id.getVarDeclaration().getVarSlot(), id.getName(),
                id.getVarDeclaration().isUseRef(), initExpr);
    }

    @Override
    public IExecutableExpression processLogicalExpression(LogicalExpression node, IXLangCompileScope context) {
        IExecutableExpression leftExpr = buildValueExpr(node.getLeft(), context);
        IExecutableExpression rightExpr = buildValueExpr(node.getRight(), context);
        return BinaryExecutable.valueOf(node.getLocation(), node.getOperator(), leftExpr, rightExpr);
    }

    @Override
    public IExecutableExpression processMemberExpression(MemberExpression node, IXLangCompileScope context) {
        if (isClassRef(node.getObject())) {
            IClassModel classModel = getRefClass((Identifier) node.getObject());
            if (node.getComputed()) {
                throw new NopEvalException(ERR_EXEC_CLASS_NOT_ALLOW_ATTR_EXPR)
                        .param(ARG_CLASS_NAME, classModel.getClassName()).source(node.getObject());
            }
            String propName = getPropName(node);
            IFieldModel field = classModel.getStaticField(propName);
            if (field == null)
                throw new NopEvalException(ERR_EXEC_CLASS_NO_STATIC_FIELD)
                        .param(ARG_CLASS_NAME, classModel.getClassName()).param(ARG_PROP_NAME, propName)
                        .source(node.getObject());

            return new StaticGetterGetPropertyExecutable(node.getLocation(), classModel.getClassName(), propName,
                    field.getGetter());
        }
        IExecutableExpression objExpr = buildValueExpr(node.getObject(), context);
        Expression prop = node.getProperty();
        if (node.getComputed()) {
            IExecutableExpression attr = buildValueExpr(prop, context);
            return new GetAttrExecutable(node.getLocation(), objExpr, node.getOptional(), attr);
        } else {
            return new GetPropertyExecutable(node.getLocation(), objExpr, node.getOptional(), ((Identifier) prop).getName());
        }
    }

    private String getPropName(MemberExpression node) {
        return ((Identifier) node.getProperty()).getName();
    }

    private IClassModel getRefClass(Identifier node) {
        ImportClassDefinition def = (ImportClassDefinition) node.getResolvedDefinition();
        return def.getClassModel();
    }

    private boolean isClassRef(Expression node) {
        if (node.getASTKind() != XLangASTKind.Identifier) {
            return false;
        }

        return ((Identifier) node).getIdentifierKind() == IdentifierKind.IMPORT_CLASS_REF;
    }

    @Override
    public IExecutableExpression processCallExpression(CallExpression node, IXLangCompileScope context) {
        Expression callee = node.getCallee();
        IExecutableExpression[] argExprs = buildArgExprs(node.getArguments(), context);
        switch (callee.getASTKind()) {
            case Identifier:
                return buildIdentifierCall((Identifier) callee, argExprs, node, context);
            case MemberExpression:
                return buildMemberCall((MemberExpression) callee, argExprs, node, context);
            default: {
                IExecutableExpression funcExpr = processNotNullAST(callee, context);
                return VarFunctionExecutable.build(node.getLocation(), funcExpr, node.getOptional(), argExprs);
            }
        }
    }

    private IExecutableExpression[] buildArgExprs(List<? extends XLangASTNode> exprs, IXLangCompileScope context) {
        IExecutableExpression[] ret = new IExecutableExpression[exprs.size()];
        for (int i = 0, n = exprs.size(); i < n; i++) {
            XLangASTNode expr = exprs.get(i);
            ret[i] = buildValueExpr(expr, context);
        }
        return ret;
    }

    private IExecutableExpression buildValueExpr(XLangASTNode expr, IXLangCompileScope context) {
        return processNotNullAST(expr, context);
    }

    /**
     * 是变量引用
     *
     * @param node
     * @return
     */
//    private boolean isRefIdentifier(IXLangASTNode node) {
//        if (node.getASTKind() != XLangASTKind.Identifier)
//            return false;
//
//        Identifier id = (Identifier) node;
//        return id.getResolvedDefinition().isUseRef();
//    }
    private IExecutableExpression buildIdentifierCall(Identifier id, IExecutableExpression[] argExprs,
                                                      CallExpression node, IXLangCompileScope context) {
        if (id.getResolvedDefinition() instanceof ResolvedFuncDefinition) {
            IFunctionModel fn = ((ResolvedFuncDefinition) id.getResolvedDefinition()).getFunctionModel();
            argExprs = normalizeArgExprs(node, fn, argExprs);
            if (fn.getInvoker() instanceof ExecutableFunction) {
                return ((ExecutableFunction) fn.getInvoker()).withArgs(id.getLocation(), argExprs);
            } else if (fn.getInvoker() instanceof XplLibTagCompiler.LazyCompiledFunction) {
                return new LazyCompiledExecutableFunction(id.getLocation(), id.getName(),
                        argExprs, (XplLibTagCompiler.LazyCompiledFunction) fn.getInvoker());
            }
            return FunctionExecutable.build(node.getLocation(), id.getName(), fn, argExprs);
        } else if (id.getIdentifierKind() == IdentifierKind.FUNC_REF) {
            LocalVarDeclaration func = (LocalVarDeclaration) id.getResolvedDefinition();
            FuncData funcData = executableFuncs.get(func.getToken());
            ExecutableFunction fn = funcData.fn;
            argExprs = normalizeArgExprs(node, fn, argExprs);

            LexicalScope fnScope = func.getFunctionScope();
            if (fnScope.getClosureCount() <= 0) {
                CallFuncExecutable exec = new CallFuncExecutable(node.getLocation(), fn.getFuncName(),
                        fn.getSlotNames(), argExprs, fn.getBody());
                // 函数嵌套，被引用的函数此时尚未编译body部分
                if (fn.getBody() == null) {
                    funcData.addLazyConsumer(exec::setBodyExpr);
                }
                return exec;
            } else {
                LexicalScope currentScope = id.getLexicalScope();
                CallFuncWithClosureExecutable exec = new CallFuncWithClosureExecutable(node.getLocation(),
                        fn.getFuncName(), fn.getSlotNames(), argExprs, fn.getBody(),
                        currentScope.getClosureSlots(fnScope.getAllClosureVars()), fnScope.getClosureTargetSlots());
                if (fn.getBody() == null) {
                    funcData.addLazyConsumer(exec::setBodyExpr);
                }
                return exec;
            }
        } else {
            IExecutableExpression funcExpr = processIdentifier(id, context);
            return VarFunctionExecutable.build(node.getLocation(), funcExpr, node.getOptional(), argExprs);
        }
    }

    private IExecutableExpression[] normalizeArgExprs(CallExpression node, IFunctionModel fn, IExecutableExpression[] argExprs) {
        if (fn.getArgCount() == argExprs.length)
            return argExprs;

        if (fn.getMaxArgCount() < argExprs.length)
            throw new NopEvalException(ERR_EXEC_TOO_MANY_ARGS).param(ARG_MAX_COUNT, fn.getMaxArgCount())
                    .param(ARG_FUNC_NAME, fn.getName()).source(node).param(ARG_EXPR,
                            fn.getName() + "(" + StringHelper.join(Arrays.asList(argExprs), ",") + ")");

        if (fn.getMinArgCount() > argExprs.length)
            throw new NopEvalException(ERR_EXEC_TOO_FEW_ARGS).param(ARG_MIN_COUNT, fn.getMinArgCount())
                    .param(ARG_FUNC_NAME, fn.getName()).source(node).param(ARG_EXPR,
                            fn.getName() + "(" + StringHelper.join(Arrays.asList(argExprs), ",") + ")");

        IExecutableExpression[] ret = new IExecutableExpression[fn.getArgCount()];
        System.arraycopy(argExprs, 0, ret, 0, argExprs.length);
        for (int i = argExprs.length, n = ret.length; i < n; i++) {
            ret[i] = NullExecutable.NULL;
        }
        return ret;
    }

    private IExecutableExpression buildMemberCall(MemberExpression callee, IExecutableExpression[] argExprs,
                                                  CallExpression node, IXLangCompileScope context) {
        if (isClassRef(callee.getObject())) {
            IClassModel classModel = getRefClass((Identifier) callee.getObject());
            if (callee.getComputed()) {
                throw new NopEvalException(ERR_EXEC_CLASS_NOT_ALLOW_ATTR_EXPR)
                        .param(ARG_CLASS_NAME, classModel.getClassName()).source(callee.getObject());
            }
            String funcName = getPropName(callee);
            IMethodModelCollection coll = classModel.getStaticMethodsByName(funcName);
            if (coll == null)
                throw new NopEvalException(ERR_EXEC_CLASS_NO_STATIC_METHOD)
                        .param(ARG_CLASS_NAME, classModel.getClassName()).param(ARG_METHOD_NAME, funcName)
                        .source(callee.getObject());

            IFunctionModel method = coll.getUniqueMethod(argExprs.length);
            if (method != null) {
                return FunctionExecutable.build(node.getLocation(), funcName, method, argExprs);
            }

            return new StaticFunctionExecutable(node.getLocation(), classModel.getClassName(),
                    funcName, node.getOptional(), coll, argExprs);
        }

        if (!callee.getComputed()) {
            IExecutableExpression objExpr = buildValueExpr(callee.getObject(), context);
            String funcName = ((Identifier) callee.getProperty()).getName();
            if (XLangConstants.SYS_FUNC_DEBUG.equals(funcName)) {
                return buildDebugExpr(callee.getLocation(), objExpr, argExprs);
            }

            IExecutableExpression convertExpr = buildConvertExpr(callee.getLocation(), funcName, objExpr, argExprs);
            if (convertExpr != null)
                return convertExpr;

            if (!callee.getOptional()) {
                objExpr = new GuardNotNullExecutable(objExpr.getLocation(), objExpr);
            }

            return ObjFunctionExecutable.build(node.getLocation(), objExpr, funcName, node.getOptional(), argExprs);
        } else {
            IExecutableExpression funcExpr = processNotNullAST(callee, context);
            return VarFunctionExecutable.build(node.getLocation(), funcExpr, node.getOptional(), argExprs);
        }
    }

    /**
     * a.f().$(prefix)转换为DebugHelper.v(loc,prefix, "a.f()",a.f())
     */
    private IExecutableExpression buildDebugExpr(SourceLocation loc, IExecutableExpression objExpr,
                                                 IExecutableExpression[] argExprs) {
        if (argExprs.length > 1) {
            throw new NopEvalException(ERR_EXEC_TOO_MANY_ARGS).param(ARG_MAX_COUNT, 1)
                    .param(ARG_FUNC_NAME, XLangConstants.SYS_FUNC_DEBUG).loc(loc).param(ARG_EXPR, objExpr.display());
        }
        IExecutableExpression prefixExpr = argExprs.length == 0 ? LiteralExecutable.build(null, "debug") : argExprs[0];
        return new DebugExecutable(loc, objExpr, prefixExpr);
    }

    /**
     * a.$toInt()这种内置类型转换函数
     */
    private IExecutableExpression buildConvertExpr(SourceLocation loc, String funcName, IExecutableExpression objExpr,
                                                   IExecutableExpression[] argExprs) {
        if (XLang.isGlobalVarName(funcName)) {
            TargetTypeConverter converter = SysConverterRegistry.instance().getConverterByName(funcName.substring(1));
            if (converter != null) {
                if (argExprs.length == 0) {
                    return new ConvertExecutable(loc, objExpr, funcName, converter.getConverter());
                } else if (argExprs.length == 1) {
                    return new ConvertWithDefaultExecutable(loc, objExpr, funcName, converter.getConverter(),
                            argExprs[0]);
                } else {
                    throw new NopEvalException(ERR_EXEC_CONVERT_FUNC_ONLY_ALLOW_ONE_ARG).param(ARG_FUNC_NAME, funcName)
                            .loc(loc).param(ARG_EXPR, objExpr.display());
                }
            }
        }
        return null;
    }

    private IExecutableExpression[] normalizeArgExprs(CallExpression node, ExecutableFunction func,
                                                      IExecutableExpression[] argExprs) {
        func.checkArgCount(node.getLocation(), argExprs);
        if (argExprs.length >= func.getArgCount())
            return argExprs;

        IExecutableExpression[] ret = new IExecutableExpression[func.getArgCount()];
        System.arraycopy(argExprs, 0, ret, 0, argExprs.length);
        IExecutableExpression[] defaultValues = func.getDefaultArgValues();
        for (int i = argExprs.length, n = func.getArgCount(); i < n; i++) {
            ret[i] = defaultValues[i - argExprs.length];
        }

        return ret;
    }

    @Override
    public IExecutableExpression processNewExpression(NewExpression node, IXLangCompileScope context) {
        IClassModel classModel = node.getCallee().getClassModel();
        IExecutableExpression[] argExprs = this.buildArgExprs(node.getArguments(), context);
        if (argExprs.length == 0) {
            IFunctionModel constructor = getConstructor(classModel);
            if (constructor == null)
                throw new NopEvalException(ERR_EXEC_CLASS_NO_CONSTRUCTOR)
                        .param(ARG_CLASS_NAME, classModel.getClassName()).param(ARG_ARG_COUNT, 0);
            return FunctionExecutable.build(node.getLocation(), classModel.getClassName(), constructor,
                    IExecutableExpression.EMPTY_EXPRS);
        }
        if (!classModel.hasConstructorWithArgCount(argExprs.length)) {
            throw new NopEvalException(ERR_EXEC_CLASS_NO_CONSTRUCTOR).param(ARG_CLASS_NAME, classModel.getClassName())
                    .param(ARG_ARG_COUNT, argExprs.length);
        }
        return new NewObjectExecutable(node.getLocation(), classModel, argExprs);
    }

    IFunctionModel getConstructor(IClassModel classModel) {
        Class<?> rawClass = classModel.getRawClass();
        if (rawClass == Set.class) {
            classModel = ReflectionManager.instance().getClassModel(LinkedHashSet.class);
        } else if (rawClass == List.class) {
            classModel = ReflectionManager.instance().getClassModel(ArrayList.class);
        } else if (rawClass == Map.class) {
            classModel = ReflectionManager.instance().getClassModel(LinkedHashMap.class);
        }
        return classModel.getConstructor(0);
    }

    @Override
    public IExecutableExpression processSequenceExpression(SequenceExpression node, IXLangCompileScope context) {
        List<IExecutableExpression> exprs = buildExprs(node.getExpressions(), context);
        if (exprs == null)
            return null;
        return SeqExecutable.valueOf(node.getLocation(), toArray(exprs));
    }

    @Override
    public IExecutableExpression processConcatExpression(ConcatExpression node, IXLangCompileScope context) {
        IExecutableExpression[] exprs = buildArgExprs(node.getExpressions(), context);
        if (exprs == null || exprs.length == 0)
            return null;
        return new ConcatExecutable(node.getLocation(), exprs);
    }

    @Override
    public IExecutableExpression processTemplateExpression(TemplateExpression node, IXLangCompileScope context) {
        IExecutableExpression[] exprs = buildArgExprs(node.getExpressions(), context);
        if (exprs == null || exprs.length == 0)
            return null;
        return new ConcatExecutable(node.getLocation(), exprs);
    }

    @Override
    public IExecutableExpression processBraceExpression(BraceExpression node, IXLangCompileScope context) {
        return buildValueExpr(node.getExpr(), context);
    }

    @Override
    public IExecutableExpression processOutputXmlAttrExpression(OutputXmlAttrExpression node,
                                                                IXLangCompileScope context) {
        IExecutableExpression value = buildValueExpr(node.getValue(), context);
        return new OutputXmlAttrExecutable(node.getLocation(), node.getName(), value);
    }

    @Override
    public IExecutableExpression processOutputXmlExtAttrsExpression(OutputXmlExtAttrsExpression node,
                                                                    IXLangCompileScope context) {
        IExecutableExpression attrs = buildValueExpr(node.getExtAttrs(), context);
        return new OutputXmlExtAttrsExecutable(node.getLocation(), node.getExcludeNames(), attrs);
    }

    @Override
    public IExecutableExpression processExportDeclaration(ExportDeclaration node, IXLangCompileScope context) {
        Declaration decl = node.getDeclaration();
        return processAST(decl, context);
    }

    @Override
    public IExecutableExpression processExportNamedDeclaration(ExportNamedDeclaration node,
                                                               IXLangCompileScope context) {
        return null;
    }

    @Override
    public IExecutableExpression processExportAllDeclaration(ExportAllDeclaration node, IXLangCompileScope context) {
        return null;
    }

    @Override
    public IExecutableExpression processImportDeclaration(ImportDeclaration node, IXLangCompileScope context) {
        return null;
    }

    @Override
    public IExecutableExpression processImportAsDeclaration(ImportAsDeclaration node, IXLangCompileScope context) {
        return null;
    }

    @Override
    public IExecutableExpression processAwaitExpression(AwaitExpression node, IXLangCompileScope context) {
        return super.processAwaitExpression(node, context);
    }

    @Override
    public IExecutableExpression processDecorators(Decorators node, IXLangCompileScope context) {
        return super.processDecorators(node, context);
    }

    @Override
    public IExecutableExpression processQualifiedName(QualifiedName node, IXLangCompileScope context) {
        return super.processQualifiedName(node, context);
    }

    @Override
    public IExecutableExpression processDecorator(Decorator node, IXLangCompileScope context) {
        return super.processDecorator(node, context);
    }

    @Override
    public IExecutableExpression processUsingStatement(UsingStatement node, IXLangCompileScope context) {
        return super.processUsingStatement(node, context);
    }

    @Override
    public IExecutableExpression processMacroExpression(MacroExpression node, IXLangCompileScope context) {
        return super.processMacroExpression(node, context);
    }

    @Override
    public IExecutableExpression processTextOutputExpression(TextOutputExpression node, IXLangCompileScope context) {
        return new OutputTextExecutable(node.getLocation(), node.getText());
    }

    @Override
    public IExecutableExpression processEscapeOutputExpression(EscapeOutputExpression node,
                                                               IXLangCompileScope context) {
        IExecutableExpression valueExpr = buildValueExpr(node.getText(), context);
        return new EscapeOutputExecutable(node.getLocation(), node.getEscapeMode(), valueExpr);
    }

    @Override
    public IExecutableExpression processCollectOutputExpression(CollectOutputExpression node,
                                                                IXLangCompileScope context) {
        IExecutableExpression bodyExpr = processNotNullAST(node.getBody(), context);
        if (node.getOutputMode() == XLangOutputMode.node)
            return new CollectNodeExecutable(node.getLocation(), bodyExpr, node.getSingleNode());
        if (node.getOutputMode() == XLangOutputMode.xjson) {
            return new GenXJsonExecutable(bodyExpr);
        }
        if (node.getOutputMode() == XLangOutputMode.sql) {
            return new CollectSqlExecutable(bodyExpr);
        }
        return new CollectTextExecutable(node.getLocation(), bodyExpr);
    }

    @Override
    public IExecutableExpression processCompareOpExpression(CompareOpExpression node, IXLangCompileScope context) {
        IExecutableExpression leftExpr = buildValueExpr(node.getLeft(), context);
        IExecutableExpression rightExpr = buildValueExpr(node.getRight(), context);
        FilterOp filterOp = FilterOp.fromName(node.getOp());
        if (filterOp == null)
            throw new NopEvalException(ERR_XPL_UNKNOWN_FILTER_OP).param(ARG_OP, node.getOp()).source(node);

        return new CompareOpExecutable(node.getLocation(), filterOp, leftExpr, rightExpr);
    }

    @Override
    public IExecutableExpression processAssertOpExpression(AssertOpExpression node, IXLangCompileScope context) {
        IExecutableExpression valueExpr = buildValueExpr(node.getValue(), context);
        FilterOp filterOp = FilterOp.fromName(node.getOp());
        if (filterOp == null)
            throw new NopEvalException(ERR_XPL_UNKNOWN_FILTER_OP).param(ARG_OP, node.getOp()).source(node);

        return new AssertOpExecutable(node.getLocation(), filterOp, valueExpr);
    }

    @Override
    public IExecutableExpression processBetweenOpExpression(BetweenOpExpression node, IXLangCompileScope context) {
        IExecutableExpression valueExpr = buildValueExpr(node.getValue(), context);
        IExecutableExpression minExpr = buildValueExpr(node.getMin(), context);
        IExecutableExpression maxExpr = buildValueExpr(node.getMax(), context);
        FilterOp filterOp = FilterOp.fromName(node.getOp());
        if (filterOp == null)
            throw new NopEvalException(ERR_XPL_UNKNOWN_FILTER_OP).param(ARG_OP, node.getOp()).source(node);

        return new BetweenOpExecutable(node.getLocation(), filterOp, valueExpr, minExpr, maxExpr,
                node.getExcludeMin(), node.getExcludeMax());
    }

    @Override
    public IExecutableExpression processGenNodeExpression(GenNodeExpression node, IXLangCompileScope context) {
        if (node.getTextNode()) {
            IExecutableExpression bodyExpr = processAST(node.getBody(), context);
            if (bodyExpr != null) {
                return new OutputValueExecutable(node.getLocation(), bodyExpr);
            }
            return null;
        }
        GenNodeAttrExecutable[] attrs = buildGenNodeAttrs(node, context);
        IExecutableExpression extAttrsExpr = buildValueExpr(node.getExtAttrs(), context);
        IExecutableExpression bodyExpr = processAST(node.getBody(), context);
        String tagName = OutputParseHelper.getTagName(node.getTagName());
        IExecutableExpression tagNameExpr = null;
        if (tagName == null) {
            tagNameExpr = processAST(node.getTagName(), context);
        }
        return new GenNodeExecutable(node.getLocation(), tagName, tagNameExpr, attrs, extAttrsExpr, bodyExpr);
    }

    private GenNodeAttrExecutable[] buildGenNodeAttrs(GenNodeExpression node, IXLangCompileScope context) {
        GenNodeAttrExecutable[] attrs = new GenNodeAttrExecutable[node.getAttrs().size()];
        for (int i = 0, n = node.getAttrs().size(); i < n; i++) {
            GenNodeAttrExpression attrExpr = node.getAttrs().get(i);
            attrs[i] = new GenNodeAttrExecutable(attrExpr.getName(), buildValueExpr(attrExpr.getValue(), context));
        }
        return attrs;
    }

    @Override
    public IExecutableExpression processTypeOfExpression(TypeOfExpression node, IXLangCompileScope context) {
        IExecutableExpression expr = processNotNullAST(node.getArgument(), context);
        return new TypeOfExecutable(node.getLocation(), expr);
    }

    @Override
    public IExecutableExpression processInstanceOfExpression(InstanceOfExpression node, IXLangCompileScope context) {
        IGenericType type = node.getRefType().getTypeInfo();
        IExecutableExpression expr = processNotNullAST(node.getValue(), context);
        return new InstanceOfExecutable(node.getLocation(), expr, type);
    }

    @Override
    public IExecutableExpression processCastExpression(CastExpression node, IXLangCompileScope context) {
        return null;
    }


    @Override
    public IExecutableExpression processCustomExpression(CustomExpression node, IXLangCompileScope context) {
        return node.buildExecutable(context);
    }
}