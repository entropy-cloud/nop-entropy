/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ooxml.common;

import io.nop.commons.util.StringHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.xml.IXNodeHandler;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.tpl.ITemplateOutput;
import io.nop.core.resource.zip.IZipOutput;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;

public interface IOfficePackagePart extends ITemplateOutput {
    /**
     * 文件在zip包中的路径
     *
     * @return
     */
    String getPath();

    default IOfficePackagePart loadInMemory() {
        return this;
    }

    /**
     * 将文件内容解析为XML节点返回
     *
     * @return
     */
    XNode loadXml();

    XNode buildXml(IEvalContext context);

    /**
     * 采用事件驱动方式解析文件内容
     */
    default void processXml(IXNodeHandler handler, IEvalContext context) {
        loadXml().process(handler);
    }

    default void generateToResource(IResource file, IEvalContext context) {
        XNode node = buildXml(context);

        node.saveToResource(file, StringHelper.ENCODING_UTF8);
    }

    @Override
    default void generateToStream(OutputStream os, IEvalContext context) throws IOException {
        buildXml(context).saveToStream(os, StringHelper.ENCODING_UTF8);
    }

    default void generateToZip(IZipOutput out, IEvalContext context) throws IOException {
        ZipEntry entry = out.newZipEntry(getPath());
        OutputStream os = out.addEntry(entry);
        generateToStream(os, context);
    }
}