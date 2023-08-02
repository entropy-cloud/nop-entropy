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
