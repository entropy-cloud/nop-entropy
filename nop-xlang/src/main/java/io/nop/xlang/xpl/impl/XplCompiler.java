/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpl.impl;

import io.nop.api.core.config.AppConfig;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.Literal;
import io.nop.xlang.ast.XLangASTBuilder;
import io.nop.xlang.ast.XLangASTKind;
import io.nop.xlang.ast.XLangOutputMode;
import io.nop.xlang.expr.XLangExprParser;
import io.nop.xlang.scope.XLangCompileScope;
import io.nop.xlang.script.IScriptCompiler;
import io.nop.xlang.script.ScriptCompilerRegistry;
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.IXplTagCompiler;
import io.nop.xlang.xpl.IXplTagLib;
import io.nop.xlang.xpl.XLangParseBuffer;
import io.nop.xlang.xpl.output.IXplUnknownTagCompiler;
import io.nop.xlang.xpl.output.OutputModelHandlers;
import io.nop.xlang.xpl.tags.InfoTagCompiler;
import io.nop.xlang.xpl.tags.InternalTagCompilers;
import io.nop.xlang.xpl.tags.XplSlotProcessor;
import io.nop.xlang.xpl.utils.XplParseHelper;
import io.nop.xlang.xpl.xlib.XplLibHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.xlang.XLangErrors.ARG_CLASS_NAME;
import static io.nop.xlang.XLangErrors.ARG_DECORATED;
import static io.nop.xlang.XLangErrors.ARG_LANG;
import static io.nop.xlang.XLangErrors.ARG_NODE;
import static io.nop.xlang.XLangErrors.ARG_SCRIPT_LANGS;
import static io.nop.xlang.XLangErrors.ARG_TAG_NAME;
import static io.nop.xlang.XLangErrors.ERR_XPL_ATTRS_EXPR_VALUE_NOT_MAP;
import static io.nop.xlang.XLangErrors.ERR_XPL_DECORATOR_CHILD_NOT_ALLOW_MULTIPLE_DECORATED;
import static io.nop.xlang.XLangErrors.ERR_XPL_DECORATOR_CHILD_NOT_ALLOW_TEXT_NODE;
import static io.nop.xlang.XLangErrors.ERR_XPL_NOT_ALLOW_MULTIPLE_DECORATOR_CHILD;
import static io.nop.xlang.XLangErrors.ERR_XPL_NOT_ALLOW_UNKNOWN_TAG;
import static io.nop.xlang.XLangErrors.ERR_XPL_UNKNOWN_SCRIPT_LANG;
import static io.nop.xlang.xpl.XplConstants.ATTR_XPL_ALLOW_UNKNOWN_TAG;
import static io.nop.xlang.xpl.XplConstants.ATTR_XPL_ATTRS;
import static io.nop.xlang.xpl.XplConstants.ATTR_XPL_DISABLE_NS;
import static io.nop.xlang.xpl.XplConstants.ATTR_XPL_DUMP;
import static io.nop.xlang.xpl.XplConstants.ATTR_XPL_ENABLE_NS;
import static io.nop.xlang.xpl.XplConstants.ATTR_XPL_IF;
import static io.nop.xlang.xpl.XplConstants.ATTR_XPL_IGNORE_EXPR;
import static io.nop.xlang.xpl.XplConstants.ATTR_XPL_IGNORE_TAG;
import static io.nop.xlang.xpl.XplConstants.ATTR_XPL_INVERT;
import static io.nop.xlang.xpl.XplConstants.ATTR_XPL_IS;
import static io.nop.xlang.xpl.XplConstants.ATTR_XPL_LIB;
import static io.nop.xlang.xpl.XplConstants.ATTR_XPL_OUTPUT_MODE;
import static io.nop.xlang.xpl.XplConstants.ATTR_XPL_RETURN;
import static io.nop.xlang.xpl.XplConstants.ATTR_XPL_SKIP_IF;
import static io.nop.xlang.xpl.XplConstants.TAG_XPL_DECORATOR;
import static io.nop.xlang.xpl.XplConstants.XDSL_NS;
import static io.nop.xlang.xpl.XplConstants.XPL_CORE_NS;
import static io.nop.xlang.xpl.XplConstants.XPL_INFO_NS;
import static io.nop.xlang.xpl.XplConstants.XPL_MACRO_NS;
import static io.nop.xlang.xpl.XplConstants.XPL_NS;
import static io.nop.xlang.xpl.XplConstants.X_NS;
import static io.nop.xlang.xpl.utils.XplParseHelper.getAttrBool;
import static io.nop.xlang.xpl.utils.XplParseHelper.getAttrCsvSet;
import static io.nop.xlang.xpl.utils.XplParseHelper.getAttrEnum;
import static io.nop.xlang.xpl.utils.XplParseHelper.getAttrIdentifier;
import static io.nop.xlang.xpl.utils.XplParseHelper.parseAttrSimpleExpr;
import static io.nop.xlang.xpl.utils.XplParseHelper.parseAttrTemplateExpr;
import static io.nop.xlang.xpl.utils.XplParseHelper.simplifiedIfStatement;

