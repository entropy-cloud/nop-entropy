/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.lang;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.vfs.VirtualFile;
import io.nop.commons.util.StringHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XLangFileTypeDetector implements FileTypeRegistry.FileTypeDetector {
    @Override
    public @Nullable FileType detect(@NotNull VirtualFile file, @NotNull ByteSequence firstBytes,
                                     @Nullable CharSequence firstCharsIfText) {
        if (firstCharsIfText == null)
            return null;

        String ext = file.getExtension();
        if (ext == null)
            return null;

        if (ext.equals("xpl") || ext.equals("xgen") || ext.equals("xrun"))
            return XLangFileType.INSTANCE;

        String schema = XLangFileHelper.getSchemaFromContent(firstCharsIfText);
        if (!StringHelper.isEmpty(schema)) {
            return XLangFileType.INSTANCE;
        }

        return null;
    }
}
