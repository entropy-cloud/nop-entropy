/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.zip;

import io.nop.api.core.exceptions.NopException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class JdkZipTool implements IZipTool {

    @Override
    public IZipOutput newZipOutput(OutputStream os, ZipOptions options) {
        if (options == null) {
            options = new ZipOptions();
        }

        try {
            ZipOutputStream zos = options.isJarFile() ? new JarOutputStream(os) : new ZipOutputStream(os);
            return new JdkZipOutput(zos, options.isJarFile(), options.getProgressListener());
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public IZipInput newZipInput(InputStream is, ZipOptions options) {
        return new JdkZipInput(new ZipInputStream(is), options == null ? null : options.getProgressListener());
    }
}