public class XplCompiler extends XLangExprParser implements IXplCompiler {
    static final Logger LOG = LoggerFactory.getLogger(XplCompiler.class);

    @Override
    public Expression parseTag(XNode node, IXLangCompileScope scope) {
        XLangParseBuffer buf = new XLangParseBuffer();
        parseTag(buf, node, scope);
        return buf.getResult();
    }

    @Override
    public void parseTag(XLangParseBuffer buf, XNode node, IXLangCompileScope scope) {
        Set<String> enableNs = getAttrCsvSet(node, ATTR_XPL_ENABLE_NS);
        if (enableNs != null) {
            scope.enableNs(enableNs);
        }
        Set<String> disableNs = getAttrCsvSet(node, ATTR_XPL_DISABLE_NS);
        if (disableNs != null) {
            scope.disableNs(disableNs);
        }

        boolean dump = node.attrBoolean(ATTR_XPL_DUMP);
        XLangParseBuffer oldBuf = buf;
        if (dump) {
            buf = new XLangParseBuffer();
        }

        if (scope.isNsEnabled(XPL_NS)) {
            Expression expr = XplSlotProcessor.processXplSlot(node, this, scope, n -> {
                XLangParseBuffer b = new XLangParseBuffer();
                _parseTag(b, n, scope);
                return b.getResult();
            });

            if (expr != null) {
                buf.add(expr);
            } else {
                _parseTag(buf, node, scope);
            }
        } else {
            IXplTagCompiler tagCompiler = getTagCompiler(node, node.getTagName(), false, scope);
            tagCompiler.parseTag(buf, node, this, scope);
        }

        if (dump) {
            Expression expr = buf.getResult();
            LOG.info("compiledTagExpr={}", JsonTool.stringify(expr, null, "  "));
            oldBuf.add(expr);
        }
    }

    private void _parseTag(XLangParseBuffer buf, XNode node, IXLangCompileScope scope) {
        node = spreadAttrs(node, scope);
        // Expression frameExpr = XplFrameHandler.processFrame(node, this, scope);
        // if (frameExpr != null) {
        // buf.add(frameExpr);
        // return;
        // }

        XNode decorated = decorate(node);
        if (decorated != node) {
            parseTag(buf, decorated, scope);
            return;
        }

        XLangOutputMode outputMode = getAttrEnum(node, ATTR_XPL_OUTPUT_MODE, XLangOutputMode.class, this, scope);

        XLangOutputMode oldOutputMode = null;
        if (outputMode != null) {
            oldOutputMode = scope.getOutputMode();
            scope.setOutputMode(outputMode);
        }

        boolean ignoreExpr = getAttrBool(node, ATTR_XPL_IGNORE_EXPR, scope.isIgnoreExpr());
        boolean ignoreTag = getAttrBool(node, ATTR_XPL_IGNORE_TAG, scope.isIgnoreTag());
        boolean allowUnknownTag = getAttrBool(node, ATTR_XPL_ALLOW_UNKNOWN_TAG, scope.isAllowUnknownTag());

        if (!ignoreTag) {
            addXplLibs(node, scope);
        }

        Expression xplIs = parseAttrTemplateExpr(node, ATTR_XPL_IS, this, scope);
        Expression skipIf = parseAttrSimpleExpr(node, ATTR_XPL_SKIP_IF, this, scope);
        Expression xplIf = parseAttrSimpleExpr(node, ATTR_XPL_IF, this, scope);
        boolean invert = getAttrBool(node, ATTR_XPL_INVERT, false);
        Identifier returnVar = getAttrIdentifier(node, ATTR_XPL_RETURN, this, scope);

        boolean oldIgnoreExpr = scope.isIgnoreExpr();
        boolean oldAllowUnknownTag = scope.isAllowUnknownTag();
        boolean oldIgnoreTag = scope.isIgnoreTag();

        scope.setAllowUnknownTag(allowUnknownTag);
        scope.setIgnoreExpr(ignoreExpr);
        scope.setIgnoreTag(ignoreTag);

        try {
            String tagName = node.getTagName();
            if (xplIs != null) {
                if (xplIs.getASTKind() == XLangASTKind.Identifier) {
                    tagName = ((Identifier) xplIs).getName();
                } else if (xplIs.getASTKind() == XLangASTKind.Literal) {
                    tagName = ((Literal) xplIs).getStringValue();
                } else {
                    // 标签名为表达式，则只能使用unknownCompiler来编译
                    ignoreTag = true;
                }
            }

            IXplTagCompiler tagCompiler = getTagCompiler(node, tagName, ignoreTag, scope);

            Expression expr = tagCompiler.parseTag(node, this, scope);
            if (skipIf != null) {
                // skiIf返回true时需要跳过当前节点，只编译body部分
                Expression bodyExpr = parseTagBody(node, scope);
                expr = simplifiedIfStatement(node.getLocation(), skipIf, bodyExpr, expr);
            }

            // 对应执行顺序为 if(xplIf) returnVar = invert(expr)

            if (invert) {
                expr = XLangASTBuilder.not(node.getLocation(), expr);
            }

            if (returnVar != null) {
                expr = XLangASTBuilder.varDecl(node.getLocation(), returnVar, expr);
            }

            if (xplIf != null) {
                expr = simplifiedIfStatement(node.getLocation(), xplIf, expr, null);
            }
            buf.add(expr);
        } finally {
            if (oldOutputMode != outputMode) {
                scope.setOutputMode(oldOutputMode);
            }
            if (oldAllowUnknownTag != allowUnknownTag)
                scope.setAllowUnknownTag(oldAllowUnknownTag);

            if (oldIgnoreExpr != ignoreExpr)
                scope.setIgnoreExpr(oldIgnoreExpr);

            if (oldIgnoreTag != ignoreTag) {
                scope.setIgnoreTag(oldIgnoreTag);
            }
        }
    }

