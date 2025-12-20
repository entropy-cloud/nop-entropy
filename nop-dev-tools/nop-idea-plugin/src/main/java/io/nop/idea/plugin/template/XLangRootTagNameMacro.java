/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.template;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Macro;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.vfs.VirtualFile;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.idea.plugin.lang.XLangFileType;
import io.nop.idea.plugin.lang.XLangLanguageSubstitutor;
import io.nop.idea.plugin.resource.ProjectEnv;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 根据 <code>x:schema</code> 或文件名得到 DSL 的根节点标签名
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-08-06
 */
public class XLangRootTagNameMacro extends Macro {
    private static final String XDEF_SUFFIX = ".xdef";
    private static final Pattern REGEX_SCHEMA = Pattern.compile(".+ \\S+:schema=\"([^\"]+)\".+",
                                                                Pattern.DOTALL | Pattern.MULTILINE);

    @Override
    public @NonNls String getName() {
        return "rootTagName";
    }

    @Override
    public @Nullable Result calculateResult(Expression @NotNull [] params, ExpressionContext context) {
        Editor editor = context.getEditor();
        VirtualFile file = editor != null ? editor.getVirtualFile() : null;
        if (file == null) {
            return null;
        }

        String text = editor.getDocument().getText();
        Matcher matcher = REGEX_SCHEMA.matcher(text);
        if (!matcher.matches()) {
            return null;
        }

        String schemaPath = matcher.group(1);
        if (StringHelper.isBlank(schemaPath) || !schemaPath.endsWith(XDEF_SUFFIX)) {
            return null;
        }

        String schemaAbsPath = XmlPsiHelper.getNopVfsAbsolutePath(schemaPath, file);
        String charset = XLangFileType.INSTANCE.getCharset(file, text.getBytes());

        String tagName = ProjectEnv.withProject(context.getProject(), () -> {
            IResource resource = VirtualFileSystem.instance().getResource(schemaAbsPath);
            XNode node = null;
            try {
                node = XLangLanguageSubstitutor.parseRootNode(resource.getReader(charset));
            } catch (Exception ignore) {
            }

            String name = node != null ? node.getTagName() : null;
            return name == null || name.endsWith(":unknown-tag") ? null : name;
        });

        if (tagName == null) {
            String fileName = file.getName();
            String schemaName = fileName.endsWith(XDEF_SUFFIX) ? fileName : StringHelper.fileName(schemaPath);
            tagName = StringHelper.removeFileExt(schemaName);
        }

        return new TextResult(tagName);
    }

    @Override
    public boolean isAcceptableInContext(TemplateContextType context) {
        return context instanceof XLangFileLiveTemplateContextType;
    }
}
