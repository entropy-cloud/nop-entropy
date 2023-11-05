/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpl.utils;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopEvalException;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.CDataText;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.OptionalValue;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.lang.xml.XNode;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.IBeanModel;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.api.IXLangCompileScope;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.ast.BinaryExpression;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.ast.Identifier;
import io.nop.xlang.ast.IfStatement;
import io.nop.xlang.ast.ImportAsDeclaration;
import io.nop.xlang.ast.Literal;
import io.nop.xlang.ast.ObjectExpression;
import io.nop.xlang.ast.PropertyAssignment;
import io.nop.xlang.ast.XLangASTKind;
import io.nop.xlang.ast.XLangASTNode;
import io.nop.xlang.ast.XLangIdentifierDefinition;
import io.nop.xlang.ast.definition.ImportClassDefinition;
import io.nop.xlang.ast.definition.LocalVarDeclaration;
import io.nop.xlang.ast.definition.ScopeVarDefinition;
import io.nop.xlang.expr.ExprConstants;
import io.nop.xlang.expr.ExprPhase;
import io.nop.xlang.expr.IXLangExprParser;
import io.nop.xlang.utils.EvalHelper;
import io.nop.xlang.xpl.IXplCompiler;
import io.nop.xlang.xpl.IXplTagLib;
import io.nop.xlang.xpl.XplConstants;
import io.nop.xlang.xpl.xlib.XplLibHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.core.CoreErrors.ARG_ATTR_VALUE;
import static io.nop.core.CoreErrors.ARG_CLASS_NAME;
import static io.nop.xlang.XLangErrors.ARG_ALIAS;
import static io.nop.xlang.XLangErrors.ARG_ALLOWED_NAMES;
import static io.nop.xlang.XLangErrors.ARG_ATTR_NAME;
import static io.nop.xlang.XLangErrors.ARG_EXPR;
import static io.nop.xlang.XLangErrors.ARG_NODE;
import static io.nop.xlang.XLangErrors.ARG_SLOT_NAME;
import static io.nop.xlang.XLangErrors.ARG_TAG_NAME;
import static io.nop.xlang.XLangErrors.ARG_VALUE;
import static io.nop.xlang.XLangErrors.ERR_EXPR_NOT_LITERAL;
import static io.nop.xlang.XLangErrors.ERR_XLANG_IMPORTED_CLASS_MUST_STARTS_WITH_UPPERCASE;
import static io.nop.xlang.XLangErrors.ERR_XLANG_IMPORT_CLASS_ALIAS_IS_BUILT_IN_TYPE_NAME;
import static io.nop.xlang.XLangErrors.ERR_XPL_ATTR_MUST_NOT_SYS_IDENTIFIER;
import static io.nop.xlang.XLangErrors.ERR_XPL_ATTR_NOT_ALLOW_EXPR;
import static io.nop.xlang.XLangErrors.ERR_XPL_ATTR_NOT_IDENTIFIER;
import static io.nop.xlang.XLangErrors.ERR_XPL_ATTR_NOT_SIMPLE_EXPR;
import static io.nop.xlang.XLangErrors.ERR_XPL_ATTR_NOT_VALID_CLASS_NAME;
import static io.nop.xlang.XLangErrors.ERR_XPL_ATTR_NOT_XML_NAME;
import static io.nop.xlang.XLangErrors.ERR_XPL_ENUM_NO_FACTORY_METHOD;
import static io.nop.xlang.XLangErrors.ERR_XPL_IDENTIFIER_MUST_NOT_KEYWORD;
import static io.nop.xlang.XLangErrors.ERR_XPL_INVALID_ATTR_EXPR;
import static io.nop.xlang.XLangErrors.ERR_XPL_INVALID_VPATH_ATTR;
import static io.nop.xlang.XLangErrors.ERR_XPL_MISSING_ATTR;
import static io.nop.xlang.XLangErrors.ERR_XPL_MISSING_SLOT;
import static io.nop.xlang.XLangErrors.ERR_XPL_NOT_XML_NAME;
import static io.nop.xlang.XLangErrors.ERR_XPL_PARSE_ATTR_INT_FAIL;
import static io.nop.xlang.XLangErrors.ERR_XPL_SCOPE_ARGS_MUST_BE_MAP_EXPR;
import static io.nop.xlang.XLangErrors.ERR_XPL_UNKNOWN_TAG_ATTR;

