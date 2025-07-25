/*
 * Copyright (c) 2017-2025 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.idea.plugin.resource;

import com.intellij.psi.PsiField;
import io.nop.api.core.beans.DictOptionBean;

/**
 * 根据枚举类所生成的 {@link DictOptionBean}
 * <p/>
 * 用于得到字典项的关联元素
 *
 * @author <a href="mailto:flytreeleft@crazydan.org">flytreeleft</a>
 * @date 2025-06-27
 */
public class EnumDictOptionBean extends DictOptionBean {
    public final PsiField target;

    public EnumDictOptionBean(PsiField target) {
        this.target = target;
    }
}
