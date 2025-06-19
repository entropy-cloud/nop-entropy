/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.link;

import java.util.ArrayList;
import java.util.List;

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

        String attrName = attr.getName();
        XmlTagInfo tagInfo = XDefPsiHelper.getTagInfo(attr);

        XDefTypeDecl attrDefType = null;
        if (tagInfo != null && tagInfo.getDefNode() != null) {
            attrDefType = tagInfo.getAttrType(attrName);
        }

        if (attrDefType == null) {
            // 未定义类型属性，直接针对 *.xdef 文件做跳转
            if (attrValue.endsWith(".xdef")) {
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
        } //
        else if (XDefConstants.STD_DOMAIN_XDEF_REF.equals(stdDomain)) {
            return getPsiFilesByXDefRef(project, tagInfo, attrValue);
        }

        // <c:import from="/nop/web/xlib/web.xlib" />
        // <c:include src="dingflow-gen/impl_GenComponents.xpl" />

        // XDefConstants.STD_DOMAIN_METHOD_REF
        // XDefConstants.STD_DOMAIN_NAME_OR_V_PATH

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

    private PsiElement[] getPsiFilesByXDefRef(Project project, XmlTagInfo tagInfo, String attrValue) {
        // - /nop/schema/xdef.xdef:
        //   - `<schema xdef:ref="schema-node.xdef" />`
        //   - `<item xdef:ref="ISchema" />`
        // - /nop/schema/schema/schema-node.xdef:
        //   `<schema ref="/test/test-filter.xdef#FilterCondition" />`，`FilterCondition` 为节点的唯一属性值
        String target;
        PsiElement[] psiFiles;

        if (attrValue.indexOf(".") > 0) {
            int hashIndex = attrValue.indexOf('#');
            String path = hashIndex > 0 ? attrValue.substring(0, hashIndex) : attrValue;

            target = hashIndex > 0 ? attrValue.substring(hashIndex + 1) : null;
            psiFiles = getPsiFilesByPath(project, tagInfo.getTag(), path);
        } else {
            target = attrValue;
            // Note: 只能引用当前文件内的名字
            psiFiles = new PsiElement[] { tagInfo.getTag().getContainingFile() };
        }

        if (psiFiles == null || StringHelper.isEmpty(target)) {
            return psiFiles;
        }

        List<PsiElement> result = new ArrayList<>();
        for (PsiElement psiFile : psiFiles) {
            PsiTreeUtil.processElements(psiFile, element -> {
                if (element instanceof XmlTag tag) {
                    if (target.equals(tag.getAttributeValue("xdef:name")) //
                        || target.equals(tag.getAttributeValue("meta:name")) //
                        || target.equals(tag.getAttributeValue("name")) //
                        || target.equals(tag.getAttributeValue("id")) //
                    ) {
                        result.add(tag);
                    }
                }
                return true; // 继续遍历
            });
        }

        return result.isEmpty() ? psiFiles : result.toArray(new PsiElement[0]);
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
}