public class XplParseHelper {
    static final Logger LOG = LoggerFactory.getLogger(XplParseHelper.class);

    static final Object[] EMPTY_VALUES = new Object[0];

    public static void checkNoArgNames(XNode node) {
        checkArgNames(node, Collections.emptyList());
    }

    public static void checkArgNames(XNode node, Collection<String> names) {
        for (String name : node.getAttrNames()) {
            int pos = name.indexOf(':');
            if (pos > 0) {
                if (name.startsWith(XLangConstants.XPL_NS_PREFIX)) {
                    if (!XLangConstants.XPL_ATTRS.contains(name))
                        throw newAttrError(ERR_XPL_UNKNOWN_TAG_ATTR, node, name).param(ARG_ALLOWED_NAMES,
                                XLangConstants.XPL_ATTRS);
                }
                continue;
            }
            if (!names.contains(name))
                throw newAttrError(ERR_XPL_UNKNOWN_TAG_ATTR, node, name).param(ARG_ALLOWED_NAMES, names);
        }
    }

    protected static NopException newAttrError(ErrorCode errorCode, XNode node, String attrName) {
        return new NopEvalException(errorCode).loc(node.attrLoc(attrName)).param(ARG_TAG_NAME, node.getTagName())
                .param(ARG_ATTR_NAME, attrName).param(ARG_NODE, node);
    }

    public static void checkSlotNames(XNode node, Collection<String> names) {
        for (XNode child : node.getChildren()) {
            String tagName = child.getTagName();
            if (!names.contains(tagName))
                throw new NopEvalException(ERR_XPL_UNKNOWN_TAG_ATTR).param(ARG_SLOT_NAME, tagName)
                        .param(ARG_ALLOWED_NAMES, names).param(ARG_NODE, node);
        }
    }

    public static XNode getSlot(XNode node, String name) {
        return node.uniqueChild(name, NopEvalException::new);
    }

    public static XNode requireSlot(XNode node, String name) {
        XNode slot = getSlot(node, name);
        if (slot == null)
            throw new NopEvalException(ERR_XPL_MISSING_SLOT).param(ARG_SLOT_NAME, name).param(ARG_NODE, node);
        return slot;
    }

    private static NopException missingAttr(XNode node, String name) {
        return new NopEvalException(ERR_XPL_MISSING_ATTR).param(ARG_ATTR_NAME, name).param(ARG_NODE, node);
    }

    public static Expression requireAttrExpr(XNode node, String name, IXLangExprParser exprParser,
                                             IXLangCompileScope scope) {
        Expression expr = parseAttrExpr(node, name, exprParser, scope);
        if (expr == null)
            throw missingAttr(node, name);
        return expr;
    }

    public static Expression parseAttrExpr(XNode node, String name, IXLangExprParser exprParser,
                                           IXLangCompileScope scope) {
        ValueWithLocation attr = node.attrValueLoc(name);
        if (attr.isNull())
            return null;

        Object attrValue = attr.getValue();
        if (attrValue instanceof Expression) {
            throw newAttrError(ERR_XPL_ATTR_NOT_ALLOW_EXPR, node, name).param(ARG_EXPR, attrValue);
        }

        if (!(attrValue instanceof String)) {
            return Literal.valueOf(attr.getLocation(), attrValue);
        }

        String str = attr.asString();
        if (str == null || str.length() <= 0)
            return null;

        if (!isExpr(str))
            throw new NopEvalException(ERR_XPL_INVALID_ATTR_EXPR).param(ARG_ATTR_NAME, name).param(ARG_VALUE, str)
                    .param(ARG_NODE, node);

        return exprParser.parseTemplateExpr(attr.getLocation(), str, true, ExprPhase.eval, scope);
    }

    public static Expression parseAttrSimpleExpr(XNode node, String name, IXLangExprParser exprParser,
                                                 IXLangCompileScope scope) {
        ValueWithLocation attr = node.attrValueLoc(name);
        if (attr.isNull())
            return null;

        Object attrValue = attr.getValue();
        if (attrValue instanceof Expression) {
            throw newAttrError(ERR_XPL_ATTR_NOT_ALLOW_EXPR, node, name).param(ARG_VALUE, attrValue);
        }

        if (!(attrValue instanceof String)) {
            return Literal.valueOf(attr.getLocation(), attrValue);
        }

        String str = attr.asString();
        if (str == null || str.length() <= 0)
            return null;

        if (str.startsWith("${"))
            throw newAttrError(ERR_XPL_ATTR_NOT_SIMPLE_EXPR, node, name).param(ARG_VALUE, attrValue);

        return exprParser.parseSimpleExpr(attr.getLocation(), str, scope);
    }

