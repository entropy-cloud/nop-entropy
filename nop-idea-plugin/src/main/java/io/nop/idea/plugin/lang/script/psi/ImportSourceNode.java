package io.nop.idea.plugin.lang.script.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceSet;
import org.jetbrains.annotations.NotNull;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-29
 */
public class ImportSourceNode extends RuleSpecNode {

    public ImportSourceNode(@NotNull ASTNode node) {
        super(node);
    }

    /** 构造 Java 相关的引用对象，从而支持自动补全、引用跳转、文档显示等 */
    @Override
    public PsiReference @NotNull [] getReferences() {
        String fqn = getText();

        JavaClassReferenceProvider provider = new JavaClassReferenceProvider();
        // 支持解析包名：JavaClassReference#advancedResolveInner
        provider.setOption(JavaClassReferenceProvider.ADVANCED_RESOLVE, true);

        JavaClassReferenceSet refSet = new JavaClassReferenceSet(fqn, this, 0, false, provider);

        return refSet.getReferences();
    }
}
