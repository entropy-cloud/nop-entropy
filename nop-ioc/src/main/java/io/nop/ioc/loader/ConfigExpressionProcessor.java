/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ioc.loader;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.MutableString;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IExecutableExpression;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.type.IGenericType;
import io.nop.ioc.IocConstants;
import io.nop.ioc.impl.BeanDefinition;
import io.nop.ioc.impl.IBeanPropValueResolver;
import io.nop.ioc.impl.resolvers.BeanContainerValueResolver;
import io.nop.ioc.impl.resolvers.BeanTypeValueResolver;
import io.nop.ioc.impl.resolvers.ConcatValueResolver;
import io.nop.ioc.impl.resolvers.ConfigValueResolver;
import io.nop.ioc.impl.resolvers.ExpressionValueResolver;
import io.nop.ioc.impl.resolvers.FixedValueResolver;
import io.nop.xlang.api.ExprEvalAction;
import io.nop.xlang.api.XLang;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.expr.simple.SimpleExprParser;
import io.nop.xlang.xpl.IXplCompiler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import static io.nop.ioc.IocErrors.ARG_BEAN_NAME;
import static io.nop.ioc.IocErrors.ARG_EXPR;
import static io.nop.ioc.IocErrors.ARG_PROP_NAME;
import static io.nop.ioc.IocErrors.ARG_TRACE;
import static io.nop.ioc.IocErrors.ARG_VALUE;
import static io.nop.ioc.IocErrors.ERR_IOC_INVALID_BEAN_CONFIG_VALUE;
import static io.nop.ioc.IocErrors.ERR_IOC_INVALID_BIND_EXPR;

public class ConfigExpressionProcessor {
    public static final ConfigExpressionProcessor INSTANCE = new ConfigExpressionProcessor();

    public IBeanPropValueResolver process(BeanDefinition bean, SourceLocation loc, String propName, String expr,
                                          IGenericType expectedType) {
        if (containsSpringExpr(expr)) {
            return parseSpringExpr(bean, loc, propName, expr);
        }

        if (containsBindExpr(expr)) {
            return parseBindExpr(bean, loc, propName, expr);
        }

        if (expr.charAt(0) == '@') {
            return parsePrefixExpr(bean, propName, expr, expectedType);
        }

        return buildValue(loc, expr, expectedType);
    }

    IBeanPropValueResolver parseSpringExpr(BeanDefinition bean, SourceLocation loc, String propName, String expr) {
        TextScanner sc = TextScanner.fromString(loc, expr);
        List<String> configVars = new ArrayList<>();
        List<IBeanPropValueResolver> resolvers = parseExpr(sc, "${", "}", (l, v) -> buildValue(l, v, null),
                s -> parseSpringExpr0(sc, configVars));
        if (resolvers.size() == 1) {
            return resolvers.get(0);
        }
        return new ConcatValueResolver(resolvers);
    }

    IBeanPropValueResolver parseBindExpr(BeanDefinition bean, SourceLocation loc, String propName, String expr) {
        TextScanner sc = TextScanner.fromString(loc, expr);
        List<IBeanPropValueResolver> resolvers = parseExpr(sc, "@{", "}", (l, v) -> buildValue(l, v, null),
                s -> parseBindExpr(sc, bean, propName, null));
        if (resolvers.size() == 1) {
            return resolvers.get(0);
        }
        return new ConcatValueResolver(resolvers);
    }

    IBeanPropValueResolver parseSpringExpr0(TextScanner sc, List<String> configVars) {
        String configVar = sc.nextConfigVar();
        configVars.add(configVar);
        sc.skipBlank();
        if (sc.tryMatch(':')) {
            if (sc.tryMatch("${")) {
                IBeanPropValueResolver value = parseSpringExpr0(sc, configVars);
                sc.match('}');
                return value;
            } else {
                String defaultValue = sc.nextUntil('}', false).trim().toString();
                return new ConfigValueResolver(false, configVars, false, defaultValue);
            }
        } else {
            return new ConfigValueResolver(false, configVars, true, null);
        }
    }

