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
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-08-06
 */
public class XLangRootTagNameMacro extends Macro {
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

        String vfsPath = matcher.group(1);
        if (StringHelper.isBlank(vfsPath) || !vfsPath.endsWith(".xdef")) {
            return null;
        }

        String resourcePath = XmlPsiHelper.getNopVfsAbsolutePath(vfsPath, file);
        String charset = XLangFileType.INSTANCE.getCharset(file, text.getBytes());

        String tagName = ProjectEnv.withProject(context.getProject(), () -> {
            IResource resource = VirtualFileSystem.instance().getResource(resourcePath);
            XNode node = XLangLanguageSubstitutor.parseRootNode(resource.getReader(charset));
            if (node == null) {
                return null;
            }

            String name = node.getTagName();
            if (name.endsWith(":unknown-tag")) {
                name = file.getName();

                int index = name.indexOf('.');
                name = index <= 0 ? name : name.substring(0, index);
            }
            return name;
        });

        return tagName != null ? new TextResult(tagName) : null;
    }

    @Override
    public boolean isAcceptableInContext(TemplateContextType context) {
        return context instanceof XLangFileLiveTemplateContextType;
    }
}
