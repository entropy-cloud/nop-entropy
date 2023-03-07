/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.idea.plugin.utils;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.ResourceHelper;
import io.nop.xlang.xpl.xlib.XplLibHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class XmlPsiHelper {
    static final PsiFile[] EMPTY_FILES = new PsiFile[0];
    static final PsiElement[] EMPTY_ELEMENTS = new PsiElement[0];

    public static String absolutePath(String path, XmlElement element) {
        String filePath = getNopVfsPath(element);
        return StringHelper.absolutePath(filePath, path);
    }

    public static List<PsiFile> findPsiFileList(Project project, String path) {
        String fileName = StringHelper.fileFullName(path);
        PsiFile[] files = FilenameIndex.getFilesByName(project, fileName, GlobalSearchScope.allScope(project));
        if (files.length == 0)
            return Collections.emptyList();

        path = ResourceHelper.getStdPath(path);

        List<PsiFile> ret = new ArrayList<>(files.length);
        for (PsiFile file : files) {
            String matchPath = ProjectFileHelper.getNopVfsStdPath(file.getVirtualFile());
            if (Objects.equals(path, matchPath))
                ret.add(file);
        }
        return ret;
    }

    public static PsiFile[] findPsiFile(Project project, String path) {
        List<PsiFile> list = findPsiFileList(project, path);
        if (list.isEmpty())
            return EMPTY_FILES;
        return list.toArray(EMPTY_FILES);
    }

    public static List<PsiFile> findXplLib(Project project, XmlTag tag) {
        String ns = StringHelper.getNamespace(tag.getName());
        if ("thisLib".equals(ns)) {
            PsiFile file = tag.getContainingFile();
            String fileName = file.getName();
            if (fileName.endsWith(".xlib")) {
                // 同一个库文件可能存在多个定制文件
                String path = ProjectFileHelper.getNopVfsPath(file.getVirtualFile());
                List<PsiFile> list = findPsiFileList(project, path);
                if (list.isEmpty())
                    list = Collections.singletonList(file);
                return list;
            }

            String path = ProjectFileHelper.getNopVfsPath(file.getVirtualFile());
            int pos = path.lastIndexOf("/xlib/");
            if (pos > 0) {
                // 标签的实现文件，假定格式为/xlib/{libName}/impl_xxx.xpl
                pos += "/xlib/".length();
                int pos2 = path.indexOf('/', pos);
                if (pos2 > 0) {
                    return findPsiFileList(project, path.substring(0, pos2) + ".xlib");
                }
            }
            return Collections.emptyList();
        }

        XmlAttribute attr = tag.getAttribute("xpl:lib");
        if (attr != null) {
            List<String> paths = StringHelper.split(attr.getValue(), ',');
            for (String path : paths) {
                String libNs = XplLibHelper.getNamespaceFromLibPath(path);
                if (ns.equals(libNs))
                    return findPsiFileList(project, path);
            }
        }

        PsiFile[] files = FilenameIndex.getFilesByName(project, ns + ".xlib", GlobalSearchScope.allScope(project));
        return files.length == 0 ? Collections.emptyList() : Arrays.asList(files);
    }

    public static PsiElement[] findXplTag(Project project, XmlTag tag) {
        List<PsiFile> files = findXplLib(project, tag);
        if (files.isEmpty())
            return EMPTY_ELEMENTS;

        String tagBegin = "<" + tag.getLocalName();

        List<PsiElement> ret = new ArrayList<>();
        for (PsiFile file : files) {
            String text = file.getText();
            int fromPos = 0;
            do {
                int pos = text.indexOf(tagBegin, fromPos);
                if (pos >= 0) {
                    int end = pos + tagBegin.length();
                    if (end == text.length() || text.charAt(end) == ' ' || text.charAt(end) == '/' || text.charAt(end) == '>') {
                        PsiElement element = file.findElementAt(pos + 1);
                        if (element != null && isXmlTag(element)) {
                            ret.add(element);
                            break;
                        }
                    }
                    fromPos = end;
                } else {
                    break;
                }
            } while (true);
        }
        return ret.toArray(EMPTY_ELEMENTS);
    }

    private static boolean isXmlTag(PsiElement element) {
        IElementType type = element.getNode().getElementType();
        return type == XmlElementType.XML_NAME || type == XmlElementType.XML_TAG_NAME || type == XmlElementType.XML_TAG;
    }

    public static String getNopVfsPath(PsiElement element) {
        PsiFile file = element.getContainingFile();
        if (file == null)
            return null;

        VirtualFile vf = file.getVirtualFile();
        if (vf == null)
            return null;

        return ProjectFileHelper.getNopVfsPath(vf);
    }

    public static SourceLocation getLocation(PsiElement element) {
        return getLocation(element, element.getTextOffset(), element.getTextLength());
    }

    public static SourceLocation getValueLocation(XmlTag element) {
        if (element.getValue() == null)
            return null;

        TextRange range = element.getValue().getTextRange();
        int offset = range.getStartOffset();
        return getLocation(element, offset, range.getLength());
    }

    static SourceLocation getLocation(PsiElement element, int offset, int len) {
        PsiFile psiFile = element.getContainingFile();
        VirtualFile vf = psiFile.getVirtualFile();
        if (vf == null)
            return null;

        String path = ProjectFileHelper.getNopVfsPath(vf);

        Document document = psiFile.getViewProvider().getDocument();
        assert document != null;
        int sourceLine = document.getLineNumber(offset);
        int sourceColumn = offset - document.getLineStartOffset(sourceLine);
        return SourceLocation.fromLine(path, sourceLine, sourceColumn, len);
    }

    private static PsiElement getRootElement(PsiElement element) {
        do {
            PsiElement parent = element.getParent();
            if (parent == null)
                return element;
            element = parent;
        } while (true);
    }

    public static String getAttrName(XmlAttributeValue value) {
        if (value == null)
            return null;

        if (!(value.getParent() instanceof XmlAttribute))
            return null;

        XmlAttribute attr = (XmlAttribute) value.getParent();
        return attr.getName();
    }

    public static XmlAttribute getAttr(XmlAttributeValue value) {
        if (value == null)
            return null;

        if (!(value.getParent() instanceof XmlAttribute))
            return null;

        XmlAttribute attr = (XmlAttribute) value.getParent();
        return attr;
    }

    public static boolean hasChild(XmlTag tag) {
        return tag.getNode().findChildByType(XmlElementType.XML_TAG) != null;
    }

    public static boolean isInComment(PsiElement element) {
        IElementType elementType = element.getNode().getElementType();
        return elementType == XmlTokenType.XML_COMMENT_END
                || elementType == XmlTokenType.XML_COMMENT_START
                || elementType == XmlTokenType.XML_COMMENT_CHARACTERS;
    }

    public static boolean isElementType(PsiElement elm, IElementType type) {
        if (elm == null)
            return false;
        ASTNode node = elm.getNode();
        if (node == null)
            return false;

        return node.getElementType() == type;
    }

    public static Set<String> getChildTagNames(XmlTag tag) {
        Set<String> tagNames = new HashSet<>();
        for (PsiElement element : tag.getChildren()) {
            if (element instanceof XmlTag) {
                tagNames.add(((XmlTag) element).getName());
            }
        }
        return tagNames;
    }

    public static XmlTag getXmlTag(PsiElement element) {
        if (element == null)
            return null;

        if (element instanceof XmlTag)
            return ((XmlTag) element);

        do {
            PsiElement parent = element.getParent();
            if (parent == null)
                return null;
            if (parent instanceof XmlTag)
                return (XmlTag) parent;
            element = parent;
        } while (true);
    }

    public static XmlTag getRoot(XmlTag tag) {
        do {
            XmlTag parent = tag.getParentTag();
            if (parent == null)
                return tag;
            tag = parent;
        } while (true);
    }

    public static String getXmlnsForUrl(XmlTag tag, String url) {
        for (XmlAttribute attr : tag.getAttributes()) {
            if (url.equals(attr.getValue())) {
                String name = attr.getName();
                if (name.startsWith("xmlns:"))
                    return name.substring("xmlns:".length());
            }
        }
        return null;
    }
}
