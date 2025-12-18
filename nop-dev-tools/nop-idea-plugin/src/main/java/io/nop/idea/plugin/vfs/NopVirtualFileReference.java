package io.nop.idea.plugin.vfs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import io.nop.core.resource.ResourceHelper;
import io.nop.idea.plugin.lang.reference.XLangReferenceBase;
import io.nop.idea.plugin.messages.NopPluginBundle;
import io.nop.idea.plugin.utils.ProjectFileHelper;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.xlang.xdsl.XDslConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-12
 */
public class NopVirtualFileReference extends XLangReferenceBase {
    private final String path;

    /**
     * @param path
     *         可以为相对路径，最终通过 myElement 确定其绝对路径
     */
    public NopVirtualFileReference(
            PsiElement myElement, TextRange myRangeInElement, //
            String path
    ) {
        super(myElement, myRangeInElement);
        this.path = path;
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        String vfsPath = path;
        boolean isSuper = XDslConstants.EXTENDS_SUPER.equals(path);
        // Note: super 指向的是相同 vfs 路径的父资源
        if (isSuper) {
            vfsPath = XmlPsiHelper.getNopVfsPath(myElement);
            if (vfsPath != null) {
                vfsPath = ResourceHelper.getStdPath(vfsPath);
            }
        }

        String absPath = XmlPsiHelper.getNopVfsAbsolutePath(vfsPath, myElement);
        NopVirtualFile target = new NopVirtualFile(myElement, absPath, (file) -> {
            // 对于 super，需排除掉 delta 和 tenant 定制资源
            if (isSuper) {
                String path = XmlPsiHelper.getNopVfsPath(file);
                if (path != null //
                    && (ResourceHelper.isTenantPath(path) //
                        || ResourceHelper.isDeltaPath(path)) //
                ) {
                    return null;
                }
            }
            return file;
        });

        if (target.hasEmptyChildren()) {
            String msg = NopPluginBundle.message("xlang.annotation.reference.vfs-file-not-found", path);
            setUnresolvedMessage(msg);

            return null;
        }
        return target;
    }

    /** @return 项目内所有可访问的 vfs 资源路径 */
    @Override
    public Object @NotNull [] getVariants() {
        Project project = myElement.getProject();

        return ProjectFileHelper.getCachedNopVfsPaths(project).toArray();
    }
}
