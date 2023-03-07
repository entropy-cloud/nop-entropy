/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.config.model;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.core.resource.component.AbstractComponentModel;

import java.util.Map;

@DataBean
public class ConfigModel extends AbstractComponentModel {
    private Map<String, ConfigVarModel> vars;

    public ConfigVarModel getVar(String varName) {
        return vars == null ? null : vars.get(varName);
    }

    public Map<String, ConfigVarModel> getVars() {
        return vars;
    }

    public void setVars(Map<String, ConfigVarModel> vars) {
        this.vars = vars;
    }
}
