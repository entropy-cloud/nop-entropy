/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpl.xlib;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.impl.FunctionArgument;
import io.nop.core.reflect.impl.FunctionModel;
import io.nop.core.resource.cache.IObjectChangeDetectable;
import io.nop.core.resource.cache.ResourceCacheEntryWithLoader;
import io.nop.core.resource.component.ResourceComponentManager;
import io.nop.core.resource.deps.ResourceDependencySet;
import io.nop.core.resource.deps.VirtualResourceDependencySet;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.ast.ArrowFunctionExpression;
import io.nop.xlang.ast.CallExpression;
import io.nop.xlang.ast.ChainExpression;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.Literal;
import io.nop.xlang.ast.NamedTypeNode;
import io.nop.xlang.ast.ObjectExpression;
import io.nop.xlang.ast.ParameterDeclaration;
import io.nop.xlang.ast.PropertyAssignment;
import io.nop.xlang.ast.XLangASTBuilder;
import io.nop.xlang.ast.XLangASTNode;
import io.nop.xlang.ast.XLangOutputMode;
import io.nop.xlang.ast.definition.ResolvedFuncDefinition;
import io.nop.xlang.utils.ExprEvalHelper;
import io.nop.xlang.xdef.XDefConstants;
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.IXplLibTagCompiler;
import io.nop.xlang.xpl.IXplTagAttribute;
import io.nop.xlang.xpl.IXplTagSlot;
import io.nop.xlang.xpl.IXplTagSlotArg;
import io.nop.xlang.xpl.XplConstants;
import io.nop.xlang.xpl.XplSlotType;
import io.nop.xlang.xpl.utils.XplParseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static io.nop.xlang.XLangConfigs.CFG_XPL_LIB_TAG_RELOADABLE;
import static io.nop.xlang.XLangErrors.ARG_ALLOWED_NAMES;
import static io.nop.xlang.XLangErrors.ARG_ARG_NAME;
import static io.nop.xlang.XLangErrors.ARG_ATTR_NAME;
import static io.nop.xlang.XLangErrors.ARG_NAME;
import static io.nop.xlang.XLangErrors.ARG_NODE;
import static io.nop.xlang.XLangErrors.ARG_SLOT_NAME;
import static io.nop.xlang.XLangErrors.ARG_TAG_NAME;
import static io.nop.xlang.XLangErrors.ARG_VAR_NAME;
import static io.nop.xlang.XLangErrors.ERR_XLANG_INVALID_VAR_NAME;
import static io.nop.xlang.XLangErrors.ERR_XPL_MACRO_TAG_ATTR_NOT_STATIC_VALUE;
import static io.nop.xlang.XLangErrors.ERR_XPL_TAG_ATTR_IS_MANDATORY;
import static io.nop.xlang.XLangErrors.ERR_XPL_TAG_FUNC_IS_COMPILING;
import static io.nop.xlang.XLangErrors.ERR_XPL_TAG_MISSING_ATTR;
import static io.nop.xlang.XLangErrors.ERR_XPL_TAG_MISSING_SLOT;
import static io.nop.xlang.XLangErrors.ERR_XPL_TAG_UNKNOWN_SLOT_ARG;
import static io.nop.xlang.XLangErrors.ERR_XPL_UNKNOWN_TAG_ATTR;
import static io.nop.xlang.XLangErrors.ERR_XPL_UNKNOWN_TAG_SLOT;

/**
 * 延迟编译标签的具体实现，从而缩小标签的资源依赖范围。在延迟编译的情况下，只有使用到具体某个标签的代码才会依赖该标签内部实现所用到的资源。
 */
public class XplLibTagCompiler implements IXplLibTagCompiler {
    static final Logger LOG = LoggerFactory.getLogger(XplLibTagCompiler.class);

    private final XplTagLib lib;
    private final XplTag tag;

    /**
     * 实现代码发生变化时将会重新编译
     */
    private final ResourceCacheEntryWithLoader<CompiledTag> cachedCompiledTag = new ResourceCacheEntryWithLoader<>("XplLibTagCompiler",
            path -> this.buildCompiledTag(null));