    public static Expression parseAttrTemplateExpr(XNode node, String name, IXLangExprParser exprParser,
                                                   IXLangCompileScope scope) {
        ValueWithLocation attr = node.attrValueLoc(name);
        if (attr.isNull())
            return null;

        Object attrValue = attr.getValue();
        if (attrValue instanceof Expression) {
            return (Expression) attrValue;// throw newAttrError(ERR_XPL_ATTR_NOT_ALLOW_EXPR, node, name).param(ARG_EXPR,
            // attrValue);
        }

        if (!(attrValue instanceof String)) {
            return Literal.valueOf(attr.getLocation(), attrValue);
        }

        String str = attr.asString();
        if (str == null || str.length() <= 0)
            return Literal.stringValue(node.getLocation(), str);

        if (!hasExpr(str))
            return Literal.valueOf(attr.getLocation(), attrValue);

        return exprParser.parseTemplateExpr(attr.getLocation(), str, false, ExprPhase.eval, scope);
    }

    public static boolean hasExpr(String s) {
        if (s == null)
            return false;
        if (s.lastIndexOf('}') <= 0)
            return false;

        if (s.indexOf("${") >= 0)
            return true;

        if (s.indexOf("#{") >= 0)
            return true;

        return false;
    }

    public static boolean isExpr(String s) {
        if (s == null)
            return false;
        if (!s.endsWith("}"))
            return false;
        return s.startsWith("#{") || s.startsWith("${");
    }

    public static boolean isCpExpr(String s) {
        if (s == null)
            return false;
        if (!s.endsWith("}"))
            return false;
        return s.startsWith("#{");
    }

    public static boolean containsCpExpr(String s) {
        if (s == null)
            return false;
        if (s.contains("#{") && s.indexOf('}') >= 0)
            return true;
        return false;
    }

    public static Expression parseContentTemplate(XNode node, IXLangExprParser parser, IXLangCompileScope scope) {
        ValueWithLocation content = node.content();
        try {
            return parseTemplateExpr(content, parser, scope);
        } catch (NopException e) {
            e.addXplStack(node);
            throw e;
        }
    }

    public static Expression parseTemplateExpr(ValueWithLocation content, IXLangExprParser parser,
                                               IXLangCompileScope scope) {
        Object value = content.getValue();
        if (value == null)
            return null;

        if (!(value instanceof String) && !(value instanceof CDataText))
            return Literal.valueOf(content.getLocation(), value);
        String str = value.toString();
        if (StringHelper.isEmpty(str))
            return null;
        if (scope.isIgnoreExpr()) {
            LOG.debug("xpl.ignore-expr-and-return-raw-string:expr={},loc={}", str, content.getLocation());
            return Literal.valueOf(content.getLocation(), str);
        }
        return parser.parseTemplateExpr(content.getLocation(), str, false, ExprPhase.eval, scope);
    }

    public static Expression parseAttrExprOrInt(XNode node, String name, XLangCompileTool tool) {
        return parseAttrExprOrInt(node, name, tool.getCompiler(), tool.getScope());
    }

    public static Expression parseAttrExprOrInt(XNode node, String name, IXLangExprParser exprParser,
                                                IXLangCompileScope scope) {
        ValueWithLocation attr = node.attrValueLoc(name);
        Object value = attr.getValue();
        if (value instanceof String) {
            Integer num = ConvertHelper.stringToInt(value.toString(),
                    err -> new NopEvalException(ERR_XPL_PARSE_ATTR_INT_FAIL).source(attr).param(ARG_ATTR_NAME, name)
                            .param(ARG_VALUE, value).param(ARG_NODE, node));
            return Literal.numberValue(attr.getLocation(), num);
        } else {
            return parseAttrExpr(node, name, exprParser, scope);
        }
    }

    public static String requireAttrClass(XNode node, String name, XLangCompileTool tool) {
        return requireAttrClass(node, name, tool.getCompiler(), tool.getScope());
    }

