/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpl.tags;

import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.CallExpression;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.Literal;
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.IXplTag;
import io.nop.xlang.xpl.IXplTagSlot;
import io.nop.xlang.xpl.IXplTagSlotArg;
import io.nop.xlang.xpl.XplConstants;
import io.nop.xlang.xpl.XplSlotType;
import io.nop.xlang.xpl.utils.XplParseHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static io.nop.xlang.XLangErrors.ARG_ALLOWED_NAMES;
import static io.nop.xlang.XLangErrors.ARG_ARG_NAME;
import static io.nop.xlang.XLangErrors.ARG_NODE;
import static io.nop.xlang.XLangErrors.ARG_SLOT_NAME;
import static io.nop.xlang.XLangErrors.ARG_SLOT_TYPE;
import static io.nop.xlang.XLangErrors.ARG_TAG_NAME;
import static io.nop.xlang.XLangErrors.ERR_XPL_RENDER_TAG_ONLY_ALLOW_IN_TAG_IMPL;
import static io.nop.xlang.XLangErrors.ERR_XPL_TAG_SLOT_NOT_RENDERER;
import static io.nop.xlang.XLangErrors.ERR_XPL_TAG_UNKNOWN_SLOT_ARG;
import static io.nop.xlang.XLangErrors.ERR_XPL_UNKNOWN_TAG_SLOT;
import static io.nop.xlang.ast.XLangASTBuilder.ifStatement;

/**
 * 编译自定义标签的实现代码中标记了xpl:slot属性的节点。例如
 *
 * <pre>{@code
 * <div xpl:slot="a" xpl:slotBinding="{a:1,b:2}">
 *    <content/>
 * </div>
 * }</pre>
 * <p>
 * 将会被编译为表达式
 *
 * <pre>{@code
 * if(slot_a){
 *    slot_a({a:1,b:2})
 * }else{
 *     <div>
 *         <content/>
 *     </div>
 * }}
 * </pre>
 */
public class XplSlotProcessor {

    public static final XplSlotProcessor INSTANCE = new XplSlotProcessor();

    public static Expression processXplSlot(XNode node, IXplCompiler cp, IXLangCompileScope scope,
                                            Function<XNode, Expression> compileDefault) {
        ValueWithLocation slotAttr = node.attrValueLoc(XplConstants.ATTR_XPL_SLOT);
        String slotName = slotAttr.asString();
        if (StringHelper.isEmpty(slotName))
            return null;

        IXplTag tag = scope.getCurrentTag();
        if (tag == null)
            throw new NopEvalException(ERR_XPL_RENDER_TAG_ONLY_ALLOW_IN_TAG_IMPL).param(ARG_NODE, node);

        node = node.cloneInstance();

        IXplTagSlot slot = tag.getSlot(slotName);
        if (slot == null)
            throw new NopEvalException(ERR_XPL_UNKNOWN_TAG_SLOT).param(ARG_NODE, node)
                    .param(ARG_TAG_NAME, tag.getTagName()).param(ARG_SLOT_NAME, slotName);

        if (slot.getSlotType() != XplSlotType.renderer)
            throw new NopEvalException(ERR_XPL_TAG_SLOT_NOT_RENDERER).param(ARG_TAG_NAME, tag.getTagName())
                    .param(ARG_SLOT_NAME, slot.getName()).param(ARG_SLOT_TYPE, slot.getSlotType())
                    .param(ARG_NODE, node);
        String varName = slot.getVarName();

        SourceLocation loc = node.getLocation();
        Identifier slotVar = Identifier.valueOf(loc, varName);
        List<Expression> bindingArgs = parseSlotArgs(node, slot, cp, scope);

        node.removeAttr(XplConstants.ATTR_XPL_SLOT);
        node.removeAttr(XplConstants.ATTR_XPL_SLOT_ARGS);

        Expression defaultExpr = compileDefault.apply(node);
        if (defaultExpr != null)
            defaultExpr.detach();

        /**
         * 如果指定了slot renderer，则执行slot renderer, 否则执行当前标签
         */
        CallExpression render = CallExpression.valueOf(loc, slotVar, bindingArgs);
        return ifStatement(node.getLocation(), slotVar.deepClone(), render, defaultExpr);
    }

    static List<Expression> parseSlotArgs(XNode node, IXplTagSlot slot, IXplCompiler cp, IXLangCompileScope scope) {
        Map<String, Expression> scopeBinding = XplParseHelper.parseSlotArgs(node, cp, scope);
        if (scopeBinding.isEmpty() && slot.getArgs().isEmpty())
            return Collections.emptyList();

        for (String name : scopeBinding.keySet()) {
            if (slot.getArg(name) == null)
                throw new NopEvalException(ERR_XPL_TAG_UNKNOWN_SLOT_ARG).param(ARG_NODE, node).param(ARG_ARG_NAME, name)
                        .param(ARG_ALLOWED_NAMES, slot.keySet_args());
        }

        List<Expression> binding = new ArrayList<>(slot.getArgs().size());
        for (IXplTagSlotArg arg : slot.getArgs()) {
            Expression argExpr = scopeBinding.get(arg.getName());
            if (argExpr == null) {
                argExpr = Literal.valueOf(arg.getLocation(), arg.getDefaultValue());
            }

            binding.add(argExpr);
        }

        return binding;
    }
}
