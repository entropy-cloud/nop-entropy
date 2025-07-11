package io.nop.idea.plugin.reference;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import io.nop.idea.plugin.lang.reference.XLangReference;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 对 Nop vfs 资源文件的引用
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-22
 */
public class XLangVfsFileReference extends PsiReferenceBase<XmlElement> implements XLangReference {
    private final PsiFile file;

    public XLangVfsFileReference(
            @NotNull XmlElement refElement, TextRange textRange, //
            @NotNull PsiFile file
    ) {
        super(refElement, textRange,
              // 不采用延迟解析模式，以确保当解析到有其他相关引用时，其能够被 PsiMultiReference 作为最优引用
              false);
        this.file = file;
    }

    /** 得到具体的引用对象（文件、文件行、文件内某个元素等） */
    @Override
    public @Nullable PsiElement resolve() {
        PsiElement result = XmlPsiHelper.findFirstElement(file, (element) -> element instanceof XmlTag);

        return result == null ? file : result;
    }

    /** 得到补全建议元素列表，可以为字符串或 {@link PsiElement} */
    @Override
    public @NotNull Object @NotNull [] getVariants() {
        return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public boolean isReferenceTo(@NotNull PsiElement target) {
        // XmlAttributeReference#isReferenceTo
        PsiManager manager = getElement().getManager();
        return manager.areElementsEquivalent(target, this.file);
    }
}