    public static String requireAttrClass(XNode node, String name, IXLangExprParser parser, IXLangCompileScope scope) {
        String src = getAttrClass(node, name, parser, scope);
        if (StringHelper.isEmpty(src))
            throw missingAttr(node, name);
        return src;
    }

    public static String getAttrClass(XNode node, String name, XLangCompileTool tool) {
        return getAttrClass(node, name, tool.getCompiler(), tool.getScope());
    }

    public static String getAttrClass(XNode node, String name, IXLangExprParser parser, IXLangCompileScope scope) {
        Literal literal = getAttrLiteral(node, name, parser, scope);
        if (literal == null)
            return null;
        String str = literal.getStringValue();
        if (str == null || str.length() <= 0)
            return null;
        if (!StringHelper.isValidClassName(str))
            throw new NopEvalException(ERR_XPL_ATTR_NOT_VALID_CLASS_NAME).param(ARG_ATTR_NAME, name)
                    .param(ARG_ATTR_VALUE, str).param(ARG_NODE, node);
        return str;
    }

    public static String requireAttrVPath(XNode node, String name, IXLangExprParser parser, IXLangCompileScope scope) {
        String src = getAttrVPath(node, name, parser, scope);
        if (StringHelper.isEmpty(src))
            throw missingAttr(node, name);
        return src;
    }

    public static String getAttrVPath(XNode node, String name, XLangCompileTool tool) {
        return getAttrVPath(node, name, tool.getCompiler(), tool.getScope());
    }

    public static String getAttrVPath(XNode node, String name, IXLangExprParser parser, IXLangCompileScope scope) {
        Literal literal = getAttrLiteral(node, name, parser, scope);
        if (literal == null)
            return null;
        String text = literal.getStringValue();
        if (StringHelper.isEmpty(text))
            return null;
        if (!StringHelper.isValidVPath(text))
            throw new NopEvalException(ERR_XPL_INVALID_VPATH_ATTR).param(ARG_ATTR_NAME, name).param(ARG_VALUE, text);

        String resourcePath = literal.resourcePath();
        if (resourcePath == null) {
            resourcePath = node.resourcePath();
        }
        return StringHelper.absolutePath(resourcePath, text);
    }

    public static Literal requireAttrLiteral(XNode node, String name, IXLangExprParser parser,
                                             IXLangCompileScope scope) {
        Literal literal = getAttrLiteral(node, name, parser, scope);
        if (literal == null || literal.getValue() == null)
            throw missingAttr(node, name);
        return literal;
    }

    public static Literal getAttrLiteral(XNode node, String name, IXLangExprParser exprParser,
                                         IXLangCompileScope scope) {
        // tell cpd to start ignoring code - CPD-OFF
        ValueWithLocation attr = node.attrValueLoc(name);
        if (attr.isNull())
            return null;

        Object attrValue = attr.getValue();
        if (attrValue instanceof Expression) {
            throw newAttrError(ERR_XPL_ATTR_NOT_ALLOW_EXPR, node, name).param(ARG_EXPR, attrValue);
        }

        if (!(attrValue instanceof String)) {
            return Literal.valueOf(attr.getLocation(), attrValue);
        }

        String str = attr.asString();
        if (str == null || str.length() <= 0)
            return null;

        if (containsCpExpr(str)) {
            Expression expr = exprParser.parseTemplateExpr(attr.getLocation(), str, false, ExprPhase.compile, scope);
            return attrExprToLiteral(node, name, attr.getLocation(), expr);
        }
        // resume CPD analysis - CPD-ON

        return Literal.valueOf(attr.getLocation(), attrValue);
    }

    public static Literal attrExprToLiteral(XNode node, String attrName, SourceLocation loc, Expression expr) {
        if (expr == null)
            return null;
        if (expr.getASTKind() == XLangASTKind.Literal)
            return (Literal) expr;
        throw new NopEvalException(ERR_EXPR_NOT_LITERAL).loc(loc).param(ARG_ATTR_NAME, attrName).param(ARG_EXPR, expr)
                .param(ARG_NODE, node);
    }

    public static Identifier getAttrIdentifier(XNode node, String name, IXLangExprParser exprParser,
                                               IXLangCompileScope scope) {
        Identifier id = _getAttrIdentifier(node, name, exprParser, scope);
        if (id != null) {
            checkAttrIdentifier(node, name, id.getLocation(), id.getName());
            return id;
        }
        return null;
    }

