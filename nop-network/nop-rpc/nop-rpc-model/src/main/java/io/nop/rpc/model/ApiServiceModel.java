/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.model;

import io.nop.api.core.util.INeedInit;
import io.nop.commons.util.StringHelper;
import io.nop.rpc.model._gen._ApiServiceModel;

public class ApiServiceModel extends _ApiServiceModel implements IWithOptions, INeedInit {
    private transient ApiModel apiModel;

    public ApiServiceModel() {

    }

    @Override
    public void init() {
        init(null);
    }

    public void init(ApiModel apiModel) {
        if (apiModel != null) {
            this.apiModel = apiModel;
        }

        if (getMethods() != null) {
            for (ApiMethodModel method : getMethods()) {
                method.init(this);
            }
        }
    }

    public ApiModel getApiModel() {
        return apiModel;
    }

    public String getSimpleClassName() {
        return StringHelper.simpleClassName(getClassName());
    }

    public String getBizObjName() {
        String bizObjName = (String) getExtProp("bizObjName");
        return StringHelper.isEmpty(bizObjName) ? getName() : bizObjName;
    }

    @Override
    public String getClassName() {
        return StringHelper.normalizeClassName(super.getClassName(), getApiPackageName(), false);
    }

    public String getPackageName() {
        return StringHelper.packageName(getClassName());
    }

    public String getPackagePath() {
        return StringHelper.classNameToPath(getPackageName());
    }

    public String getApiPackageName() {
        return apiModel == null ? null : apiModel.getApiPackageName();
    }
}
