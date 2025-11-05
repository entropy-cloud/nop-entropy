/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.http.api.support;

import io.nop.api.core.exceptions.NopException;
import io.nop.http.api.client.IHttpInputFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public class DefaultHttpInputFile implements IHttpInputFile {
    private final File file;

    public DefaultHttpInputFile(File file) {
        this.file = file;
    }

    @Override
    public File toFile() {
        return file;
    }

    @Override
    public String getName() {
        return file.getName();
    }

    @Override
    public InputStream getInputStream() {
        try {
            return new FileInputStream(file);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public long getLength() {
        return file.length();
    }
}