    private static Identifier _getAttrIdentifier(XNode node, String name, IXLangExprParser exprParser,
                                                 IXLangCompileScope scope) {
        ValueWithLocation attr = node.attrValueLoc(name);
        if (attr.isNull())
            return null;

        Object attrValue = attr.getValue();
        if (attrValue instanceof Expression) {
            throw newAttrError(ERR_XPL_ATTR_NOT_ALLOW_EXPR, node, name).param(ARG_EXPR, attrValue);
        }

        if (!(attrValue instanceof String)) {
            throw new NopEvalException(ERR_XPL_ATTR_NOT_IDENTIFIER).source(attr).param(ARG_ATTR_NAME, name)
                    .param(ARG_VALUE, attrValue).param(ARG_NODE, node);
        }

        String str = attr.asString();
        if (str == null || str.length() <= 0)
            return null;

        if (containsCpExpr(str)) {
            Expression expr = exprParser.parseTemplateExpr(attr.getLocation(), str, false, ExprPhase.compile, scope);
            Literal literal = attrExprToLiteral(node, name, attr.getLocation(), expr);
            if (literal == null)
                return null;
            Object value = literal.getValue();
            if (value == null)
                return null;

            if (!(value instanceof String)) {
                throw new NopEvalException(ERR_XPL_ATTR_NOT_IDENTIFIER).source(attr).param(ARG_ATTR_NAME, name)
                        .param(ARG_VALUE, value).param(ARG_NODE, node);
            }
            return Identifier.valueOf(attr.getLocation(), value.toString());
        }

        return Identifier.valueOf(attr.getLocation(), str);
    }

    public static void checkAttrIdentifier(XNode node, String name, SourceLocation loc, Object value) {
        if (!(value instanceof String) || !StringHelper.isValidJavaVarName((String) value))
            throw new NopEvalException(ERR_XPL_ATTR_NOT_IDENTIFIER).loc(loc).param(ARG_ATTR_NAME, name)
                    .param(ARG_VALUE, value).param(ARG_NODE, node);
    }

    public static void checkNotSysVar(XNode node, String name, Identifier id) {
        if (id == null)
            return;
        if (id.getName().charAt(0) == ExprConstants.PREFIX_SYS_VAR)
            throw new NopEvalException(ERR_XPL_ATTR_MUST_NOT_SYS_IDENTIFIER).source(id).param(ARG_ATTR_NAME, name)
                    .param(ARG_VALUE, id.getName()).param(ARG_NODE, node);

        if (StringHelper.isXLangKeyword(id.getName()))
            throw new NopEvalException(ERR_XPL_IDENTIFIER_MUST_NOT_KEYWORD).source(id).param(ARG_ATTR_NAME, name)
                    .param(ARG_VALUE, id.getName()).param(ARG_NODE, node);
    }

    public static void checkAttrXmlName(XNode node, String name, SourceLocation loc, Object value) {
        if (!(value instanceof String) || !StringHelper.isValidXmlName((String) value, true, true))
            throw new NopEvalException(ERR_XPL_ATTR_NOT_XML_NAME).loc(loc).param(ARG_ATTR_NAME, name)
                    .param(ARG_VALUE, value).param(ARG_NODE, node);
    }

    public static void checkAllXmlName(XNode node, SourceLocation loc, Set<String> names) {
        if (names == null)
            return;

        for (String name : names) {
            if (!StringHelper.isValidXmlName(name, true, true))
                throw new NopEvalException(ERR_XPL_NOT_XML_NAME).loc(loc).param(ARG_VALUE, name).param(ARG_NODE, node);
        }
    }

    public static <T> T getAttrEnum(XNode node, String name, Class<T> clazz, XLangCompileTool tool) {
        return getAttrEnum(node, name, clazz, tool.getCompiler(), tool.getScope());
    }

    public static <T> T requireAttrEnum(XNode node, String name, Class<T> clazz, XLangCompileTool tool) {
        return requireAttrEnum(node, name, clazz, tool.getCompiler(), tool.getScope());
    }

    public static <T> T getAttrEnum(XNode node, String name, Class<T> clazz, IXLangExprParser parser,
                                    IXLangCompileScope scope) {
        Literal literal = getAttrLiteral(node, name, parser, scope);
        if (literal == null)
            return null;
        String str = literal.getStringValue();
        if (StringHelper.isEmpty(str))
            return null;

        IBeanModel beanModel = ReflectionManager.instance().getBeanModelForClass(clazz);
        IFunctionModel factoryMethod = beanModel.getFactoryMethod();
        if (factoryMethod != null) {
            return (T) factoryMethod.call1(null, str, scope);
        }
        throw new NopEvalException(ERR_XPL_ENUM_NO_FACTORY_METHOD).param(ARG_CLASS_NAME, clazz.getName())
                .param(ARG_NODE, node);
    }

