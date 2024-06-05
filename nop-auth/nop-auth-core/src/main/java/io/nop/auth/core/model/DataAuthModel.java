/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.model;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.auth.core.model._gen._DataAuthModel;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.core.lang.eval.IEvalScope;

import java.util.Set;

public class DataAuthModel extends _DataAuthModel {
    public DataAuthModel() {

    }

    public void initCheckerFromFilter() {
        for (ObjDataAuthModel objAuth : this.getObjs()) {
            for (RoleDataAuthModel roleAuth : objAuth.getRoleAuths()) {
                if (roleAuth.getCheck() == null && roleAuth.getFilter() != null) {
                    roleAuth.setCheck(new FilterChecker(roleAuth.getFilter()));
                }
            }
        }
    }

    public void sort() {
        for (ObjDataAuthModel objModel : this.getObjs()) {
            objModel.sort();
        }
    }

    public Set<String> decideDynamicRoles(ObjDataAuthModel objAuth, IEvalScope scope) {
        IEvalAction decider = objAuth.getRoleDecider();
        if (decider == null)
            decider = this.getRoleDecider();
        if (decider == null)
            return null;
        return ConvertHelper.toCsvSet(decider.invoke(scope));
    }
}