    /**
     * 强制按照node输出模式编译.当输出模式为xml的标签被用在x:exp-extends段中时会使用这里的编译结果
     */
    private final ResourceCacheEntryWithLoader<CompiledTag> cachedCompiledTagForNodeNode = new ResourceCacheEntryWithLoader<>(
            "XplLibTagCompilerForNodeMode", path -> this.buildCompiledTag(XLangOutputMode.node));

    public XplLibTagCompiler(XplTagLib lib, XplTag tag) {
        this.tag = tag;
        this.lib = lib;
    }

    static class CompiledTag implements IObjectChangeDetectable {
        /**
         * 标签实现体可以在标签上标记xpl:slot来引用外部传入的slot。如果外部没有定制对应的slot，则原标签作为缺省实现被渲染。 如果外部传入了slot，则外部传入的slot将替代缺省实现来渲染。
         * <p>
         * 例如: my.xlib中
         *
         * <pre>{@code
         *    <Dialog>
         *        <slot name="buttons" />
         *
         *        <source>
         *            <div class='dialog'>
         *               <div xpl:slot="buttons">
         *                   <!--buttons段的缺省实现是一个提交按钮 -->
         *                   <button onclick="onSubmit()">submit</button>
         *               </div>
         *            </div>
         *        </source>
         *    </Dialog>
         * }</pre>
         * <p>
         * 调用时可以通过buttons这个slot来改变按钮区的设置
         *
         * <pre>{@code
         *       <my:Dialog>
         *           <buttons>
         *               <button onclick="onAdd()">Add</button>
         *               <button onclick="onSubmit()">submit</button>
         *           </buttons>
         *       </my:Dialog>
         *   }</pre>
         */
        // Map<String, XNode> slotDefaults = Collections.emptyMap();
        FunctionModel functionModel;

        /**
         * 延迟编译source段所产生的依赖
         */
        ResourceDependencySet deps;

        // XNode getSlotDefault(String slotName) {
        // return slotDefaults.get(slotName);
        // }

        @Override
        public boolean isObjectChanged() {
            ResourceDependencySet deps = this.deps;
            if (deps == null)
                return false;
            return ResourceComponentManager.instance().isAnyDependsChange(deps.getDepends());
        }

        IFunctionModel lazyCompile() {
            IEvalFunction invoker = functionModel.getInvoker();
            if (invoker instanceof LazyCompiledFunction) {
                ((LazyCompiledFunction) invoker).compile();
            }
            if (deps != null) {
                ResourceComponentManager.instance().traceAllDepends(deps.getDepends().keySet());
            }
            return functionModel;
        }
    }

    @Override
    public Expression parseTag(XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        checkUnknownArgs(node);

        CompiledTag compiledTag;

        // 如果外部环境要求node输出，而标签本身标记了xml或者html输出，则转换为node输出
        if (isOutputNode(scope.getOutputMode())) {
            compiledTag = this.cachedCompiledTagForNodeNode.getObject(CFG_XPL_LIB_TAG_RELOADABLE.get());
        } else {
            compiledTag = this.cachedCompiledTag.getObject(CFG_XPL_LIB_TAG_RELOADABLE.get());
        }

        List<Expression> tagArgs = parseFuncArgs(node, cp, scope);

        CallExpression callExpr = new CallExpression();
        callExpr.setLocation(node.getLocation());
        callExpr.setXplLibPath(lib.resourcePath());
        Identifier id = Identifier.valueOf(node.getLocation(), tag.getTagFuncName());
        id.setResolvedDefinition(new ResolvedFuncDefinition(compiledTag.functionModel));
        callExpr.setCallee(id);
        callExpr.setArguments(tagArgs);

        compiledTag.lazyCompile();

        Expression ret = callExpr;

        if (tag.isMacro()) {
            // 宏标签需要被立刻执行
            ret = ExprEvalHelper.runMacroExpression(callExpr, scope, tag.isDump());
        }

        if (tag.isConditionTag()) {
            Expression body = cp.parseTagBody(node, scope);
            if (body != null) {
                ret = XLangASTBuilder.ifStatement(node.getLocation(), callExpr, body);
            }
        }
        return ret;
    }

