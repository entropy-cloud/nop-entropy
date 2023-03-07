/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.http.api.support;

import io.nop.api.core.exceptions.NopException;
import io.nop.http.api.client.IHttpOutputFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class DefaultHttpOutputFile implements IHttpOutputFile {
    private final File file;

    public DefaultHttpOutputFile(File file) {
        this.file = file;
    }

    @Override
    public File toFile() {
        return file;
    }

    @Override
    public OutputStream getOutputStream() {
        try {
            return new FileOutputStream(file);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }
}
