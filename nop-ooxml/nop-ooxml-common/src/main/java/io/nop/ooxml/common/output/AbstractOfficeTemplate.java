/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ooxml.common.output;

import io.nop.api.core.time.CoreMetrics;
import io.nop.core.context.IEvalContext;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.tpl.IBinaryTemplateOutput;
import io.nop.core.resource.zip.ZipOptions;
import io.nop.ooxml.common.OfficeConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

public abstract class AbstractOfficeTemplate implements IBinaryTemplateOutput {
    static final Logger LOG = LoggerFactory.getLogger(AbstractOfficeTemplate.class);

    private boolean indent;

    public AbstractOfficeTemplate indent(boolean indent) {
        this.indent = indent;
        return this;
    }

    public boolean isIndent() {
        return indent;
    }

    @Override
    public void generateToStream(OutputStream os, IEvalContext context) throws IOException {
        IResource resource = ResourceHelper.getTempResource();
        LOG.debug("nop.ooxml.begin-generate-to-resource");
        long beginTime = CoreMetrics.currentTimeMillis();
        try {
            File tempDir = resource.toFile();
            tempDir.mkdirs();
            generateToDir(tempDir, context);
            LOG.info("nop.ooxml.generate-to-dir:usedTime={}", CoreMetrics.currentTimeMillis() - beginTime);

            ZipOptions options = new ZipOptions();
            String password = (String) context.getEvalScope().getValue(OfficeConstants.VAR_FILE_PASSWORD);
            options.setPassword(password);
            ResourceHelper.zipDirToStream(new FileResource(tempDir), os, options);
            os.flush();
        } finally {
            ResourceHelper.deleteAll(resource);
            long endTime = CoreMetrics.currentTimeMillis();
            LOG.info("nop.ooxml.generate-to-zip:usedTime={}", endTime - beginTime);
        }
    }

    public abstract void generateToDir(File dir, IEvalContext context);
}
