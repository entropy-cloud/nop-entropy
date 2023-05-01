/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpath.parse;

import io.nop.api.core.util.SourceLocation;
import io.nop.commons.functional.select.IMatchEvaluator;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.xlang.api.XLang;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.ast.Expression;
import io.nop.xlang.expr.simple.SimpleExprParser;
import io.nop.xlang.xpath.IXPathContext;
import io.nop.xlang.xpath.IXPathElementSelector;
import io.nop.xlang.xpath.IXPathOperator;
import io.nop.xlang.xpath.IXPathValueSelector;
import io.nop.xlang.xpath.evaluator.ExprEvaluator;
import io.nop.xlang.xpath.operator.AttrOperator;
import io.nop.xlang.xpath.operator.ExprOperator;
import io.nop.xlang.xpath.operator.IXPathOperatorProvider;
import io.nop.xlang.xpath.operator.XPathOperatorRegistry;
import io.nop.xlang.xpath.selector.CascadeSelector;
import io.nop.xlang.xpath.selector.ChildEvaluatorSelector;
import io.nop.xlang.xpath.selector.ChildIndexSelector;
import io.nop.xlang.xpath.selector.CompositeNodeSelector;
import io.nop.xlang.xpath.selector.CurrentEvaluatorSelector;
import io.nop.xlang.xpath.selector.CurrentNodeSelector;
import io.nop.xlang.xpath.selector.CurrentTagSelector;
import io.nop.xlang.xpath.selector.MultiXPathSelector;
import io.nop.xlang.xpath.selector.ParentSelector;
import io.nop.xlang.xpath.selector.RootSelector;
import io.nop.xlang.xpath.selector.XPathSelector;

import java.util.ArrayList;
import java.util.List;

import static io.nop.xlang.XLangErrors.ARG_OP;
import static io.nop.xlang.XLangErrors.ERR_XPATH_UNKNOWN_OPERATOR;

public class XPathSelectorParser<E> {
    private IXPathOperatorProvider operatorProvider = XPathOperatorRegistry.instance();
    private SimpleExprParser exprParser = new XPathExprParser().subExpr(true);
    private XLangCompileTool tool = XLang.newCompileTool().allowUnregisteredScopeVar(true);

    public XPathSelectorParser operatorProvider(IXPathOperatorProvider provider) {
        this.operatorProvider = provider;
        return this;
    }

    public IXPathValueSelector<E, Object> parseFromText(SourceLocation loc, String text) {
        TextScanner sc = TextScanner.fromString(loc, text);
        IXPathValueSelector<E, Object> selector = xpath(sc);
        sc.checkEnd();
        return selector;
    }

    private IXPathValueSelector<E, Object> xpath(TextScanner sc) {
        IXPathValueSelector<E, Object> selector = (IXPathValueSelector) singleSelector(sc);
        List<IXPathValueSelector<E, Object>> selectors = null;
        while (sc.tryMatch('|')) {
            IXPathValueSelector<E, Object> single = (IXPathValueSelector) singleSelector(sc);
            if (selectors == null) {
                selectors = new ArrayList<>();
                selectors.add(selector);
            }
            selectors.add(single);
        }
        if (selectors != null)
            return new MultiXPathSelector<>(selectors);
        return selector;
    }

    IXPathValueSelector<E, ?> singleSelector(TextScanner sc) {
        IXPathOperator<E> operator = valueOperator(sc);
        if (operator != null)
            return XPathSelector.of(null, operator);

        if (sc.cur == '/') {
            if (maybeValueSelector(sc.peek())) {
                sc.next();
                operator = valueOperator(sc);
                return XPathSelector.of(RootSelector.INSTANCE, operator);
            }
        }

        return complexSelector(sc);
    }

    private IXPathValueSelector<E, ?> complexSelector(TextScanner sc) {
        IXPathElementSelector<E> elementSelector = elementSelector(sc);
        IXPathOperator<E> operator = valueOperator(sc);
        if (operator == null)
            return elementSelector;

        return XPathSelector.of(elementSelector, operator);
    }

