/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.common.impl;

import io.nop.core.context.IEvalContext;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.ooxml.common.IOfficePackagePart;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class TextOfficePackagePart implements IOfficePackagePart {
    private final String path;
    private String text;

    public TextOfficePackagePart(String path, String text) {
        this.path = path;
        this.text = text;
    }

    @Override
    public String getPath() {
        return path;
    }


    public void setText(String text) {
        this.text = text;
    }

    public String loadText() {
        return text;
    }

    @Override
    public XNode loadXml() {
        return loadXml();
    }

    @Override
    public XNode buildXml(IEvalContext context) {
        return loadXml();
    }

    public void generateToResource(IResource file, IEvalContext context) {
        ResourceHelper.writeText(file, text);
    }


    @Override
    public void generateToStream(OutputStream os, IEvalContext context) throws IOException {
        os.write(text.getBytes(StandardCharsets.UTF_8));
    }

}
