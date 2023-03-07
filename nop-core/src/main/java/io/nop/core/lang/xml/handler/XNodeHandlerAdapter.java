/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.lang.xml.handler;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.core.lang.xml.XNode;

import java.util.Map;

import static io.nop.core.CoreErrors.ARG_ATTR_NAME;

public class XNodeHandlerAdapter implements IXNodeHandler {

    @Override
    public void beginDoc(String encoding, String docType, String instruction) {

    }

    @Override
    public void comment(String comment) {

    }

    @Override
    public void beginNode(SourceLocation loc, String tagName, Map<String, ValueWithLocation> attrs) {

    }

    @Override
    public void value(SourceLocation loc, Object value) {

    }

    @Override
    public void text(SourceLocation loc, String value) {
        value(loc, value);
    }

    @Override
    public void endNode(String tagName) {

    }

    @Override
    public void appendChild(XNode child) {
        child.process(this);
    }

    @Override
    public XNode endDoc() {
        return null;
    }

    @Override
    public void simpleNode(SourceLocation loc, String tagName, Map<String, ValueWithLocation> attrs) {
        beginNode(loc, tagName, attrs);
        endNode(tagName);
    }

    protected String getAttr(Map<String, ValueWithLocation> attrs, String name) {
        ValueWithLocation vl = attrs.get(name);
        if (vl == null)
            return null;
        return vl.asString();
    }

    protected int getAttrInt(Map<String, ValueWithLocation> attrs, String name, int defaultValue) {
        ValueWithLocation vl = attrs.get(name);
        if (vl == null)
            return defaultValue;

        return ConvertHelper.toInt(vl.getValue(), err -> new NopException(err).source(vl).param(ARG_ATTR_NAME, name));
    }

    protected Double getAttrDouble(Map<String, ValueWithLocation> attrs, String name, Double defaultValue) {
        ValueWithLocation vl = attrs.get(name);
        if (vl == null)
            return defaultValue;

        return ConvertHelper.toDouble(vl.getValue(), err -> new NopException(err).source(vl).param(ARG_ATTR_NAME, name));
    }

    protected boolean getAttrBoolean(Map<String, ValueWithLocation> attrs, String name, boolean defaultValue) {
        ValueWithLocation vl = attrs.get(name);
        if (vl == null)
            return defaultValue;

        return ConvertHelper.toPrimitiveBoolean(vl.getValue(),
                err -> new NopException(err).source(vl).param(ARG_ATTR_NAME, name));
    }
}