    private IXPathElementSelector<E> elementSelector(TextScanner sc) {
        List<IXPathElementSelector<E>> selectors = new ArrayList<>();
        boolean root = false;
        if (sc.tryConsume('.')) {
            selectors.add(CurrentNodeSelector.INSTANCE);
            sc.consume('/');
            if (sc.tryConsume('/')) {
                selectors.add(CascadeSelector.INSTANCE);
            }
        } else if (sc.tryConsume('/')) {
            selectors.add(RootSelector.INSTANCE);
            if (sc.tryConsume('/')) {
                selectors.add(CascadeSelector.INSTANCE);
            } else {
                root = true;
            }
        }

        IXPathElementSelector<E> component = root ? rootComponent(sc) : pathComponent(sc);
        selectors.add(component);

        while (sc.tryConsume('/')) {
            if (maybeValueSelector(sc.cur) || sc.cur == '|' || sc.isEnd())
                break;
            if (sc.tryConsume('/')) {
                selectors.add(CascadeSelector.INSTANCE);
            }
            component = pathComponent(sc);
            selectors.add(component);
        }
        if (selectors.size() == 1)
            return selectors.get(0);
        return new CompositeNodeSelector<>(selectors);
    }

    private boolean maybeValueSelector(int cur) {
        return cur == '$' || cur == '@' || cur == ':';
    }

    IXPathElementSelector<E> rootComponent(TextScanner sc) {
        String tagName;
        if (sc.tryConsume('*')) {
            tagName = "*";
        } else {
            tagName = pathComponentName(sc);
        }

        if (sc.cur == '[') {
            IMatchEvaluator<E, IXPathContext<E>> evaluator = evaluator(sc);
            return new CurrentEvaluatorSelector<>(tagName, evaluator);
        }
        return new CurrentTagSelector<>(tagName);
    }

    IXPathElementSelector<E> pathComponent(TextScanner sc) {
        if (sc.cur == '.') {
            sc.consume("..");
            return ParentSelector.INSTANCE;
        } else {
            boolean uniqueMatch = false;
            String tagName;
            IMatchEvaluator<E, IXPathContext<E>> evaluator = null;
            if (sc.tryConsume('*')) {
                tagName = "*";
            } else {
                if (sc.tryConsume('#'))
                    uniqueMatch = true;
                tagName = pathComponentName(sc);
            }
            if (sc.cur == '[') {
                if (StringHelper.isDigit(sc.peek())) {
                    sc.next();
                    int index = sc.nextInt();
                    sc.skipBlankInLine();
                    sc.match(']');
                    return ChildIndexSelector.of(index);
                }
                evaluator = evaluator(sc);
            }
            return ChildEvaluatorSelector.of(uniqueMatch, tagName, evaluator);
        }
    }

    protected String pathComponentName(TextScanner sc) {
        String name = sc.nextXmlName();
        return name;
    }

    private IXPathOperator<E> valueOperator(TextScanner sc) {
        if (sc.cur == '$') {
            String name = sc.nextJavaVar();
            IXPathOperator op = operatorProvider.getOperator(name);
            if (op == null)
                throw sc.newError(ERR_XPATH_UNKNOWN_OPERATOR).param(ARG_OP, name);
            return op;
        } else if (sc.cur == '@') {
            sc.next();
            String name = sc.nextXmlName();
            return new AttrOperator<>(name);
        } else if (sc.cur == ':') {
            sc.match(":[");
            Expression expr = exprParser.parseExpr(sc);
            sc.match(']');
            IEvalAction action = tool.buildEvalAction(expr);
            return new ExprOperator<>(action);
        } else {
            return null;
        }
    }

    private IMatchEvaluator<E, IXPathContext<E>> evaluator(TextScanner sc) {
        sc.match('[');
        Expression expr = exprParser.parseExpr(sc);
        IEvalAction action = tool.buildEvalAction(expr);
        IMatchEvaluator<E, IXPathContext<E>> evaluator = new ExprEvaluator<>(action);
        sc.match(']');
        return evaluator;
    }
}