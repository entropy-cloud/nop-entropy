package io.nop.idea.plugin.reference;

import java.util.ArrayList;
import java.util.List;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 对 Nop vfs 资源文件的引用
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-22
 */
public class NopVfsFileReference extends PsiReferenceBase<XmlElement> {
    private final PsiFile file;

    /**
     * @param refElement
     *         与引用直接相关的元素。例如绑定属性值与文件的引用关系，则该参数需为不包含引号的、类型为
     *         {@link XmlElementType#XML_ATTRIBUTE_VALUE_TOKEN} 类型的元素。
     *         如果在该元素内以分隔符分隔了多个引用，则需要通过参数 <code>textRange</code>
     *         指定对应引用的关联文本所在的文本范围，该范围相对于该元素，从 0 开始计算。
     *         相关处理逻辑见 {@link com.intellij.psi.impl.SharedPsiElementImplUtil#addReferences SharedPsiElementImplUtil#addReferences}）
     */
    public NopVfsFileReference(@NotNull XmlElement refElement, TextRange textRange, @NotNull PsiFile file) {
        super(refElement, textRange,
              // 不采用延迟解析模式，以确保当解析到有其他相关引用时，其能够被 PsiMultiReference 作为最优引用
              false);
        this.file = file;
    }

    /** 得到具体的引用对象（文件、文件行、文件内某个元素等） */
    @Override
    public @Nullable PsiElement resolve() {
        // TODO 在光标显示引用信息时，参考 class 文档样式显示 xdef/dsl 的 vfs 路径及其文档：JavaDocumentationProvider
        List<PsiElement> results = new ArrayList<>();
        PsiTreeUtil.processElements(this.file, el -> {
            if (el instanceof XmlTag tag) {
                results.add(tag);
                // 仅取根节点
                return false;
            }
            return true; // 继续遍历
        });

        return results.isEmpty() ? this.file : results.get(0);
    }

    /** 得到补全建议元素列表，可以为字符串或 {@link PsiElement} */
    @Override
    public @NotNull Object @NotNull [] getVariants() {
        return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public boolean isReferenceTo(@NotNull PsiElement target) {
        return target instanceof PsiFile && ((PsiFile) target).getVirtualFile().getPath().endsWith("/_vfs" + file);
    }
}
