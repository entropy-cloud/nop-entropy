package io.nop.idea.plugin.reference;

import java.util.ArrayList;
import java.util.List;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 对 Nop vfs 资源文件的引用
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-22
 */
public class NopVfsFileReference extends PsiReferenceBase<XmlElement> implements XLangReference {
    private final PsiFile file;
    /** 文件内的定位锚点 */
    private final Anchor anchor;

    /**
     * @param refElement
     *         与引用直接相关的元素。例如绑定属性值与文件的引用关系，则该参数需为不包含引号的、类型为
     *         {@link XmlElementType#XML_ATTRIBUTE_VALUE_TOKEN} 类型的元素。
     *         如果在该元素内以分隔符分隔了多个引用，则需要通过参数 <code>textRange</code>
     *         指定对应引用的关联文本所在的文本范围，该范围相对于该元素，从 0 开始计算。
     *         相关处理逻辑见 {@link com.intellij.psi.impl.SharedPsiElementImplUtil#addReferences SharedPsiElementImplUtil#addReferences}）
     */
    public NopVfsFileReference(
            @NotNull XmlElement refElement, TextRange textRange, //
            @NotNull PsiFile file, Anchor anchor
    ) {
        super(refElement, textRange,
              // 不采用延迟解析模式，以确保当解析到有其他相关引用时，其能够被 PsiMultiReference 作为最优引用
              false);
        this.file = file;
        this.anchor = anchor;
    }

    /** 得到具体的引用对象（文件、文件行、文件内某个元素等） */
    @Override
    public @Nullable PsiElement resolve() {
        List<PsiElement> results = new ArrayList<>();

        if (anchor instanceof PosAnchor pos) {
            PsiElement el = XmlPsiHelper.getPsiElementAt(file, pos.line, pos.column);
            if (el != null) {
                results.add(el);
            }
        } else {
            PsiTreeUtil.processElements(file, element -> {
                if (!(element instanceof XmlElement el)) {
                    return true; // 跳过非 xml 元素
                }

                if (anchor == null) {
                    if (el instanceof XmlTag) {
                        results.add(el);
                    }
                } else if (anchor instanceof RefAnchor ref) {
                    if (ref.match(el)) {
                        results.add(el);
                    }
                }
                // 仅取第一个匹配到的元素，否则，继续遍历
                return results.isEmpty();
            });
        }

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

    public static class NotFound extends PsiReferenceBase<XmlElement> implements XLangReference {
        private final String path;

        public NotFound(@NotNull XmlElement refElement, TextRange textRange, String path) {
            super(refElement, textRange, false);
            this.path = path;
        }

        public String getPath() {
            return path;
        }

        @Override
        public @Nullable PsiElement resolve() {
            return null;
        }

        @Override
        public @NotNull Object @NotNull [] getVariants() {
            return ArrayUtil.EMPTY_OBJECT_ARRAY;
        }

        @Override
        public boolean isReferenceTo(@NotNull PsiElement target) {
            return false;
        }
    }

    public interface Anchor {}

    public record PosAnchor(int line, int column) implements Anchor {}

    public record RefAnchor(String value) implements Anchor {

        public boolean match(XmlElement element) {
            if (value == null) {
                // 若未引用名字，则匹配 xml 根节点
                return element instanceof XmlTag;
            } //
            else if (element instanceof XmlAttribute attr) {
                String attrName = attr.getName();
                String attrValue = attr.getValue();

                // Note: xdef-ref 引用的只能是 xdef:name 命名的节点
                return ("xdef:name".equals(attrName) //
                        || "meta:name".equals(attrName) //
                       ) && value.equals(attrValue);
            }

            return false;
        }
    }
}
