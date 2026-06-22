/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.template;

import java.util.Map;

import com.intellij.ide.fileTemplates.DefaultCreateFromTemplateHandler;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import io.nop.commons.util.StringHelper;
import io.nop.idea.plugin.lang.XLangFileType;
import io.nop.idea.plugin.messages.NopPluginBundle;
import org.jetbrains.annotations.NotNull;

/**
 * 用于为模板准备变量，并可控制从模板内容到 {@link com.intellij.psi.PsiElement} 的转换过程
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-08-03
 */
public class CreateXLangFileFromTemplateHandler extends DefaultCreateFromTemplateHandler {

    @Override
    public boolean handlesTemplate(@NotNull FileTemplate template) {
        // Note: 默认情况下模版的后缀名不会关联 XLang 类型，因此，只能检查文件名是否匹配，不能检查其关联类型
        return /*template.isTemplateOfType(XLangFileType.INSTANCE) //
               && */ArrayUtil.contains(template.getName(),
                                       CreateXLangFileFromTemplateAction.TEMPLATE_XDSL_NAME,
                                       CreateXLangFileFromTemplateAction.TEMPLATE_XDEF_NAME);
    }

    @Override
    public @NotNull PsiElement createFromTemplate(
            @NotNull Project project, @NotNull PsiDirectory directory, String fileName, @NotNull FileTemplate template,
            @NotNull String templateText, @NotNull Map<String, Object> props
    ) throws IncorrectOperationException {
        FileType type = FileTypeRegistry.getInstance().getFileTypeByFileName(fileName);

        // 将创建的文件关联为 XML 类型，再由 XLangLanguageSubstitutor 根据其内容动态决定是否绑定 XLang，
        // 避免将 xml 后缀全部关联为 XLang 类型。注意，在 IDEA 2025+ 中该方案不会立即生效，暂时无法解决
        if (type != XmlFileType.INSTANCE && type != XLangFileType.INSTANCE) {
            String fileType = StringHelper.fileType(fileName);
            if (!StringHelper.isEmpty(fileType)) {
                FileTypeManager.getInstance().associateExtension(XmlFileType.INSTANCE, fileType);
            }
        }

        return super.createFromTemplate(project, directory, fileName, template, templateText, props);
    }

    @Override
    public void prepareProperties(
            @NotNull Map<String, Object> props, String filename, //
            @NotNull FileTemplate template, @NotNull Project project
    ) {
        if (template.getExtension().equals("xdef")) {
            String name = StringHelper.removeLastPart(filename, '.');
            if (name.isEmpty()) {
                name = filename;
            }
            name = name.replace('.', '-');

            props.put("NODE_ROOT_NAME", name);
        }

        super.prepareProperties(props, filename, template, project);
    }

    @Override
    protected String checkAppendExtension(String fileName, @NotNull FileTemplate template) {
        if (!StringHelper.isValidFileName(fileName)) {
            throw new IncorrectOperationException(NopPluginBundle.message("action.error.invalid-filename"));
        }

        if (template.getExtension().equals("xdsl")) {
            String ext = StringHelper.fileExt(fileName);

            if (ext.isEmpty()) {
                throw new IncorrectOperationException(NopPluginBundle.message("action.error.no-xlang-file-extension"));
            }
            return fileName;
        }
        return super.checkAppendExtension(fileName, template);
    }
}