    private void checkUnknownArgs(XNode node) {
        if (tag.getUnknownAttrsVar() == null) {
            node.forEachAttr((name, vl) -> {
                if (tag.getAttr(name) == null) {
                    if (name.startsWith(XLangConstants.SLOT_VAR_PREFIX)) {
                        String slotName = name.substring(XLangConstants.SLOT_VAR_PREFIX.length());
                        if (tag.getSlot(slotName) == null)
                            throw new NopEvalException(ERR_XPL_UNKNOWN_TAG_SLOT).loc(node.getLocation())
                                    .param(ARG_TAG_NAME, node.getTagName()).param(ARG_SLOT_NAME, slotName)
                                    .param(ARG_NODE, node).param(ARG_ALLOWED_NAMES, tag.keySet_slots());
                        return;
                    }

                    if (isAllowedUnknownAttr(node, name))
                        return;

                    throw new NopEvalException(ERR_XPL_UNKNOWN_TAG_ATTR).loc(node.getLocation())
                            .param(ARG_TAG_NAME, node.getTagName()).param(ARG_ATTR_NAME, name).param(ARG_NODE, node)
                            .param(ARG_ALLOWED_NAMES, tag.keySet_attrs());
                }
            });
        }

        if (!tag.isConditionTag() && tag.getSlot(XLangConstants.SLOT_DEFAULT) == null) {
            node.forEachChild(child -> {
                if (tag.getSlot(child.getTagName()) == null)
                    throw new NopEvalException(ERR_XPL_UNKNOWN_TAG_SLOT).loc(node.getLocation())
                            .param(ARG_TAG_NAME, node.getTagName()).param(ARG_SLOT_NAME, child.getTagName())
                            .param(ARG_NODE, node).param(ARG_ALLOWED_NAMES, tag.keySet_slots());
            });
        }
    }

    public XplTag getTag() {
        return tag;
    }

    private boolean isAllowedUnknownAttr(XNode node, String name) {
        if (tag.isIgnoreUnknownAttrs())
            return true;

        int pos = name.indexOf(':');
        if (pos < 0)
            return false;

        // 所有带名字空间的参数都是允许的
        if (name.startsWith(XLangConstants.XPL_NS_PREFIX)) {
            if (!XplConstants.XPL_ATTRS.contains(name))
                throw new NopEvalException(ERR_XPL_UNKNOWN_TAG_ATTR).loc(node.attrLoc(name))
                        .param(ARG_TAG_NAME, node.getTagName()).param(ARG_ATTR_NAME, name).param(ARG_NODE, node)
                        .param(ARG_ALLOWED_NAMES, XplConstants.XPL_ATTRS);
            return true;
        }

        String ns = name.substring(0, pos);
        if (tag.getCheckNs() != null && tag.getCheckNs().contains(ns))
            return false;

        return true;
    }
    //
    // private Expression parseMacroTag(XNode node, IXplCompiler cp, IXLangCompileScope scope) {
    // scope.enterMacro();
    // ExprEvalAction action;
    // try {
    // scope.enterBlock(true);
    // action = new XLangCompileTool(scope).compileTagBody(node, tag.getOutputMode());
    // } finally {
    // scope.leaveBlock(true);
    // scope.leaveMacro();
    // }
    //
    // Object result = action.invoke(scope);
    // if (result instanceof Expression) {
    // return (Expression) result;
    // }
    //
    // return Literal.valueOf(node.getLocation(), result);
    // }

//    private List<Expression> buildCallArgs(IXplCompiler cp, IXLangCompileScope scope, SourceLocation loc,
//                                           List<Expression> argExprs) {
//        if (tag.getAttrs().size() < argExprs.size()) {
//            throw new NopEvalException(ERR_XPL_TAG_FUNC_TOO_MAY_ARGS).loc(loc).param(ARG_TAG_NAME, tag.getTagName())
//                    .param(ARG_MAX_COUNT, tag.getAttrs().size());
//        }
//        XNode node = XNode.make(tag.getTagName());
//        node.setLocation(loc);
//        for (int i = 0, n = argExprs.size(); i < n; i++) {
//            Expression argExpr = argExprs.get(i);
//            node.setAttr(argExpr.getLocation(), tag.getAttrs().get(i).getName(), argExpr);
//        }
//        return parseFuncArgs(node, cp, scope);
//    }

