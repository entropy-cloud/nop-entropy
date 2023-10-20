/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.core.model;

import io.nop.auth.core.model._gen._RoleDataAuthModel;
import io.nop.core.lang.eval.IEvalPredicate;
import io.nop.core.lang.xml.IXNodeGenerator;

public class RoleDataAuthModel extends _RoleDataAuthModel {
    public RoleDataAuthModel() {

    }

    public void mergeChecker(IEvalPredicate checker) {
        if (checker == null)
            return;

        if (this.getCheck() == null) {
            this.setCheck(checker);
        } else {
            this.setCheck(getCheck().and(checker));
        }
    }

    public void mergeFilter(IXNodeGenerator filter) {
        if (filter == null)
            return;

        if (getFilter() == null) {
            setFilter(filter);
        } else {
            setFilter(getFilter().both(filter));
        }
    }
}
