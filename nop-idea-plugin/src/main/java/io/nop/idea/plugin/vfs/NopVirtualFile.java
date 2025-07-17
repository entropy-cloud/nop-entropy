package io.nop.idea.plugin.vfs;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;
import javax.swing.*;

import com.intellij.codeInsight.navigation.PsiTargetNavigator;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.PsiElementBase;
import com.intellij.util.IncorrectOperationException;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 为 Nop VFS 资源创建独立的 {@link PsiElement}，
 * 从而便于后续为其补充独立的{@link com.intellij.openapi.fileEditor.FileEditor 编辑视图}以支持其 delta 层叠机制
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-12
 */
public class NopVirtualFile extends PsiElementBase implements PsiNamedElement {
    private final PsiElement srcElement;
    private final String path;
    private final Function<PsiFile, PsiElement> targetResolver;

    private PsiElement[] children;

    /** @see #NopVirtualFile(PsiElement, String, Function) */
    public NopVirtualFile(PsiElement srcElement, String path) {
        this(srcElement, path, null);
    }

    /**
     * @param srcElement
     *         与该 vfs 直接相关的源元素
     * @param path
     *         该 vfs 的绝对路径
     * @param targetResolver
     *         目标元素获取函数
     */
    public NopVirtualFile(PsiElement srcElement, String path, Function<PsiFile, PsiElement> targetResolver) {
        this.srcElement = srcElement;
        this.path = path;
        assert path.startsWith("/");

        this.targetResolver = targetResolver;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + ':' + getPath();
    }

    @Override
    public boolean canNavigate() {
        return true;
    }

    @Override
    public void navigate(boolean requestFocus) {
        Editor editor = FileEditorManager.getInstance(getProject()).getSelectedTextEditor();
        if (editor == null) {
            return;
        }

        // 支持 ctrl + 点击 方式跳转到具体文件
        PsiTargetNavigator<PsiElement> navigator = new PsiTargetNavigator<>(() -> Arrays.asList(getChildren()));
        navigator.navigate(editor, null);
    }

    /** ctrl + 鼠标移动 所显示的元素信息 */
    @Override
    public ItemPresentation getPresentation() {
        return new ItemPresentation() {
            @Override
            public @Nullable String getPresentableText() {
                return getName();
            }

            @Override
            public @Nullable Icon getIcon(boolean unused) {
                return null;
            }
        };
    }

    public boolean hasEmptyChildren() {
        return getChildren().length == 0;
    }

    /** {@link #getChildren()} 是否为 {@link PsiFile} */
    public boolean forFileChildren() {
        return targetResolver == null;
    }

    @Override
    public @NotNull PsiElement @NotNull [] getChildren() {
        if (children == null) {
            PsiFile srcFile = srcElement.getContainingFile();
            String srcVfsPath = XmlPsiHelper.getNopVfsPath(srcElement);

            children = XmlPsiHelper.findPsiFilesByNopVfsPath(this, path)
                                   .stream()
                                   // Note: 如果是同名的 vfs，则仅对同一文件做引用识别
                                   .filter(file -> !path.equals(srcVfsPath) || srcFile == file)
                                   .map(file -> targetResolver != null ? targetResolver.apply(file) : file)
                                   .filter(Objects::nonNull)
                                   .toArray(PsiElement[]::new);
        }
        return children;
    }

    @Override
    public @NotNull Project getProject() {
        return srcElement.getProject();
    }

    public String getPath() {
        return path;
    }

    @Override
    public String getName() {
        return getPath();
    }

    @Override
    public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
        return null;
    }

    @Override
    public @NotNull Language getLanguage() {
        return PlainTextLanguage.INSTANCE;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public PsiFile getContainingFile() {
        return null;
    }

    @Override
    public String getText() {
        return getName();
    }

    @Override
    public TextRange getTextRange() {
        return TextRange.allOf(getText());
    }

    @Override
    public PsiElement getParent() {
        return null;
    }

    @Override
    public int getStartOffsetInParent() {
        return 0;
    }

    @Override
    public int getTextLength() {
        return getText().length();
    }

    @Override
    public @Nullable PsiElement findElementAt(int offset) {
        return null;
    }

    @Override
    public int getTextOffset() {
        return 0;
    }

    @Override
    public char @NotNull [] textToCharArray() {
        return new char[0];
    }

    @Override
    public ASTNode getNode() {
        return null;
    }
}
