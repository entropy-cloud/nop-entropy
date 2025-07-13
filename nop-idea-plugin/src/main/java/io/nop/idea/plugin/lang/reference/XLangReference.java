package io.nop.idea.plugin.lang.reference;

import com.intellij.psi.PsiReference;

/**
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-23
 */
public interface XLangReference extends PsiReference {

    /** 得到告警信息，如，文件不存在、引用目标不存在等 */
    default String getMessage() {return null;}
}