    private List<Expression> parseFuncArgs(XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        List<Expression> args = new ArrayList<>();

        buildArgs(node, args, name -> XplParseHelper.parseAttrTemplateExpr(node, name, cp, scope));

        if (tag.getUnknownAttrsVar() != null) {
            args.add(buildUnknownArgsObj(node, cp, scope));
        }

        if (tag.getCallLocationVar() != null) {
            args.add(buildCallLocationVar(node));
        }

        if (!tag.isConditionTag()) {
            buildSlotArgs(node, args, slot -> {
                XNode child = slot.getName().equals(XLangConstants.SLOT_DEFAULT) ? (node.hasBody() ? node : null)
                        : node.childByTag(slot.getName());

                XLangOutputMode oldMode = scope.getOutputMode();
                if (child == null) {
                    scope.setOutputMode(getSlotOutputMode(slot, scope));
                    try {
                        Expression expr = XplParseHelper.parseAttrExpr(node, slot.getVarName(), cp, scope);
                        return expr;
                    } finally {
                        scope.setOutputMode(oldMode);
                    }
                }

                if (slot.getSlotType() == XplSlotType.node) {
                    child = child.cloneInstance();
                    child.freeze(true);
                    return Literal.valueOf(child.getLocation(), child);
                } else {
                    scope.setOutputMode(getSlotOutputMode(slot, scope));
                    try {
                        return parseSlotArg(child, slot, cp, scope);
                    } finally {
                        scope.setOutputMode(oldMode);
                    }
                }
            });
        }
        return args;
    }

    private XLangOutputMode getSlotOutputMode(IXplTagSlot slot, IXLangCompileScope scope) {
        XLangOutputMode outputMode = scope.getOutputMode();
        if (isOutputNode(outputMode)) {
            outputMode = XLangOutputMode.node;
        } else {
            outputMode = tag.getOutputMode();
        }

        XLangOutputMode slotMode = slot.getOutputMode();
        if (slotMode == null)
            slotMode = tag.getOutputMode();

        if (slotMode == null)
            slotMode = XLangOutputMode.none;

        if (slotMode.isXmlOrHtml()) {
            if (outputMode == XLangOutputMode.node)
                return XLangOutputMode.node;
            return slotMode;
        }

        return slotMode;
    }

    private Expression parseSlotArg(XNode child, IXplTagSlot slot, IXplCompiler cp, IXLangCompileScope scope) {

        // child = mergeSlot(child, slot, compiledTag);

        ArrowFunctionExpression expr = new ArrowFunctionExpression();
        expr.setLocation(child.getLocation());
        expr.setFuncName(slot.getSlotFuncName());

        expr.setParams(parseSlotScope(child, slot, scope));

        Expression body = cp.parseTagBody(child, scope);
        expr.setBody(body);
        return expr;
    }

    // private XNode mergeSlot(XNode child, IXplTagSlot slot, CompiledTag compiledTag) {
    // XNode slotDefault = compiledTag.getSlotDefault(slot.getName());
    // if (slotDefault == null) {
    // return copySlot(child);
    // }
    //
    // XDslKeys keys = XDslKeys.XPL;
    // XDefOverride override = OverrideHelper.getOverride(child, keys.OVERRIDE);
    // if (override == null || override == XDefOverride.REPLACE)
    // return copySlot(child);
    //
    // slotDefault = slotDefault.cloneInstance();
    // child = child.cloneInstance();
    // child.setTagName(slotDefault.getTagName());
    //
    // // xpl slot的缺省合并策略为替换，而一般xdsl模型的缺省合并策略为merge
    // new DeltaMerger(keys, XDefOverride.REPLACE).merge(slotDefault, child, null, false);
    //
    // return slotDefault;
    // }

    // private XNode copySlot(XNode node) {
    // XNode copy = node.cloneInstance();
    // copy.setTagName(XplConstants.TAG_C_UNIT);
    // copy.clearAttrs();
    // return copy;
    // }

