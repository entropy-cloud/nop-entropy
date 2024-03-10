/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.maven.plugin.shaded;

import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import org.apache.maven.plugins.shade.relocation.Relocator;
import org.apache.maven.plugins.shade.resource.ReproducibleResourceTransformer;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public class SpringFactoryResourceTransformer implements ReproducibleResourceTransformer {
    private long time = Long.MIN_VALUE;

    private Set<String> classNames = new TreeSet<>();

    @Override
    public void processResource(String s, InputStream inputStream, List<Relocator> list, long time) throws IOException {
        if (time > this.time)
            this.time = time;
        processResource(s, inputStream, list);
    }

    @Override
    public boolean canTransformResource(String path) {
        return path.endsWith("org.springframework.boot.autoconfigure.AutoConfiguration.imports");
    }

    @Override
    public void processResource(String path, InputStream in, List<Relocator> relocatorList) throws IOException {
        String text = IoHelper.readText(in, StringHelper.ENCODING_UTF8);
        List<String> list = StringHelper.stripedSplit(text, '\n');
        for (String name : list) {
            name = ShadeHelper.relocate(name, relocatorList);
            classNames.add(name);
        }
    }


    @Override
    public boolean hasTransformedResource() {
        return !classNames.isEmpty();
    }

    @Override
    public void modifyOutputStream(JarOutputStream jarOut) throws IOException {
        String path = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";
        JarEntry jarEntry = new JarEntry(path);
        jarEntry.setTime(time);
        jarOut.putNextEntry(jarEntry);

        String text = StringHelper.join(classNames, "\n");
        Writer out = IoHelper.toWriter(jarOut, StringHelper.ENCODING_UTF8);
        out.write(text);
        out.flush();
    }
}
