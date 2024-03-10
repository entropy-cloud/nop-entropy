/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.impl;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IFile;
import io.nop.core.resource.ResourceHelper;

import static io.nop.core.CoreErrors.ARG_RESOURCE;
import static io.nop.core.CoreErrors.ERR_RESOURCE_CREATE_TEMP_FILE_FAIL;

public abstract class AbstractFile extends AbstractResource implements IFile {
    private static final long serialVersionUID = -6771455371193745855L;

    public AbstractFile(String path) {
        super(path);
    }

    @Override
    public IFile createTempFile(String prefix, String postfix) {
        if (!StringHelper.isEmpty(prefix)) {
            Guard.checkArgument(StringHelper.isValidFileName(prefix), "invalid fileName prefix", prefix);
        }

        if (!StringHelper.isEmpty(postfix)) {
            Guard.checkArgument(StringHelper.isValidFileName(postfix), "invalid fileName postfix", postfix);
        }

        if (prefix == null)
            prefix = "";
        if (postfix == null)
            postfix = "";

        for (int i = 0; i < 10000; i++) {
            String name = generateName(prefix, postfix);

            IFile file = getResource(name);
            if (file.createNewFile())
                return file;
        }
        throw new NopException(ERR_RESOURCE_CREATE_TEMP_FILE_FAIL).param(ARG_RESOURCE, this);
    }

    String generateName(String prefix, String postfix) {
        long n = MathHelper.random().nextLong();
        if (n == Long.MIN_VALUE) {
            n = 0; // corner case
        } else {
            n = Math.abs(n);
        }

        String nano = Long.toString(CoreMetrics.nanoTime());
        if (nano.length() > 8) {
            nano = nano.substring(nano.length() - 8);
        }

        String name = prefix + nano + n + postfix;
        return name;
    }

    @Override
    public final IFile getResource(String relativeName) {
        Guard.checkArgument(ResourceHelper.isValidRelativeName(relativeName), "invalid relative resource path",
                relativeName);
        if (relativeName.length() == 0)
            return this;

        return doGetRelative(relativeName);
    }

    protected abstract IFile doGetRelative(String relativeName);
}