    private List<ParameterDeclaration> parseSlotScope(XNode node, IXplTagSlot slot, IXLangCompileScope scope) {
        ValueWithLocation attr = node.attrValueLoc(XplConstants.ATTR_XPL_SLOT_SCOPE);

        Map<String, String> map = StringHelper.parseSlotScope(attr.asString());
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (slot.getArg(entry.getKey()) == null) {
                throw new NopEvalException(ERR_XPL_TAG_UNKNOWN_SLOT_ARG).param(ARG_NODE, node)
                        .param(ARG_SLOT_NAME, slot.getName()).param(ARG_ARG_NAME, entry.getKey())
                        .param(ARG_ALLOWED_NAMES, slot.keySet_args());
            }

            if (!StringHelper.isValidPropName(entry.getValue()))
                throw new NopEvalException(ERR_XLANG_INVALID_VAR_NAME).param(ARG_NODE, node)
                        .param(ARG_SLOT_NAME, slot.getName()).param(ARG_VAR_NAME, entry.getValue());
        }

        if (slot.getArgs().isEmpty())
            return Collections.emptyList();

        List<ParameterDeclaration> params = new ArrayList<>(slot.getArgs().size());
        for (IXplTagSlotArg arg : slot.getArgs()) {
            String paramName = map.get(arg.getName());
            if (paramName == null) {
                if (arg.isImplicit()) {
                    // 如果是隐式变量，则没有特殊声明的时候直接使用缺省参数名
                    paramName = arg.getName();
                } else {
                    // xpl:slotScope没有引用此变量，因此它需要被忽略
                    paramName = scope.generateVarName("__");
                }
            }
            ParameterDeclaration param = XLangASTBuilder.paramDecl(attr.getLocation(), paramName, arg.getType());
            param.setImplicit(arg.isImplicit());
            params.add(param);
        }
        return params;
    }

    private void buildArgs(XNode node, List<Expression> args, Function<String, Expression> fn) {
        if (tag.getAttrsVar() == null) {
            for (IXplTagAttribute attr : tag.getAttrs()) {
                Expression expr = buildArgExpr(node, attr, fn,
                        err -> new NopEvalException(err).loc(node.getLocation()).param(ARG_TAG_NAME, node.getTagName())
                                .param(ARG_ATTR_NAME, attr.getName()).param(ARG_NODE, node));
                args.add(expr);
            }
        } else {
            args.add(buildArgsObj(node, fn));
        }
    }

    private ObjectExpression buildArgsObj(XNode node, Function<String, Expression> fn) {
        List<XLangASTNode> props = new ArrayList<>();
        for (IXplTagAttribute attr : tag.getAttrs()) {
            Expression expr = buildArgExpr(node, attr, fn, err -> new NopEvalException(err)
                    .loc(node.attrLoc(attr.getName())).param(ARG_ATTR_NAME, attr.getName()).param(ARG_NODE, node));

            PropertyAssignment assign = new PropertyAssignment();
            assign.setLocation(attr.getLocation());
            assign.setKey(Literal.valueOf(attr.getLocation(), attr.getVarName()));
            assign.setValue(expr);
            props.add(assign);
        }
        return ObjectExpression.valueOf(tag.getLocation(), props);
    }

    private Expression buildArgExpr(XNode node, IXplTagAttribute attr, Function<String, Expression> fn,
                                    Function<ErrorCode, NopException> errorFactory) {
        Expression expr = fn.apply(attr.getName());
        if (expr == null) {
            // implicit参数假设外部环境中具有同名的变量
            if (attr.isImplicit()) {
                expr = Identifier.implicitVar(node.getLocation(), attr.getVarName());
            } else {
                if (!attr.isOptional())
                    throw errorFactory.apply(ERR_XPL_TAG_MISSING_ATTR);
                expr = Literal.valueOf(attr.getLocation(), attr.getDefaultValue());
            }
        } else {
            if (expr instanceof Literal) {
                Object value = ((Literal) expr).getValue();
                if (attr.getType() != null) {
                    value = ReflectionManager.instance().getConverterForJavaType(attr.getType().getRawClass())
                            .convert(value, errorFactory);
                }
                if (value != null && XDefConstants.STD_DOMAIN_V_PATH.equals(attr.getStdDomain())) {
                    String path = value.toString();
                    path = StringHelper.absolutePath(expr.resourcePath(), path);
                    value = path;
                }
                ((Literal) expr).setValue(value);
            } else {
                if (tag.isMacro())
                    throw errorFactory.apply(ERR_XPL_MACRO_TAG_ATTR_NOT_STATIC_VALUE);
            }
            expr.setASTParent(null);
        }

        if (attr.isMandatory()) {
            if (expr instanceof Literal) {
                Object value = ((Literal) expr).getValue();
                if (StringHelper.isEmptyObject(value))
                    throw errorFactory.apply(ERR_XPL_TAG_ATTR_IS_MANDATORY);
            } else if (expr instanceof ChainExpression) {
                ((ChainExpression) expr).setOptional(false);
            } else {
                ChainExpression chain = ChainExpression.valueOf(expr.getLocation(), expr, false);
                chain.setNotEmpty(true);
                chain.setTarget(tag.getTagFuncName() + '@' + attr.getName());
                expr = chain;
            }
        }
        return expr;
    }

    private ObjectExpression buildUnknownArgsObj(XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        List<XLangASTNode> props = new ArrayList<>();
        node.forEachAttr((name, vl) -> {
            if (StringHelper.hasNamespace(name)) {
                if (name.startsWith(XLangConstants.XPL_NS_PREFIX)) {
                    if (!XLangConstants.XPL_ATTRS.contains(name))
                        throw new NopEvalException(ERR_XPL_UNKNOWN_TAG_ATTR).loc(node.attrLoc(name))
                                .param(ARG_NAME, name).param(ARG_ALLOWED_NAMES, XplConstants.XPL_ATTRS);
                }
                return;
            }

            if (tag.getAttr(name) == null) {
                PropertyAssignment assign = new PropertyAssignment();
                assign.setLocation(vl.getLocation());
                assign.setKey(Literal.valueOf(vl.getLocation(), name));
                Expression expr = XplParseHelper.parseAttrTemplateExpr(node, name, cp, scope);
                assign.setValue(expr);
                props.add(assign);
            }
        });

        return ObjectExpression.valueOf(tag.getLocation(), props);
    }

    private Expression buildCallLocationVar(XNode node) {
        return Literal.valueOf(node.getLocation(), node.getLocation());
    }

    private void buildSlotArgs(XNode node, List<Expression> args, Function<IXplTagSlot, Expression> fn) {
        for (IXplTagSlot slot : tag.getSlots()) {
            Expression expr = fn.apply(slot);
            if (expr == null) {
                if (slot.isMandatory()) {
                    throw new NopEvalException(ERR_XPL_TAG_MISSING_SLOT).param(ARG_SLOT_NAME, slot.getName())
                            .param(ARG_NODE, node);
                }
                expr = Literal.valueOf(slot.getLocation(), null);
            } else {
                expr.setASTParent(null);
            }
            args.add(expr);
        }
    }

    @Override
    public IFunctionModel getFunctionModel(XLangOutputMode outputMode) {
        if (isOutputNode(outputMode))
            return cachedCompiledTagForNodeNode.getObject(CFG_XPL_LIB_TAG_RELOADABLE.get()).lazyCompile();

        CompiledTag compiledTag = cachedCompiledTag.getObject(CFG_XPL_LIB_TAG_RELOADABLE.get());
        return compiledTag.lazyCompile();
    }

    private boolean isOutputNode(XLangOutputMode outputMode) {
        if (tag.getOutputMode() == XLangOutputMode.node)
            return true;

        // 如果外部环境要求输出node，而标签本身输出模式设置为xml或者html，则也调整输出模式为node
        if (tag.getOutputMode().isXmlOrHtml()) {
            if (tag.isMacro() || outputMode == XLangOutputMode.node)
                return true;
        }
        return false;
    }

    /**
     * 自定义标签编译为一个命名函数，函数名为标签名。函数参数为attrs + slots
     *
     * @return
     */
    private CompiledTag buildCompiledTag(XLangOutputMode outputMode) {
        if (outputMode == null)
            outputMode = tag.getOutputMode();

        ResourceComponentManager.instance().traceDepends(this.tag.resourcePath());

        FunctionModel fn = new FunctionModel();
        fn.setName(getTag().getTagFuncName());
        fn.setLocation(tag.getLocation());
        fn.setArgs(buildArgsModel());

        XLangCompileTool cp = XLang.newCompileTool();
        IXLangCompileScope scope = cp.getScope();
        scope.setCurrentLib(lib);
        scope.setCurrentTag(tag);
        // scope.setSlotDefaults(new HashMap<>());
        scope.setOutputMode(outputMode);

        CompiledTag compiledTag = new CompiledTag();
        // compiledTag.slotDefaults = scope.getSlotDefaults();
        compiledTag.functionModel = fn;

        fn.setInvoker(new LazyCompiledFunction(cp, compiledTag));

        // fn.freeze(true);

        return compiledTag;
    }

    class LazyCompiledFunction implements IEvalFunction {
        private final XLangCompileTool compileTool;
        private final CompiledTag compiledTag;
        private boolean compiled;
        private IEvalFunction compiledFn;
        private RuntimeException compileException;

        public LazyCompiledFunction(XLangCompileTool compileTool, CompiledTag compiledTag) {
            this.compileTool = compileTool;
            this.compiledTag = compiledTag;
        }

        public synchronized void compile() {
            if (compileException != null)
                throw compileException;

            if (compiled)
                return;

            compiled = true;

            ResourceDependencySet deps = new VirtualResourceDependencySet(getTag().getTagFuncName());
            ResourceComponentManager.instance().collectDependsTo(deps, () -> {
                try {
                    ArrowFunctionExpression ast = parseSource(compileTool);
                    compiledFn = compileTool.compileFunction(ast);
                    compiledTag.functionModel.setInvoker(compiledFn);
                    compiledTag.functionModel.freeze(true);
                } catch (Exception e) {
                    compiledTag.deps = deps;
                    compileException = NopException.adapt(e);
                    throw compileException;
                }
                return null;
            });
            compiledTag.deps = deps;
        }

        @Override
        public Object invoke(Object thisObj, Object[] args, IEvalScope scope) {
            if (compiledFn == null) {
                if (compileException != null)
                    throw compileException;

                throw new NopException(ERR_XPL_TAG_FUNC_IS_COMPILING).source(tag).param(ARG_TAG_NAME, tag.getTagName());
            }
            return compiledFn.invoke(thisObj, args, scope);
        }
    }

    private ArrowFunctionExpression parseSource(XLangCompileTool compileTool) {
        XNode source = loadSource();
        XplParseHelper.runImportExprs(compileTool.getScope(), lib.getImportExprs());

        Expression body = compileTool.parseTagBody(source, compileTool.getScope());

        ArrowFunctionExpression func = new ArrowFunctionExpression();
        func.setLocation(this.tag.getLocation());
        func.setFuncName(tag.getTagFuncName());
        func.setParams(buildParamsDecl());
        func.setReturnType(buildReturnDecl());
        func.setBody(body);

        if (tag.isDump()) {
            LOG.info("{}={}", func.getFuncName(), JsonTool.stringify(func, null, "  "));
        }

        return func;
    }

    private List<FunctionArgument> buildArgsModel() {
        List<FunctionArgument> params = new ArrayList<>();
        if (tag.getAttrsVar() != null) {
            params.add(buildAttrsArgModel());
        } else {
            for (IXplTagAttribute attr : this.tag.getAttrs()) {
                params.add(buildArgModel(attr));
            }
        }

        if (tag.getUnknownAttrsVar() != null) {
            params.add(buildUnknownAttrsArgModel());
        }

        for (IXplTagSlot frame : this.tag.getSlots()) {
            params.add(buildArgModel(frame));
        }
        return params;
    }

    private FunctionArgument buildAttrsArgModel() {
        FunctionArgument arg = new FunctionArgument();
        arg.setName(tag.getAttrsVar());
        arg.setType(PredefinedGenericTypes.MAP_STRING_ANY_TYPE);
        return arg;
    }

    private FunctionArgument buildUnknownAttrsArgModel() {
        FunctionArgument arg = new FunctionArgument();
        arg.setName(tag.getUnknownAttrsVar());
        arg.setType(PredefinedGenericTypes.MAP_STRING_ANY_TYPE);
        return arg;
    }

    private FunctionArgument buildArgModel(IXplTagAttribute attr) {
        FunctionArgument arg = new FunctionArgument();
        arg.setName(attr.getVarName());
        arg.setType(attr.getType());
        return arg;
    }

    private FunctionArgument buildArgModel(IXplTagSlot frame) {
        FunctionArgument arg = new FunctionArgument();
        arg.setName(frame.getVarName());
        arg.setType(frame.getType());
        return arg;
    }

    private List<ParameterDeclaration> buildParamsDecl() {
        List<ParameterDeclaration> params = new ArrayList<>();
        if (this.tag.getAttrsVar() != null) {
            params.add(buildAttrsParamDecl());
        } else {
            for (IXplTagAttribute attr : this.tag.getAttrs()) {
                params.add(buildParamDecl(attr));
            }
        }

        if (tag.getUnknownAttrsVar() != null) {
            params.add(buildUnknownAttrParam());
        }

        for (IXplTagSlot frame : this.tag.getSlots()) {
            params.add(buildParamDecl(frame));
        }
        return params;
    }

    private ParameterDeclaration buildUnknownAttrParam() {
        ParameterDeclaration param = new ParameterDeclaration();
        param.setLocation(tag.getLocation());
        param.setName(Identifier.valueOf(tag.getLocation(), tag.getUnknownAttrsVar()));

        param.setType(XLangASTBuilder.buildTypeNode(tag.getLocation(), PredefinedGenericTypes.MAP_STRING_ANY_TYPE));

        param.validate();
        // param.setInitializer(Literal.valueOf(attr.getLocation(), attr.getDefaultValue()));
        return param;
    }

    private ParameterDeclaration buildParamDecl(IXplTagAttribute attr) {
        ParameterDeclaration param = new ParameterDeclaration();
        param.setLocation(attr.getLocation());
        param.setName(Identifier.valueOf(attr.getLocation(), attr.getVarName()));

        if (attr.getType() != null)
            param.setType(XLangASTBuilder.buildTypeNode(attr.getLocation(), attr.getType()));

        param.validate();
        // param.setInitializer(Literal.valueOf(attr.getLocation(), attr.getDefaultValue()));
        return param;
    }

    private ParameterDeclaration buildAttrsParamDecl() {
        ParameterDeclaration param = new ParameterDeclaration();
        param.setLocation(tag.getLocation());
        param.setName(Identifier.valueOf(tag.getLocation(), tag.getAttrsVar()));

        param.setType(XLangASTBuilder.buildTypeNode(tag.getLocation(), PredefinedGenericTypes.MAP_STRING_ANY_TYPE));

        param.validate();
        // param.setInitializer(Literal.valueOf(attr.getLocation(), attr.getDefaultValue()));
        return param;
    }

    private ParameterDeclaration buildParamDecl(IXplTagSlot frame) {
        ParameterDeclaration param = new ParameterDeclaration();
        param.setLocation(frame.getLocation());
        param.setName(Identifier.valueOf(frame.getLocation(), frame.getVarName()));

        // param.setInitializer(Literal.valueOf(frame.getLocation(), null));
        return param;
    }

    private NamedTypeNode buildReturnDecl() {
        return null;
    }

    private XNode loadSource() {
        XNode sourceNode = tag.getSource();
        return sourceNode;
        // if (sourceNode != null) {
        // LOG.debug("nop.xpl.compile-embedded-tag-source:tag={},source={}", tag.getTagName(), sourceNode);
        // return sourceNode;
        // }
        //
        // String resourcePath = getImplResourcePath();
        // IResource resource = VirtualFileSystem.instance().getResource(resourcePath);
        // try {
        // XNode node = XModelInclude.instance().loadActiveNodeFromResource(resource);
        // // 外部实现文件的根节点认为是代码内容，因此为了和内嵌source一致，这里包裹了一层
        // XNode wrapNode = XNode.make("source");
        // wrapNode.appendChild(node);
        // return wrapNode;
        // } catch (NopException e) {
        // e.addXplStack("parse_tag:" + tag.getTagFuncName());
        // throw e;
        // }
    }

    // private String getImplResourcePath() {
    // String path = lib.resourcePath();
    // String ns = XplLibHelper.getNamespaceFromLibPath(path);
    // String resourceName = ns + "/impl_" + tag.getTagName() + ".xpl";
    // if (StringHelper.isEmpty(path))
    // throw new NopException(ERR_XLIB_TAG_NO_SOURCE_DEFINED)
    // .param(ARG_RESOURCE_NAME, resourceName);
    //
    // String resourcePath = StringHelper.absolutePath(path, resourceName);
    // return resourcePath;
    // }
}
