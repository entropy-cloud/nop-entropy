/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.idea.plugin.xml;

import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import io.nop.core.lang.xml.adapter.IXNodeViewAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class XmlTagAdapter implements IXNodeViewAdapter<XmlTag> {
    public static XmlTagAdapter INSTANCE = new XmlTagAdapter();

    @Override
    public String tagName(XmlTag xmlTag) {
        return xmlTag.getName();
    }

    @Override
    public Object attr(XmlTag xmlTag, String name) {
        return xmlTag.getAttributeValue(name);
    }

    @Override
    public XmlTag getParent(XmlTag xmlTag) {
        PsiElement elm = xmlTag.getParent();
        if (elm instanceof XmlTag)
            return (XmlTag) elm;
        return null;
    }

    @Override
    public List<XmlTag> getChildren(XmlTag xmlTag) {
        PsiElement[] children = xmlTag.getChildren();
        if (children == null || children.length <= 0)
            return Collections.emptyList();

        List<XmlTag> ret = new ArrayList<>(children.length);
        for (PsiElement elm : children) {
            if (elm instanceof XmlTag) {
                ret.add((XmlTag) elm);
            }
        }
        return ret;
    }

    @Override
    public Object value(XmlTag xmlTag) {
        return text(xmlTag);
    }

    @Override
    public String xml(XmlTag xmlTag) {
        return null;
    }

    @Override
    public String innerXml(XmlTag xmlTag) {
        return null;
    }

    @Override
    public String html(XmlTag xmlTag) {
        return null;
    }

    @Override
    public String innerHtml(XmlTag xmlTag) {
        return null;
    }

    @Override
    public String text(XmlTag xmlTag) {
        XmlTagValue value = xmlTag.getValue();
        return value == null ? null : value.getText();
    }
}