    public static <T> T requireAttrEnum(XNode node, String name, Class<T> clazz, IXLangExprParser parser,
                                        IXLangCompileScope scope) {
        T attrEnum = getAttrEnum(node, name, clazz, parser, scope);
        if (attrEnum == null) {
            throw missingAttr(node, name);
        }
        return attrEnum;
    }

    public static Boolean getAttrBool(XNode node, String name) {
        ValueWithLocation attr = node.attrValueLoc(name);
        Object value = _getAttrValue(node, name, attr);
        if (value == null)
            return null;
        return ConvertHelper.toBoolean(value,
                errCode -> new NopEvalException(errCode).source(attr).param(ARG_NODE, node).param(ARG_ATTR_NAME, name));
    }

    static Object _getAttrValue(XNode node, String name, ValueWithLocation attr) {
        if (attr.isNull())
            return null;
        Object value = attr.getValue();
        if (value == null)
            return null;
        if (value instanceof Expression) {
            throw newAttrError(ERR_XPL_ATTR_NOT_ALLOW_EXPR, node, name).param(ARG_EXPR, value);
        }
        return value;
    }

    public static boolean getAttrBool(XNode node, String name, boolean defaultValue) {
        Boolean b = getAttrBool(node, name);
        if (b == null)
            return defaultValue;
        return b;
    }

    public static Set<String> getAttrCsvSet(XNode node, String name) {
        ValueWithLocation attr = node.attrValueLoc(name);
        Object value = _getAttrValue(node, name, attr);
        if (value == null)
            return null;
        return ConvertHelper.toCsvSet(value,
                errCode -> new NopEvalException(errCode).source(attr).param(ARG_NODE, node).param(ARG_ATTR_NAME, name));
    }

    public static Identifier getAttrXmlName(XNode node, String name, IXLangExprParser exprParser,
                                            IXLangCompileScope scope) {
        Identifier id = _getAttrIdentifier(node, name, exprParser, scope);
        if (id != null) {
            checkAttrXmlName(node, name, id.getLocation(), id.getName());
            return id;
        }
        return null;
    }

    public static Expression notNull(SourceLocation loc, Expression expr) {
        if (expr == null)
            return Literal.nullValue(loc);
        return expr;
    }

    public static Expression runMacroExpr(Expression expr, IXLangExprParser cp, IXLangCompileScope scope) {
        scope.enterMacro();
        IExecutableExpression executable;
        try {
            executable = cp.buildExecutable(expr, false, scope);
            if (executable == null)
                return null;

            Object value = XLang.execute(executable, scope);
            if (value == null)
                return null;
            return Literal.valueOf(executable.getLocation(), value);
        } finally {
            scope.leaveMacro();
        }
    }

    public static OptionalValue staticValue(Expression expr) {
        if (expr == null)
            return OptionalValue.NULL;

        if (expr.getASTKind() == XLangASTKind.Literal)
            return OptionalValue.of(((Literal) expr).getValue());

        if (expr.getASTKind() == XLangASTKind.Identifier) {
            Identifier id = (Identifier) expr;
            XLangIdentifierDefinition var = id.getResolvedDefinition();
            if (var instanceof LocalVarDeclaration) {
                OptionalValue value = ((LocalVarDeclaration) var).getConstValue();
                return value;
            }
            return OptionalValue.UNDEFINED;
        }

        if (expr instanceof BinaryExpression) {
            BinaryExpression binary = (BinaryExpression) expr;
            OptionalValue left = staticValue(binary.getLeft());
            if (!left.isPresent())
                return OptionalValue.UNDEFINED;

            OptionalValue right = staticValue(binary.getRight());
            if (!right.isPresent())
                return OptionalValue.UNDEFINED;

            return OptionalValue.of(EvalHelper.binaryOp(binary.getOperator(), left, right));
        }

        return OptionalValue.UNDEFINED;
    }

