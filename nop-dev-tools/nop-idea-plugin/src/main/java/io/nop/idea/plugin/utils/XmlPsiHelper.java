/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.idea.plugin.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTokenType;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.ResourceHelper;
import org.jetbrains.annotations.NotNull;

public class XmlPsiHelper {

    public static String getNopVfsPath(ISourceLocationGetter locGetter) {
        SourceLocation loc = locGetter != null ? locGetter.getLocation() : null;
        String path = loc != null ? loc.getPath() : null;

        // Note: SourceLocation#getPath() 得到的 jar 中的 vfs 路径会添加 classpath:_vfs 前缀
        return path != null ? path.replace("classpath:_vfs", "") : null;
    }

    public static String getNopVfsPath(PsiElement element) {
        PsiFile file = element.getContainingFile();
        if (file == null) {
            return null;
        }

        VirtualFile vf = file.getVirtualFile();
        return getNopVfsPath(vf, file);
    }

    private static String getNopVfsPath(VirtualFile vf, PsiFile file) {
        // Note: 在编辑过程中得到的 VirtualFile 可能为 null，需尝试通过
        // PsiFile#getOriginalFile 获得 VirtualFile
        if (vf == null && file != null && file.getOriginalFile() != file) {
            vf = file.getOriginalFile().getVirtualFile();
        }

        return vf != null ? ProjectFileHelper.getNopVfsPath(vf) : null;
    }

    /**
     * 获取 {@code path} 的 vfs 绝对路径。
     * 若 {@code path} 为相对路径，则视为其相对于 {@code element} 所在文件的目录
     */
    public static String getNopVfsAbsolutePath(String path, PsiElement element) {
        String filePath = getNopVfsPath(element);

        return StringHelper.absolutePath(filePath, path);
    }

    /**
     * 获取 {@code path} 的 vfs 绝对路径。
     * 若 {@code path} 为相对路径，则视为其相对于 {@code vf} 所在文件的目录
     */
    public static String getNopVfsAbsolutePath(String path, VirtualFile vf) {
        String filePath = getNopVfsPath(vf, null);

        return StringHelper.absolutePath(filePath, path);
    }

    public static List<PsiFile> findPsiFileList(Project project, String path) {
        GlobalSearchScope scope = ProjectFileHelper.getSearchScope(project);
        String fileName = StringHelper.fileFullName(path);

        Collection<VirtualFile> vfList = FilenameIndex.getVirtualFilesByName(fileName, scope);
        if (vfList.isEmpty()) {
            return Collections.emptyList();
        }

        path = ResourceHelper.getStdPath(path);

        List<PsiFile> ret = new ArrayList<>(vfList.size());
        for (VirtualFile vf : vfList) {
            String vfPath = ProjectFileHelper.getNopVfsStdPath(vf);

            if (Objects.equals(path, vfPath)) {
                PsiFile f = PsiManager.getInstance(project).findFile(vf);
                ret.add(f);
            }
        }
        return ret;
    }

    public static List<PsiFile> findPsiFilesByNopVfsPath(PsiElement element, String path) {
        if (element == null || path == null) {
            return List.of();
        }

        Project project = element.getProject();
        String absPath = getNopVfsAbsolutePath(path, element);

        return findPsiFileList(project, absPath);
    }

    /** 获取指定行列的 {@link PsiElement 元素} */
    public static PsiElement getPsiElementAt(PsiFile psiFile, int line, int column) {
        Document document = PsiDocumentManager.getInstance(psiFile.getProject()).getDocument(psiFile);
        if (document == null) {
            return null;
        }

        int offset;
        try {
            offset = document.getLineStartOffset(line - 1) + column - 1;
        } catch (IndexOutOfBoundsException e) {
            // Note: 对于 _delta 文件，其可能不包含目标元素
            return null;
        }

        return psiFile.findElementAt(offset);
    }

    /** 获取指定位置的 {@link PsiElement 元素} */
    public static PsiElement getPsiElementAt(PsiFile psiFile, SourceLocation loc) {
        return getPsiElementAt(psiFile, loc.getLine(), loc.getCol());
    }

    /** 获取指定行列的指定类型的 {@link PsiElement 元素} */
    public static <T extends PsiElement> T getPsiElementAt(PsiFile psiFile, SourceLocation loc, Class<T> type) {
        PsiElement element = getPsiElementAt(psiFile, loc);

        if (type.isInstance(element)) {
            return (T) element;
        }
        return PsiTreeUtil.getParentOfType(element, type);
    }

