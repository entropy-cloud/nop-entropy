/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xpath.adapter;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.core.lang.xml.adapter.IXNodeAdapter;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.xlang.XLangConstants;

import java.util.Arrays;
import java.util.Collection;

public class BeanXNodeAdapter implements IXNodeAdapter<Object> {
    @Override
    public void setAttr(Object node, String attrName, Object value) {
        BeanTool.instance().setProperty(node, attrName, value);
    }

    @Override
    public void setValue(Object node, Object value) {
        BeanTool.instance().setProperty(node, XLangConstants.XPATH_OPERATOR_VALUE, value);
    }

    @Override
    public void setXml(Object node, String xml) {
        BeanTool.instance().setProperty(node, XLangConstants.XPATH_OPERATOR_XML, xml);
    }

    @Override
    public void setInnerXml(Object node, String innerXml) {
        BeanTool.instance().setProperty(node, XLangConstants.XPATH_OPERATOR_INNER_XML, innerXml);
    }

    @Override
    public void setHtml(Object node, String html) {
        BeanTool.instance().setProperty(node, XLangConstants.XPATH_OPERATOR_HTML, html);
    }

    @Override
    public void setInnerHtml(Object node, String html) {
        BeanTool.instance().setProperty(node, XLangConstants.XPATH_OPERATOR_INNER_HTML, html);
    }

    @Override
    public void setText(Object node, String text) {
        BeanTool.instance().setProperty(node, XLangConstants.XPATH_OPERATOR_TEXT, text);
    }

    @Override
    public String tagName(Object node) {
        return null;
    }

    @Override
    public Object attr(Object node, String attrName) {
        return BeanTool.getComplexProperty(node, attrName);
    }

    @Override
    public Object getParent(Object node) {
        return null;
    }

    @Override
    public Collection<Object> getChildren(Object node) {
        if (node instanceof Collection)
            return (Collection<Object>) node;
        if (node instanceof Object[]) {
            return Arrays.asList((Object[]) node);
        }
        return null;
    }

    @Override
    public Object value(Object node) {
        return node;
    }

    @Override
    public String text(Object node) {
        return ConvertHelper.toString(BeanTool.instance().getProperty(node, XLangConstants.XPATH_OPERATOR_TEXT),
                (String) null);
    }

    @Override
    public String xml(Object node) {
        return null;
    }

    @Override
    public String innerXml(Object node) {
        return ConvertHelper.toString(BeanTool.instance().getProperty(node, XLangConstants.XPATH_OPERATOR_TEXT),
                (String) null);
    }

    @Override
    public String html(Object node) {
        return ConvertHelper.toString(BeanTool.instance().getProperty(node, XLangConstants.XPATH_OPERATOR_HTML),
                (String) null);
    }

    @Override
    public String innerHtml(Object node) {
        return ConvertHelper.toString(BeanTool.instance().getProperty(node, XLangConstants.XPATH_OPERATOR_INNER_HTML),
                (String) null);
    }
}
