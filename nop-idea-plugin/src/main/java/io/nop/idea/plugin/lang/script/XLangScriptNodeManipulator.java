package io.nop.idea.plugin.lang.script;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileFactory;
import com.intellij.util.IncorrectOperationException;
import io.nop.idea.plugin.lang.script.psi.Identifier;
import io.nop.idea.plugin.lang.script.psi.ImportDeclarationNode;
import io.nop.idea.plugin.lang.script.psi.ImportSourceNode;
import io.nop.idea.plugin.lang.script.psi.QualifiedNameNode;
import io.nop.idea.plugin.lang.script.psi.RuleSpecNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 负责处理对 XLang Script 中 AST 节点元素的修改
 * <p/>
 * 通过 {@link ElementManipulators#getManipulator} 获取其在
 * <code>plugin.xml</code> 中注册的实例
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-06
 */
public class XLangScriptNodeManipulator extends AbstractElementManipulator<RuleSpecNode> {

    /** 对 <code>element</code> 采取就地更新策略 */
    @Override
    public @Nullable RuleSpecNode handleContentChange(
            @NotNull RuleSpecNode element, @NotNull TextRange rangeInElement, String newContent
    ) throws IncorrectOperationException {
        TextRange elementTextRange = element.getTextRangeInParent().shiftLeft(element.getStartOffsetInParent());
        boolean needToReplaceElement = elementTextRange.equals(rangeInElement);

        // 对导入的类名做整体替换，但在 needToReplaceWhole = false 时，对包名做局部替换
        if (element instanceof ImportSourceNode imp && needToReplaceElement) {
            PsiElement node = PsiFileFactory.getInstance(element.getProject())
                                            .createFileFromText(element.getLanguage(), "import " + newContent);

            QualifiedNameNode qn = findQualifiedNameNode(node);
            imp.getQualifiedName().replace(qn);

            return element;
        }

        // Note: 取相对于 element 的偏移量
        int offset = rangeInElement.getStartOffset();
        // 得到待修改的元素
        PsiElement target = element.findElementAt(offset);

        if (target instanceof Identifier id) {
            // Note: 其会直接更新 element 的树结构
            id.replaceWithText(newContent);
        }

        return element;
    }

    protected QualifiedNameNode findQualifiedNameNode(PsiElement node) {
        while (node != null) {
            if (node instanceof QualifiedNameNode qn) {
                return qn;
            }

            if (node instanceof ImportDeclarationNode) {
                // 跳过 import 和 空白
                node = node.getFirstChild().getNextSibling().getNextSibling();
            } else {
                node = node.getFirstChild();
            }
        }
        return null;
    }
}
