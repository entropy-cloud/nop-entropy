package io.nop.idea.plugin.lang.script.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceSet;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * <code>import</code> 语句中导入的类名的节点
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-29
 */
public class ImportSourceNode extends RuleSpecNode {
    private static final JavaClassReferenceProvider provider = new JavaClassReferenceProvider();

    static {
        // 支持解析包名：JavaClassReference#advancedResolveInner
        provider.setOption(JavaClassReferenceProvider.ADVANCED_RESOLVE, true);
    }

    public ImportSourceNode(@NotNull ASTNode node) {
        super(node);
    }

    public String getLastQualifiedName() {
        PsiElement last = PsiTreeUtil.getDeepestLast(this);

        return last.getText();
    }

    public String getFullyQualifiedName() {
        return getText();
    }

    /** 构造 Java 相关的引用对象，从而支持自动补全、引用跳转、文档显示等 */
    @Override
    protected PsiReference @NotNull [] doGetReferences() {
        String fqn = getFullyQualifiedName();

        JavaClassReferenceSet refSet = new JavaClassReferenceSet(fqn, this, 0, false, provider);

        return refSet.getReferences();
    }
}
