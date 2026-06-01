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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.stream.Stream;

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

            Long entryTime = resolveZipEntryTime(context);
            if (entryTime != null) {
                normalizeTempDirLastModified(tempDir.toPath(), entryTime.longValue());
            }
            LOG.info("nop.ooxml.generate-to-dir:usedTime={}", CoreMetrics.currentTimeMillis() - beginTime);

            ZipOptions options = new ZipOptions();
            String password = (String) context.getEvalScope().getValue(OfficeConstants.VAR_FILE_PASSWORD);
            options.setPassword(password);
            options.setEntryTime(entryTime);
            ResourceHelper.zipDirToStream(new FileResource(tempDir), os, options);
            os.flush();
        } finally {
            ResourceHelper.deleteAll(resource);
            long endTime = CoreMetrics.currentTimeMillis();
            LOG.info("nop.ooxml.generate-to-zip:usedTime={}", endTime - beginTime);
        }
    }

    protected Long resolveZipEntryTime(IEvalContext context) {
        Object value = context.getEvalScope().getValue(OfficeConstants.VAR_ZIP_ENTRY_TIME);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }

    protected void normalizeTempDirLastModified(Path root, long entryTime) throws IOException {
        FileTime fileTime = FileTime.fromMillis(entryTime);
        try (Stream<Path> stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.setLastModifiedTime(path, fileTime);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        } catch (IllegalStateException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw e;
        }
    }

    public abstract void generateToDir(File dir, IEvalContext context);
}
