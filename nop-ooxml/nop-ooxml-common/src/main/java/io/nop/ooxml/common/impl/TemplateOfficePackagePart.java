/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ooxml.common.impl;

import io.nop.api.core.util.Guard;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.ooxml.common.IOfficePackagePart;
import io.nop.xlang.api.XLang;

import java.io.IOException;
import java.io.OutputStream;

public class TemplateOfficePackagePart implements IOfficePackagePart {
    private final String path;
    private final ITextTemplateOutput template;

    public TemplateOfficePackagePart(String path, ITextTemplateOutput template) {
        Guard.checkArgument(!path.startsWith("/"), "path should not starts with slash");
        this.path = path;
        this.template = template;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public XNode loadXml() {
        return buildXml(XLang.newEvalScope());
    }

    @Override
    public XNode buildXml(IEvalContext context) {
        return template.generateToNode(context);
    }

    @Override
    public void processXml(IXNodeHandler handler, IEvalContext context) {
        template.generateToNode(handler, context);
    }

    @Override
    public void generateToResource(IResource file, IEvalContext scope) {
        template.generateToResource(file, scope);
    }

    @Override
    public void generateToStream(OutputStream os, IEvalContext context) throws IOException {
        template.generateToStream(os, context);
    }
}