    private IXplTagCompiler getTagCompiler(XNode node, String tagName, boolean ignoreTag, IXLangCompileScope scope) {
        if (ignoreTag) {
            // if (!scope.isAllowUnknownTag()) {
            // throw new NopEvalException(ERR_XPL_NOT_ALLOW_UNKNOWN_TAG)
            // .param(ARG_TAG_NAME, tagName).param(ARG_NODE, node);
            // }
            return getUnknownTagCompiler(node, scope);
        }
        String ns = XplLibHelper.getNamespaceFromTagName(tagName);
        if (scope.isNsEnabled(ns)) {
            if (ns.equals(XPL_CORE_NS) || ns.equals(XPL_MACRO_NS)) {
                IXplTagCompiler tagCompiler = InternalTagCompilers.getTagCompiler(tagName);
                if (tagCompiler == null) {
                    throw new NopEvalException(ERR_XPL_NOT_ALLOW_UNKNOWN_TAG).param(ARG_TAG_NAME, tagName)
                            .param(ARG_NODE, node);
                }
                return tagCompiler;
            }

            if (ns.equals(XPL_INFO_NS)) {
                return InfoTagCompiler.INSTANCE;
            }
            IXplTagCompiler tagCompiler = scope.getTagCompiler(tagName);
            if (tagCompiler != null)
                return tagCompiler;

            if (!isAllowNs(ns) && !scope.isAllowUnknownTag()) {
                throw new NopEvalException(ERR_XPL_NOT_ALLOW_UNKNOWN_TAG).param(ARG_TAG_NAME, tagName).param(ARG_NODE,
                        node);
            }
        }

        return getUnknownTagCompiler(node, scope);
    }

    boolean isAllowNs(String ns) {
        if (XplLibHelper.isDefaultNs(ns))
            return true;
        return X_NS.equals(ns) || XDSL_NS.equals(ns);
    }

    private IXplUnknownTagCompiler getUnknownTagCompiler(XNode node, IXLangCompileScope scope) {
        IXplUnknownTagCompiler tagCompiler = OutputModelHandlers.getHandler(scope.getOutputMode());
        if (tagCompiler == null)
            throw new IllegalStateException(
                    "nop.err.xlang.no-default-compiler-for-outputMode:" + scope.getOutputMode() + ",node=" + node);
        return tagCompiler;
    }

    private void addXplLibs(XNode node, IXLangCompileScope scope) {
        Set<String> xplLibs = getAttrCsvSet(node, ATTR_XPL_LIB);
        if (xplLibs == null)
            return;

        for (String xplLib : xplLibs) {
            int pos = xplLib.indexOf('=');
            if (pos < 0) {
                String ns = XplLibHelper.getNamespaceFromLibPath(xplLib);
                loadLib(node.getLocation(), ns, xplLib, scope);
            } else {
                String as = xplLib.substring(0, pos).trim();
                loadLib(node.getLocation(), as, xplLib.substring(pos + 1).trim(), scope);
            }
        }
    }

