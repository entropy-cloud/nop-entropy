/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.model.utils;

import io.nop.orm.model.OrmModelConstants;
import io.nop.xlang.xmeta.IObjPropMeta;

public class ManyToManyPropMeta {
    private final IObjPropMeta propMeta;

    public ManyToManyPropMeta(IObjPropMeta propMeta) {
        this.propMeta = propMeta;
    }

    public IObjPropMeta getPropMeta() {
        return propMeta;
    }

    public String getRelatedEntityName() {
        String relatedEntityName = propMeta.getItemBizObjName();
        if (relatedEntityName == null)
            relatedEntityName = propMeta.getBizObjName();
        return relatedEntityName;
    }

    public String getJoinLeftProp() {
        return (String) propMeta.prop_get(OrmModelConstants.EXT_JOIN_LEFT_PROP);
    }

    public String getJoinRightProp() {
        return (String) propMeta.prop_get(OrmModelConstants.EXT_JOIN_RIGHT_PROP);
    }

    public String getManyToManyRefProp() {
        return (String) propMeta.prop_get(OrmModelConstants.ORM_MANY_TO_MANY_REF_PROP);
    }

//    public String getManyToManyPropName1() {
//        return (String) propMeta.prop_get(OrmModelConstants.ORM_MANY_TO_MANY_PROP_NAME1);
//    }
//
//    public String getManyToManyPropName2() {
//        return (String) propMeta.prop_get(OrmModelConstants.ORM_MANY_TO_MANY_PROP_NAME2);
//    }
//
//    public String getManyToManyDisplayName1() {
//        return (String) propMeta.prop_get(OrmModelConstants.ORM_MANY_TO_MANY_DISPLAY_NAME1);
//    }
//
//    public String getManyToManyDisplayName2() {
//        return (String) propMeta.prop_get(OrmModelConstants.ORM_MANY_TO_MANY_DISPLAY_NAME2);
//    }
}