    public static SourceLocation getLocation(PsiElement element) {
        return getLocation(element, element.getTextOffset(), element.getTextLength());
    }

    public static SourceLocation getValueLocation(XmlTag element) {
        TextRange range = element.getValue().getTextRange();
        int offset = range.getStartOffset();

        return getLocation(element, offset, range.getLength());
    }

    static SourceLocation getLocation(PsiElement element, int offset, int len) {
        PsiFile psiFile = element.getContainingFile();
        VirtualFile vf = psiFile.getVirtualFile();
        if (vf == null) {
            return null;
        }

        String path = ProjectFileHelper.getNopVfsPath(vf);

        Document document = psiFile.getViewProvider().getDocument();
        assert document != null;
        int sourceLine = document.getLineNumber(offset);
        int sourceColumn = offset - document.getLineStartOffset(sourceLine);
        return SourceLocation.fromLine(path, sourceLine, sourceColumn, len);
    }

    public static boolean isInComment(PsiElement element) {
        IElementType elementType = element.getNode().getElementType();
        return elementType == XmlTokenType.XML_COMMENT_END
               || elementType == XmlTokenType.XML_COMMENT_START
               || elementType == XmlTokenType.XML_COMMENT_CHARACTERS;
    }

    public static Set<String> getChildTagNames(XmlTag tag) {
        Set<String> names = new HashSet<>();

        for (PsiElement element : tag.getChildren()) {
            if (element instanceof XmlTag) {
                names.add(((XmlTag) element).getName());
            }
        }
        return names;
    }

    public static Set<String> getTagAttrNames(XmlTag tag) {
        Set<String> names = new HashSet<>();

        for (XmlAttribute attr : tag.getAttributes()) {
            names.add(attr.getName());
        }
        return names;
    }

    /**
     * 根据属性值获取匹配的子节点，在 <code>attrName</code> 为 <code>null</code> 时，匹配节点的标签名
     * <p/>
     * 其逻辑等价于 {@link io.nop.core.lang.xml.XNode#childByAttr}
     */
    public static XmlTag getChildTagByAttr(XmlTag tag, String attrName, String attrValue) {
        for (PsiElement element : tag.getChildren()) {
            if (!(element instanceof XmlTag child)) {
                continue;
            }

            if (attrName == null) {
                if (child.getName().equals(attrValue)) {
                    return child;
                }
            } else if (attrValue.equals(child.getAttributeValue(attrName))) {
                return child;
            }
        }
        return null;
    }

    /** 根据查找子节点上指定名字的属性 */
    public static List<XmlAttribute> getAttrsFromChildTag(XmlTag tag, String attrName) {
        List<XmlAttribute> attrs = new ArrayList<>();

        for (PsiElement element : tag.getChildren()) {
            if (!(element instanceof XmlTag child)) {
                continue;
            }

            XmlAttribute attr = child.getAttribute(attrName);
            if (attr != null) {
                attrs.add(attr);
            }
        }
        return attrs;
    }

    /** 从子节点上查找公共的属性名 */
    public static List<String> getCommonAttrNamesFromChildTag(XmlTag tag) {
        List<String> attrNames = new ArrayList<>();

        for (PsiElement element : tag.getChildren()) {
            if (!(element instanceof XmlTag child)) {
                continue;
            }

            Set<String> names = getTagAttrNames(child);
            if (attrNames.isEmpty()) {
                attrNames.addAll(names);
            } else {
                attrNames.retainAll(names);
            }
        }
        return attrNames;
    }

    /** 找到第一个符合条件的 {@link PsiElement 元素} */
    public static <T extends PsiElement> T findFirstElement(
            PsiElement element, Predicate<? super @NotNull PsiElement> condition) {
        PsiElement[] result = new PsiElement[] { null };

        PsiTreeUtil.processElements(element, el -> {
            if (condition.test(el)) {
                result[0] = el;
                return false;
            }
            return true; // 继续遍历
        });
        return (T) result[0];
    }

    public static XmlTag getRoot(XmlTag tag) {
        do {
            XmlTag parent = tag.getParentTag();
            if (parent == null) {
                return tag;
            }
            tag = parent;
        } while (true);
    }

    public static String getXmlnsForUrl(XmlTag rootTag, String url) {
        for (XmlAttribute attr : rootTag.getAttributes()) {
            if (url.equals(attr.getValue())) {
                String name = attr.getName();
                if (name.startsWith("xmlns:")) {
                    return name.substring("xmlns:".length());
                }
            }
        }
        return null;
    }
}
