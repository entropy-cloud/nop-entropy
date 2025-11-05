/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.maven.plugin.shaded;

import io.nop.commons.util.IoHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.ByteArrayResource;
import org.apache.maven.plugins.shade.relocation.Relocator;
import org.apache.maven.plugins.shade.resource.ReproducibleResourceTransformer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public abstract class AbstractResourceTransformer implements ReproducibleResourceTransformer {
    protected long time = Long.MIN_VALUE;

    protected Map<String, IResource> beansResources = new TreeMap<>();


    @Override
    public void processResource(String path, InputStream in, List<Relocator> relocatorList, long time) throws IOException {
        if (time > this.time)
            this.time = time;
        processResource(path, in, relocatorList);
    }

    protected void saveNode(String path, XNode node) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        node.saveToStream(out, "UTF-8");
        beansResources.put(path, new ByteArrayResource(path, out.toByteArray(), -1L));
    }

    protected String relocate(String name, List<Relocator> relocatorList) {
        return ShadeHelper.relocate(name, relocatorList);
    }

    @Override
    public boolean hasTransformedResource() {
        return !beansResources.isEmpty();
    }

    @Override
    public void modifyOutputStream(JarOutputStream jarOut) throws IOException {
        for (IResource resource : beansResources.values()) {
            String path = resource.getPath().substring(1);

            JarEntry jarEntry = new JarEntry(path);
            jarEntry.setTime(time);
            jarOut.putNextEntry(jarEntry);

            InputStream in = resource.getInputStream();
            try {
                IoHelper.copy(in, jarOut);
            } finally {
                IoHelper.safeClose(in);
            }
        }
    }
}
