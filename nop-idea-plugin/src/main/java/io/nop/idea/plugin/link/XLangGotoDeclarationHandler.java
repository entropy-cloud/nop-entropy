/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.link;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandlerBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTag;
import io.nop.commons.util.StringHelper;
import io.nop.idea.plugin.utils.XDefPsiHelper;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.idea.plugin.utils.XmlTagInfo;
import io.nop.xlang.xdef.XDefConstants;
import io.nop.xlang.xdef.XDefTypeDecl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/***
 * 点击ctrl能够链接到lib文件
 */
public class XLangGotoDeclarationHandler extends GotoDeclarationHandlerBase {

    @Override
    public @Nullable PsiElement getGotoDeclarationTarget(@Nullable PsiElement sourceElement, Editor editor) {
        PsiElement[] elements = getGotoDeclarationTargets(sourceElement, 0, editor);
        return elements.length == 0 ? null : elements[0];
    }

    @Override
    public @Nullable PsiElement[] getGotoDeclarationTargets(
            @Nullable PsiElement sourceElement, int offset, Editor editor
    ) {
        Project project = editor.getProject();
        PsiElement element = sourceElement != null ? sourceElement.getParent() : null;

        if (XmlPsiHelper.isElementType(element, XmlElementType.XML_TAG)) {
            return getGotoDeclarationTargetsForXmlTag(project, element);
        } //
        else if (XmlPsiHelper.isElementType(element, XmlElementType.XML_ATTRIBUTE_VALUE)) {
            return getGotoDeclarationTargetsForXmlAttributeValue(project, element, offset);
        } //
        else if (XmlPsiHelper.isElementType(element, XmlElementType.XML_TEXT)) {
            return getGotoDeclarationTargetsForXmlText(project, element);
        }

        return null;
    }

    /** 获取可从 xml 标签上跳转的元素（文件路径、节点引用等） */
    private PsiElement[] getGotoDeclarationTargetsForXmlTag(Project project, PsiElement element) {
        XmlTag tag = (XmlTag) element;
        String tagName = tag.getName();

        if (!isCustomTag(tagName)) {
            return null;
        }
        return XmlPsiHelper.findXplTag(project, tag);
    }

    /** 获取可从 xml 属性值中跳转的元素（文件路径、节点引用等） */
    private PsiElement[] getGotoDeclarationTargetsForXmlAttributeValue(
            Project project, PsiElement element, int cursorOffset
    ) {
        XmlAttribute attr = PsiTreeUtil.getParentOfType(element, XmlAttribute.class);
        if (attr == null) {
            return null;
        }

        String attrValue = attr.getValue();
        if (StringHelper.isEmpty(attrValue)) {
            return null;
        }

        XmlTagInfo tagInfo = XDefPsiHelper.getTagInfo(attr);

        XDefTypeDecl attrDefType = null;
        if (tagInfo != null && tagInfo.getDefNode() != null) {
            attrDefType = tagInfo.getAttrType(attr.getName());
        }

        if (attrDefType == null) {
            // 未定义类型属性，直接针对 *.xdef 文件做跳转
            if (attrValue.endsWith(".xdef") && attrValue.contains("/")) {
                return getPsiFilesFromPathCsv(project, attr.getContainingFile(), cursorOffset);
            }
            return null;
        }

        String stdDomain = attrDefType.getStdDomain();
        if (XDefConstants.STD_DOMAIN_V_PATH.equals(stdDomain)) {
            return getPsiFilesByPath(project, attr, attrValue);
        } //
        else if (XDefConstants.STD_DOMAIN_V_PATH_LIST.equals(stdDomain)) {
            return getPsiFilesFromPathCsv(project, attr.getContainingFile(), cursorOffset);
        }

        // XDefConstants.STD_DOMAIN_METHOD_REF
        // XDefConstants.STD_DOMAIN_NAME_OR_V_PATH
        // XDefConstants.STD_DOMAIN_XDEF_REF

//        if (attrName.equals("xpl:lib")) {
//            String path = XmlPsiHelper.absolutePath(attrValue, attr);
//
//            return XmlPsiHelper.findPsiFile(project, path);
//        } else if (isVfsPath(attr)) {
//            String path = XmlPsiHelper.absolutePath(attrValue, attr);
//
//            return XmlPsiHelper.findPsiFile(project, path);
//        }

        return null;
    }