    /**
     * 处于编译期xpl:attrs属性设置。将编译期传递的属性集合分解为具体的节点属性
     *
     * @return 返回处理后的节点或者是原节点
     */
    private XNode spreadAttrs(XNode node, IXLangCompileScope scope) {
        ValueWithLocation attr = node.attrValueLoc(ATTR_XPL_ATTRS);
        if (attr.isNull())
            return node;
        if (!XplParseHelper.isCpExpr(attr.asString())) {
            return node;
        }

        Expression expr = parseAttrSimpleExpr(node, ATTR_XPL_ATTRS, this, scope);
        SourceLocation loc = node.attrLoc(ATTR_XPL_ATTRS);
        Literal literal = XplParseHelper.attrExprToLiteral(node, ATTR_XPL_ATTRS, loc, expr);
        XNode ret = node.cloneInstance();
        ret.removeAttr(ATTR_XPL_ATTRS);

        if (literal == null)
            return ret;

        Object value = ret.getContentValue();
        if (value == null)
            return ret;

        if (!(value instanceof Map))
            throw new NopEvalException(ERR_XPL_ATTRS_EXPR_VALUE_NOT_MAP).loc(loc).param(ARG_NODE, node)
                    .param(ARG_CLASS_NAME, value.getClass().getName());

        Map<String, Object> map = (Map<String, Object>) value;
        XplParseHelper.checkAllXmlName(node, loc, map.keySet());

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!ret.hasAttr(entry.getKey())) {
                ret.setAttr(entry.getKey(), entry.getValue());
            } else {
                LOG.debug("xpl.ignore-existing-cp-attr:name={},node={}", entry.getKey(), node);
            }
        }
        return ret;
    }

    /**
     * 处理<x:decorator>机制
     *
     * @return 如果存在decorator，则返回变换后的节点，否则返回原节点
     */
    private XNode decorate(XNode node) {
        if (node.isTextNode())
            return node;

        XNode decorator = node.childByTag(XLangConstants.TAG_XPL_DECORATOR);
        if (decorator == null)
            return node;

        XNode originalDecorator = decorator;
        node = node.cloneInstance();

        int childIndex = decorator.childIndex();
        decorator = node.child(childIndex);
        decorator.detach();

        if (node.childByTag(TAG_XPL_DECORATOR) != null) {
            throw new NopEvalException(ERR_XPL_NOT_ALLOW_MULTIPLE_DECORATOR_CHILD).param(ARG_NODE, originalDecorator);
        }

        XNode ret = node;
        for (int i = decorator.getChildCount() - 1; i >= 0; i--) {
            XNode child = decorator.child(i);
            if (child.isTextNode())
                throw new NopEvalException(ERR_XPL_DECORATOR_CHILD_NOT_ALLOW_TEXT_NODE).param(ARG_NODE, child);
            List<XNode> decorated = child.findAllByTag(XLangConstants.TAG_XPL_DECORATED);
            if (decorated.size() > 1) {
                throw new NopEvalException(ERR_XPL_DECORATOR_CHILD_NOT_ALLOW_MULTIPLE_DECORATED).param(ARG_DECORATED,
                        decorated);
            } else if (decorated.isEmpty()) {
                child.appendChild(ret);
            } else {
                decorated.get(0).replaceBy(ret);
            }
            child.detach();
            ret = child;
        }

        if (AppConfig.isDebugMode())
            LOG.debug(ret.getDumpString("xpl.tag_decorated"));

        return ret;
    }

    @Override
    public Expression parseTagBody(XNode node, IXLangCompileScope scope) {
        XLangParseBuffer buf = new XLangParseBuffer();
        parseTagBody(buf, node, scope);
        return buf.getResult();
    }

    @Override
    public void parseTagBody(XLangParseBuffer buf, XNode node, IXLangCompileScope scope) {
        if (node.hasChild()) {
            scope.enterBlock(false);
            try {
                for (XNode child : node.getChildren()) {
                    parseTag(buf, child, scope);
                }
            } finally {
                scope.leaveBlock(false);
            }
        } else if (node.hasContent()) {
            getUnknownTagCompiler(node, scope).parseContent(buf, node, this, scope);
        }
    }

    @Override
    public IXplTagLib loadLib(SourceLocation loc, String namespace, String src, IXLangCompileScope scope) {
        try {
            IXplTagLib lib = XplLibHelper.loadLib(src);
            scope.addLib(namespace, lib);
            return lib;
        } catch (NopException e) {
            e.addXplStack(loc);
            throw e;
        }
    }

    @Override
    public IEvalFunction compileScript(SourceLocation loc, String lang, String source, IXLangCompileScope scope) {
        IScriptCompiler compiler = ScriptCompilerRegistry.instance().getCompiler(lang);
        if (compiler == null)
            throw new NopEvalException(ERR_XPL_UNKNOWN_SCRIPT_LANG).param(ARG_LANG, lang).param(ARG_SCRIPT_LANGS,
                    ScriptCompilerRegistry.instance().getRegisteredLanguages());
        return compiler.compile(loc, source, scope);
    }

    @Override
    public IXLangCompileScope newCompileScope() {
        XLangCompileScope scope = new XLangCompileScope(this);
        scope.setExpressionExecutor(EvalExprProvider.getGlobalExecutor());
        return scope;
    }
}