    IBeanPropValueResolver parseBindExpr(TextScanner sc, BeanDefinition bean, String propName,
                                         IGenericType expectedType) {
        if (sc.cur == '@') {
            String value = sc.nextUntil('}', false).trim().toString();
            return parsePrefixExpr(bean, propName, value, expectedType);
        }
        SimpleExprParser parser = new SimpleExprParser();
        int beginPos = sc.pos;
        Expression expr = parser.parseExpr(sc);
        int endPose = sc.pos;
        String code = sc.getBaseSequence().subSequence(beginPos, endPose).toString();

        IXplCompiler cp = XLang.newXplCompiler();
        IExecutableExpression executable = cp.buildExecutable(expr, false, cp.newCompileScope());
        return new ExpressionValueResolver(new ExprEvalAction(executable), code);
    }

    public static IBeanPropValueResolver parsePrefixExpr(BeanDefinition bean, String propName, String value,
                                                         IGenericType expectedType) {
        if (value.startsWith(IocConstants.PREFIX_BEAN)) {
            if (value.equals(IocConstants.CONFIG_BEAN_ID)) {
                return new FixedValueResolver(ConfigPropHelper.getNormalizedId(bean.getId()));
            }
            if (value.equals(IocConstants.CONFIG_BEAN_CONTAINER)) {
                return BeanContainerValueResolver.INSTANCE;
            }
            if (value.equals(IocConstants.CONFIG_BEAN_TYPE)) {
                return new BeanTypeValueResolver(bean.getBeanTypes().get(0));
            }

            throw new NopException(ERR_IOC_INVALID_BEAN_CONFIG_VALUE).source(bean).param(ARG_BEAN_NAME, bean.getId())
                    .param(ARG_TRACE, bean.getTrace()).param(ARG_PROP_NAME, propName).param(ARG_VALUE, value);
        } else if (value.startsWith(IocConstants.PREFIX_CFG) || value.startsWith(IocConstants.PREFIX_R_CFG)) {
            boolean reactive = value.startsWith(IocConstants.PREFIX_R_CFG);
            value = value.substring(IocConstants.PREFIX_CFG.length());
            int pos = value.lastIndexOf('|');
            String defaultValue = null;
            if (pos > 0) {
                defaultValue = value.substring(pos + 1).trim();
                value = value.substring(0, pos).trim();
            }
            List<String> configVars = StringHelper.stripedSplit(value, ',');
            return new ConfigValueResolver(reactive, configVars, defaultValue == null, defaultValue);
        } else {
            throw new NopException(ERR_IOC_INVALID_BIND_EXPR).source(bean).param(ARG_BEAN_NAME, bean.getId())
                    .param(ARG_TRACE, bean.getTrace()).param(ARG_PROP_NAME, propName).param(ARG_EXPR, value);
        }
    }

    IBeanPropValueResolver buildValue(SourceLocation loc, String expr, IGenericType expectedType) {
        Object value = expr;
        if (expectedType != null) {
            value = BeanTool.castBeanToType(expr, expectedType);
        }
        return new FixedValueResolver(value);
    }

    static <T> List<T> parseExpr(TextScanner sc, String exprStart, String exprEnd,
                                 BiFunction<SourceLocation, String, T> literalBuilder, Function<TextScanner, T> exprParser) {
        List<T> list = new ArrayList<>();
        SourceLocation loc = sc.location();
        MutableString text = sc.nextUntil(s -> s.startsWith(exprStart), true, "");
        if (text.length() > 0) {
            list.add(literalBuilder.apply(loc, text.toString()));
        }

        while (sc.startsWith(exprStart)) {
            sc.next(exprStart.length());
            sc.skipBlank();

            T expr = exprParser.apply(sc);
            if (expr != null) {
                list.add(expr);
            }
            sc.consume(exprEnd);

            text = sc.nextUntil(s -> s.startsWith(exprStart), true, "");
            if (text.length() > 0) {
                list.add(literalBuilder.apply(loc, text.toString()));
            }
        }

        return list;
    }

    static boolean containsSpringExpr(String expr) {
        if (expr == null)
            return false;

        return expr.contains("${") && expr.contains("}");
    }

    static boolean containsBindExpr(String expr) {
        if (expr == null)
            return false;

        return expr.contains("@{") && expr.contains("}");
    }
}
