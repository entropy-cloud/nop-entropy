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
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.ByteArrayResource;
import io.nop.core.resource.zip.IZipOutput;
import io.nop.ooxml.common.IOfficePackagePart;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;

public class ResourceOfficePackagePart implements IOfficePackagePart {
    private final String path;
    private final IResource resource;

    public ResourceOfficePackagePart(String path, IResource resource) {
        Guard.checkArgument(!path.startsWith("/"), "path should not starts with slash");
        this.path = path;
        this.resource = resource;
    }

    public IOfficePackagePart loadInMemory() {
        if (resource instanceof ByteArrayResource)
            return this;

        String name = resource.getName();
        if (name.endsWith(".xml")) {
            XNode node = loadXml();
            return new XmlOfficePackagePart(path, node);
        }

        byte[] bytes = ResourceHelper.readBytes(resource);
        return new ResourceOfficePackagePart(path,
                new ByteArrayResource(resource.getPath(), bytes, resource.lastModified()));
    }

    public IResource getResource() {
        return resource;
    }

    @Override
    public void generateToStream(OutputStream os, IEvalContext context) throws IOException {
        resource.writeToStream(os);
    }

    @Override
    public byte[] generateBytes(IEvalContext context) {
        if (resource instanceof ByteArrayResource)
            return ((ByteArrayResource) resource).getData();

        return IOfficePackagePart.super.generateBytes(context);
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public XNode loadXml() {
        return XNodeParser.instance().parseFromResource(resource);
    }

    public String loadText() {
        return ResourceHelper.readText(resource);
    }

    @Override
    public XNode buildXml(IEvalContext context) {
        return loadXml();
    }

    @Override
    public void processXml(IXNodeHandler handler, IEvalContext context) {
        XNodeParser.instance().handler(handler).parseFromResource(resource);
    }

    @Override
    public void generateToResource(IResource file, IEvalContext context) {
        resource.saveToResource(file);
    }

    @Override
    public void generateToZip(IZipOutput out, IEvalContext context) throws IOException {
        ZipEntry entry = out.newZipEntry(path);
        out.addResource(entry, resource);
    }
}