    /** 获取可从 xml 文本中跳转的元素（文件路径、节点引用等） */
    private PsiElement[] getGotoDeclarationTargetsForXmlText(Project project, PsiElement element) {
        if (!(element.getParent() instanceof XmlTag parent)) {
            return null;
        }

        String text = element.getText().trim();
        if ((text.indexOf('.') > 0 || text.indexOf('/') > 0) //
            && StringHelper.isValidFilePath(text) //
        ) {
            String path = XmlPsiHelper.absolutePath(text, parent);

            return XmlPsiHelper.findPsiFile(project, path);
        }

        return null;
    }

    /** 获取指定路径下的文件 */
    private PsiElement[] getPsiFilesByPath(Project project, @NotNull XmlElement element, String path) {
        if (!StringHelper.isValidFilePath(path)) {
            return null;
        }

        path = XmlPsiHelper.absolutePath(path, element);

        return XmlPsiHelper.findPsiFile(project, path);
    }

    /** 从 csv 文本中取光标处的文件 */
    private PsiElement[] getPsiFilesFromPathCsv(Project project, @NotNull PsiFile file, int cursorOffset) {
        PsiElement element = file.findElementAt(cursorOffset);
        assert element != null;

        // 计算 光标所在元素 在文件中的绝对位置
        int elementStart = 0;
        PsiElement parent = element;
        while (parent != null && parent.getStartOffsetInParent() > 0) {
            elementStart += parent.getStartOffsetInParent();
            parent = parent.getParent();
        }

        String path = extractPathFromCsv(element.getText(), cursorOffset - elementStart);

        return getPsiFilesByPath(project, (XmlElement) element, path);
    }

    /** 从 csv 中提取指定偏移位置所在的文件路径 */
    private String extractPathFromCsv(String csv, int offset) {
        int start = offset;
        int end = offset;

        while (start > 0) {
            char ch = csv.charAt(start - 1);
            if (ch != ',' && !Character.isWhitespace(ch)) {
                start -= 1;
            } else {
                break;
            }
        }
        while (end < csv.length()) {
            char ch = csv.charAt(end);
            if (ch != ',' && !Character.isWhitespace(ch)) {
                end += 1;
            } else {
                break;
            }
        }

        return csv.substring(start, end);
    }

    private boolean isCustomTag(String tagName) {
        int pos = tagName.indexOf(':');
        if (pos <= 0) {
            return false;
        }

        // 内置的名字空间
        String ns = tagName.substring(0, pos);
        return !ns.equals("x")
               && !ns.equals("xdef")
               && !ns.equals("xdsl")
               && !ns.equals("xpl")
               && !ns.equals("c")
               && !ns.equals("macro")
               && !ns.equals("xmlns");
    }

    private boolean isVfsPath(XmlAttribute attr) {
        String value = attr.getValue();
        String name = attr.getName();

        if (StringHelper.isEmpty(value)) {
            return false;
        }

        if (value.indexOf(':') >= 0) {
            return false;
        }

        if ("x:extends".equals(name)) {
            return true;
        }

        if ("x:schema".equals(name)) {
            return true;
        }

        if (name.startsWith("xmlns:") && value.endsWith(".xdef")) {
            return true;
        }

        if (name.equals("xdef:ref") || name.equals("ref")) {
            if (value.indexOf('.') > 0) {
                return true;
            }
        }

        // <c:include src="" />
        // <c:import from="" />
        return StringHelper.isValidFilePath(value);
    }
}
