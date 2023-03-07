/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.tpl;

import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.core.lang.xml.handler.CollectXmlHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class AbstractXmlTemplate implements IBinaryTemplateOutput {
    private boolean indent;

    public AbstractXmlTemplate indent(boolean indent) {
        this.indent = indent;
        return this;
    }

    @Override
    public void generateToStream(OutputStream os, IEvalContext context) throws IOException {
        OutputStreamWriter writer = new OutputStreamWriter(os, StringHelper.CHARSET_UTF8);
        CollectXmlHandler out = new CollectXmlHandler(writer).indent(indent);
        generateXml(out, context);
        writer.flush();
    }

    public abstract void generateXml(IXNodeHandler out, IEvalContext context) throws IOException;

    protected ValueWithLocation value(Object value) {
        return ValueWithLocation.of(null, value);
    }

    protected Map<String, ValueWithLocation> attrs(Object... values) {
        Map<String, ValueWithLocation> attrs = new LinkedHashMap<>();
        for (int i = 0, n = values.length; i < n - 1; i += 2) {
            String name = (String) values[i];
            Object value = values[i + 1];
            if (value == null)
                continue;
            attrs.put(name, value(value));
        }
        return attrs;
    }
}
