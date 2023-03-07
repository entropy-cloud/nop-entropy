/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xpath.operator;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.xlang.xpath.IXPathOperator;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.nop.xlang.XLangConstants.XPATH_OPERATOR_INNER_XML;
import static io.nop.xlang.XLangConstants.XPATH_OPERATOR_TAG;
import static io.nop.xlang.XLangConstants.XPATH_OPERATOR_TEXT;
import static io.nop.xlang.XLangConstants.XPATH_OPERATOR_VALUE;
import static io.nop.xlang.XLangConstants.XPATH_OPERATOR_XML;

@GlobalInstance
public class XPathOperatorRegistry implements IXPathOperatorProvider {
    static final XPathOperatorRegistry _instance = new XPathOperatorRegistry();

    static {
        _instance.registerOperator(XPATH_OPERATOR_INNER_XML, InnerXmlOperator.INSTANCE);
        _instance.registerOperator(XPATH_OPERATOR_XML, OuterXmlOperator.INSTANCE);
        _instance.registerOperator(XPATH_OPERATOR_TAG, TagOperator.INSTANCE);
        _instance.registerOperator(XPATH_OPERATOR_VALUE, ValueOperator.INSTANCE);
        _instance.registerOperator(XPATH_OPERATOR_TEXT, TextOperator.INSTANCE);
    }

    private final Map<String, IXPathOperator> operators = new ConcurrentHashMap<>();

    public static XPathOperatorRegistry instance() {
        return _instance;
    }

    public void registerOperator(String name, IXPathOperator operator) {
        operators.put(name, operator);
    }

    public void unregisterOperator(String name, IXPathOperator operator) {
        operators.remove(name, operator);
    }

    @Override
    public IXPathOperator getOperator(String name) {
        return operators.get(name);
    }
}