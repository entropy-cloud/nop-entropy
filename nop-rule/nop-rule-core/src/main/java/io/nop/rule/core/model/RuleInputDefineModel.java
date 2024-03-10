/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rule.core.model;

import io.nop.rule.core.model._gen._RuleInputDefineModel;
import io.nop.xlang.xmeta.ISchema;
import io.nop.xlang.xmeta.ObjMetaHelper;

public class RuleInputDefineModel extends _RuleInputDefineModel {
    public RuleInputDefineModel() {

    }

    public String getPropDisplayName(String propPath) {
        ISchema schema = getSchema();
        return ObjMetaHelper.getDisplayName(schema, propPath);
    }
}
