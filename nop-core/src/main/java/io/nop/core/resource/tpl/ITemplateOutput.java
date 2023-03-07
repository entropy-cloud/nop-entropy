/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.tpl;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.IoHelper;
import io.nop.core.context.IEvalContext;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import static io.nop.core.CoreErrors.ARG_RESOURCE;
import static io.nop.core.CoreErrors.ARG_TPL;
import static io.nop.core.CoreErrors.ERR_TPL_OUTPUT_BYTES_FAIL;
import static io.nop.core.CoreErrors.ERR_TPL_OUTPUT_TO_RESOURCE_FAIL;

public interface ITemplateOutput {
    void generateToStream(OutputStream os, IEvalContext context) throws IOException;

    default byte[] generateBytes(IEvalContext context) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            generateToStream(os, context);
        } catch (IOException e) {
            throw new NopException(ERR_TPL_OUTPUT_BYTES_FAIL).param(ARG_TPL, this);
        }
        return os.toByteArray();
    }

    default void generateToResource(IResource resource, IEvalContext context) {
        OutputStream os = resource.getOutputStream();
        try {
            generateToStream(os, context);
            os.flush();
        } catch (IOException e) {
            throw new NopException(ERR_TPL_OUTPUT_TO_RESOURCE_FAIL).param(ARG_TPL, this).param(ARG_RESOURCE, resource);
        } finally {
            IoHelper.safeClose(os);
        }
    }

    default void generateToFile(File file, IEvalContext context) {
        generateToResource(new FileResource(file), context);
    }
}