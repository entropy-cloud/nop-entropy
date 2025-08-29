// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package io.nop.idea.plugin.utils;

import java.io.File;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import io.nop.api.core.beans.DictBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.debugger.LineLocation;
import io.nop.commons.util.StringHelper;
import io.nop.core.dict.DictProvider;
import io.nop.core.resource.ResourceHelper;
import io.nop.idea.plugin.resource.ProjectEnv;
import org.jetbrains.annotations.Nullable;

public class ProjectFileHelper {

    /**
     * 与FileHelper.getFileUrl格式保持一致
     */
    public static String getFileUrl(VirtualFile file) {
        String protocol = file.getFileSystem().getProtocol();
        String path = file.getPath();
        if (protocol.equals("jar")) {
            // java的jar URL的实现中会自动对{和}等特殊字符进行编码，而IDEA不会
            path = StringHelper.encodeUriPath(path);
            return "jar:file:" + (path.startsWith("/") ? "" : "/") + path;
        }
        return protocol + ':' + (path.startsWith("/") ? "" : "/") + path;
    }

    public static URL toURL(VirtualFile file) {
        try {
            return new URL(getFileUrl(file));
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    public static LineLocation toLineLocation(XSourcePosition pos) {
        if (pos == null) {
            return null;
        }
        final VirtualFile file = pos.getFile();
        final String fileURL = ProjectFileHelper.getFileUrl(file);
        return new LineLocation(fileURL, pos.getLine() + 1);
    }

    public static String getNopVfsPath(VirtualFile file) {
        if (file == null) {
            return null;
        }

        String protocol = file.getFileSystem().getProtocol();
        String path = file.getPath();
        if (protocol.equals("jar")) {
            int pos = path.indexOf(".jar!");
            if (pos > 0) {
                path = path.substring(pos + ".jar!".length());
            }
        }
        int pos = path.indexOf("/_vfs/");
        if (pos < 0) {
            return null;
        }
        return path.substring(pos + "/_vfs/".length() - 1);
    }

    public static String getNopVfsStdPath(VirtualFile file) {
        String path = getNopVfsPath(file);
        if (path == null) {
            return null;
        }
        return ResourceHelper.getStdPath(path);
    }

    /** 查找所有的 *.xdef 资源路径 */
    public static Collection<String> findAllXdefNopVfsPaths(Project project) {
        return FilenameIndex.getAllFilesByExt(project, "xdef")
                            .stream()
                            .map(ProjectFileHelper::getNopVfsStdPath)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());
    }

    /** 查找所有的 Nop 字典资源路径 */
    public static Collection<String> findAllDictNopVfsPaths(Project project) {
        return FilenameIndex.getAllFilesByExt(project, "dict.yaml")
                            .stream()
                            .map(ProjectFileHelper::getNopVfsStdPath)
                            .filter(Objects::nonNull)
                            .filter((path) -> path.startsWith("/dict/"))
                            .collect(Collectors.toSet());
    }

    /** 从 vfs 路径中获取字典名字 */
    public static String getDictNameFromVfsPath(String path) {
        String name = StringHelper.removeHead(path, "/dict/");
        name = StringHelper.removeTail(name, ".dict.yaml");

        return name;
    }

    /** 查找所有 vfs 资源路径 */
    public static Collection<String> findAllNopVfsPaths(Project project) {
        Set<String> names = CollectionFactory.createSmallMemoryFootprintSet();
        Collections.addAll(names, FilenameIndex.getAllFilenames(project));

        Set<String> vfsPaths = CollectionFactory.createSmallMemoryFootprintSet();

        GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        FilenameIndex.processFilesByNames(names, true, scope, null, (file) -> {
            String vfsPath = getNopVfsPath(file);

            if (!file.isDirectory() && vfsPath != null) {
                vfsPaths.add(vfsPath);
            }
            return true;
        });

        return vfsPaths;
    }

    public static DictBean loadDict(PsiElement refElement, String dictName) {
        return ProjectEnv.withProject(refElement.getProject(),
                                      () -> DictProvider.instance().getDict(null, dictName, null, null));
    }

    public static XSourcePosition buildPos(LineLocation loc) {
        if (loc == null) {
            return null;
        }
        return buildPos(loc.getSourcePath(), loc.getLine());
    }

    public static XSourcePosition buildPos(String path, int line) {
        if (path.startsWith("jar:")) {
            path = StringHelper.decodeURL(path);
        }
        VirtualFile file = ProjectFileHelper.getVirtualFile(path);
        if (file == null) {
            return null;
        }
        return XDebuggerUtil.getInstance().createPosition(file, line - 1);
    }

    /**
     * 查找包含指定Class的jar包的位置
     */
    public static String getJarForClass(Class<?> clazz) {
        return PathManager.getJarPathForClass(clazz);
    }

    public static int getActualLineNumber(Project project, @Nullable XSourcePosition position) {
        if (position == null) {
            return -1;
        }

        // SourceLocation的line从1开始，而Idea的line从0开始
        int line = position.getLine();
        return line + 1;
    }

    public static CharSequence getLine(Document document, int line) {
        int beginOffset = document.getLineStartOffset(line);
        int endOffset = document.getLineEndOffset(line);
        if (beginOffset < 0 || endOffset < 0 || beginOffset >= endOffset) {
            return null;
        }

        CharSequence str = document.getCharsSequence().subSequence(beginOffset, endOffset);
        return str;
    }

    // copy from XsltBreakpointHandler
    @Nullable
    public static PsiElement findContextElement(Project project, @Nullable XSourcePosition position) {
        if (position == null) {
            return null;
        }

        final PsiFile file = PsiManager.getInstance(project).findFile(position.getFile());
        if (file == null) {
            return null;
        }

        int offset = -1;
        final Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        if (document != null && document.getLineCount() > position.getLine() && position.getLine() >= 0) {
            offset = document.getLineStartOffset(position.getLine());
        }
        if (offset < 0) {
            offset = position.getOffset();
        }

        PsiElement contextElement = file.findElementAt(offset);
        while (contextElement != null && !(contextElement instanceof XmlElement)) {
            contextElement = PsiTreeUtil.nextLeaf(contextElement);
        }
        return contextElement;
    }

    /**
     * 根据外部文件路径查找到对应VirtualFile，并更新IDE内的缓存
     *
     * @param file
     *         外部文件路径
     * @return file对应的VirtualFile
     */
    public static VirtualFile refreshFile(File file) {
        return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    }

    public static VirtualFile toVirtualFile(File file) {
        return LocalFileSystem.getInstance().findFileByIoFile(file);
    }

    public static VirtualFile getVirtualFile(String path) {
        if (StringHelper.isEmpty(path)) {
            return null;
        }

        if (path.startsWith("jar:")) {
            if (path.startsWith("jar:file:")) {
                path = path.substring("jar:file:".length());
            } else {
                path = path.substring("jar:".length());
            }
            return JarFileSystem.getInstance().findFileByPath(path);
        }

        File file;
        if (path.startsWith("file:")) {
            file = new File(path.substring("file:".length()));
        } else {
            file = new File(path);
        }

        return toVirtualFile(file);
    }

    /**
     * 得到IDE指定插件的根目录
     *
     * @param pluginName
     *         插件名称
     * @return 当前IDE的plugins目录下的指定子目录
     */
    public static File getPluginHome(String pluginName) {
        return PluginPathManager.getPluginHome(pluginName);
    }
}
