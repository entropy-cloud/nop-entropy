/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.lang.reference;

import java.util.function.Function;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttribute;
import io.nop.idea.plugin.messages.NopPluginBundle;
import io.nop.idea.plugin.utils.XmlPsiHelper;
import io.nop.idea.plugin.vfs.NopVirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * {@link io.nop.xlang.xdef.XDefConstants#STD_DOMAIN_XDEF_REF xdef-ref} 类型的值引用
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-07-13
 */
public class XLangStdDomainXdefRefReference extends XLangReferenceBase {
    private final String attrValue;

    public XLangStdDomainXdefRefReference(PsiElement myElement, TextRange myRangeInElement, String attrValue) {
        super(myElement, myRangeInElement);
        this.attrValue = attrValue;
    }

    @Override
    public @Nullable PsiElement resolveInner() {
        Project project = myElement.getProject();
        // - /nop/schema/xdef.xdef:
        //   - `<schema xdef:ref="schema-node.xdef" />`
        //   - `<item xdef:ref="ISchema" />`
        // - /nop/schema/schema/schema-node.xdef:
        //   `<schema ref="/test/test-filter.xdef#FilterCondition" />`

        String ref;
        String path = null;
        PsiElement target;
        // 含有后缀的，视为文件引用：相对路径 .. 可能出现在开头，故而，检查最后一个 . 的位置
        if (attrValue.lastIndexOf('.') > 0) {
            int hashIndex = attrValue.indexOf('#');

            path = hashIndex > 0 ? attrValue.substring(0, hashIndex) : attrValue;
            ref = hashIndex > 0 ? attrValue.substring(hashIndex + 1) : null;

            path = XmlPsiHelper.getNopVfsAbsolutePath(path, myElement);

            target = new NopVirtualFile(project, path, ref != null ? createTargetResolver(ref) : null);
            if (((NopVirtualFile) target).hasEmptyChildren()) {
                target = null;
            }
        }
        // 否则，视为名字引用
        else {
            ref = attrValue;
            // Note: 只能引用当前文件（不一定是 vfs）内的名字
            target = createTargetResolver(ref).apply(myElement.getContainingFile());
        }

        if (target == null) {
            String msg = ref == null
                         ? NopPluginBundle.message("xlang.annotation.reference.vfs-file-not-found", path)
                         : path == null
                           ? NopPluginBundle.message("xlang.annotation.reference.xdef-ref-not-found", ref)
                           : NopPluginBundle.message("xlang.annotation.reference.xdef-ref-not-found-in-path",
                                                     ref,
                                                     path);
            setUnresolvedMessage(msg);
        }
        return target;
    }

    private Function<PsiFile, PsiElement> createTargetResolver(String ref) {
        return (file) -> (XmlAttribute) XmlPsiHelper.findFirstElement(file, (element) -> {
            if (element instanceof XmlAttribute attr) {
                String name = attr.getName();
                String value = attr.getValue();

                // Note: xdef-ref 引用的只能是 xdef:name 命名的节点
                return ("xdef:name".equals(name) //
                        || "meta:name".equals(name) //
                       ) && ref.equals(value);
            }
            return false;
        });
    }
}