    public static Expression simplifiedIfStatement(SourceLocation loc, Expression test, Expression consequence,
                                                   Expression alternate, boolean ternaryExpr) {
        OptionalValue value = staticValue(test);
        if (value.isPresent()) {
            if (value.asTruthy())
                return consequence;
            return alternate;
        }
        return IfStatement.valueOf(loc, test, consequence, alternate, ternaryExpr);
    }

    public static Runnable registerMacroVar(IXLangCompileScope scope, SourceLocation loc, String varName,
                                            IGenericType type, Object value) {
        ScopeVarDefinition def = ScopeVarDefinition.mutable(varName, type);
        def.setLocation(loc);
        // 如果与已注册变量重名，则抛出异常
        scope.registerScopeVarDefinition(def, true);

        ValueWithLocation varLoc = scope.recordValueLocation(varName);
        scope.setLocalValue(loc, varName, value);

        return () -> {
            scope.unregisterScopeVarDefinition(def, true);
            scope.restoreValueLocation(varName, varLoc);
        };
    }

    public static Object getCpValue(Expression expr) {
        if (expr == null)
            return null;
        return ((Literal) expr).getValue();
    }

    public static boolean notCpValue(Expression expr) {
        return expr != null && !(expr instanceof Literal);
    }

    public static void runImportExprs(IXLangCompileScope scope, List<ImportAsDeclaration> importExprs) {
        if (importExprs != null) {
            for (ImportAsDeclaration importExpr : importExprs) {
                runImportExpr(scope, importExpr);
            }
        }
    }

    public static void runImportExpr(IXLangCompileScope scope, ImportAsDeclaration node) {
        String as = node.makeLocal().getName();

        if (node.isImportLib()) {
            String libPath = node.getImportLibPath();
            libPath = StringHelper.absolutePath(node.resourcePath(), libPath);

            IXplTagLib lib = XplLibHelper.loadLib(libPath);
            scope.addLib(as, lib);
        } else {
            IClassModel classModel = scope.getClassModelLoader().loadClassModel(node.getImportClassName());
            if (Character.isLowerCase(as.charAt(0))) {
                throw new NopEvalException(ERR_XLANG_IMPORTED_CLASS_MUST_STARTS_WITH_UPPERCASE).param(ARG_ALIAS, as)
                        .source(node);
            }
            IGenericType type = PredefinedGenericTypes.getPredefinedType(as);
            if (type != null && !type.getSimpleClassName().equals(as))
                throw new NopEvalException(ERR_XLANG_IMPORT_CLASS_ALIAS_IS_BUILT_IN_TYPE_NAME).param(ARG_ALIAS, as)
                        .source(node);
            scope.addImportedClass(as, new ImportClassDefinition(classModel));
        }
    }

    public static Map<String, Expression> parseSlotArgs(XNode node, IXplCompiler cp, IXLangCompileScope scope) {
        Expression bindingExpr = parseAttrSimpleExpr(node, XplConstants.ATTR_XPL_SLOT_ARGS, cp, scope);
        if (bindingExpr == null) {
            return Collections.emptyMap();
        }

        if (!(bindingExpr instanceof ObjectExpression)) {
            throw new NopEvalException(ERR_XPL_SCOPE_ARGS_MUST_BE_MAP_EXPR)
                    .loc(node.attrLoc(XplConstants.ATTR_XPL_SLOT_ARGS)).param(ARG_NODE, node);
        }

        Map<String, Expression> ret = new HashMap<>();
        ObjectExpression obj = (ObjectExpression) bindingExpr;
        for (XLangASTNode prop : obj.getProperties()) {
            if (!(prop instanceof PropertyAssignment)) {
                throw new NopEvalException(ERR_XPL_SCOPE_ARGS_MUST_BE_MAP_EXPR)
                        .loc(node.attrLoc(XplConstants.ATTR_XPL_SLOT_ARGS)).param(ARG_NODE, node);
            }
            PropertyAssignment assign = (PropertyAssignment) prop;
            if (!(assign.getKey() instanceof Literal)) {
                throw new NopEvalException(ERR_XPL_SCOPE_ARGS_MUST_BE_MAP_EXPR)
                        .loc(node.attrLoc(XplConstants.ATTR_XPL_SLOT_ARGS)).param(ARG_NODE, node);
            }

            String name = ((Literal) assign.getKey()).getStringValue();
            Expression value = assign.getValue();
            value.detach();
            ret.put(name, value);
        }
        return ret;
    }
}