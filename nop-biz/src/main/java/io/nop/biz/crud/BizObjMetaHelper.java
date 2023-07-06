package io.nop.biz.crud;

import io.nop.api.core.exceptions.NopException;
import io.nop.biz.BizConstants;
import io.nop.biz.api.IBizObject;
import io.nop.biz.api.IBizObjectManager;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.IObjSchema;
import io.nop.xlang.xmeta.ISchema;

import static io.nop.auth.api.AuthApiErrors.ARG_BIZ_OBJ_NAME;
import static io.nop.biz.BizErrors.ARG_PROP_NAME;
import static io.nop.biz.BizErrors.ERR_BIZ_PROP_NOT_SORTABLE;
import static io.nop.biz.BizErrors.ERR_BIZ_UNKNOWN_PROP;

public class BizObjMetaHelper {
    public static IObjPropMeta getPropMeta(IObjSchema objMeta, String propName, String relatedTag,
                                           IBizObjectManager bizObjectManager) {
        IObjPropMeta propMeta = objMeta.getProp(propName);
        if (propMeta != null)
            return propMeta;

        // 故意从后向前查找
        int pos = propName.lastIndexOf('.');
        if (pos < 0)
            return null;

        // 如果是复合属性，则检查一下是否对应于关联对象上的属性
        IObjPropMeta baseProp = getPropMeta(objMeta, propName.substring(0, pos), relatedTag, bizObjectManager);
        if (baseProp == null)
            return null;

        // 不允许递归
        if (relatedTag != null && !baseProp.containsTag(relatedTag))
            return null;

        ISchema schema = propMeta.getSchema();
        if (schema == null)
            return null;

        String bizObjName = schema.getBizObjName();
        if (bizObjName == null)
            return null;

        String refPropName = propName.substring(pos + 1);

        IBizObject bizObj = bizObjectManager.getBizObject(bizObjName);
        IObjMeta refObjMeta = bizObj.getObjMeta();
        return refObjMeta.getProp(refPropName);
    }

    public static void checkPropSortable(String bizObjName,
                                         IObjSchema objMeta, String propName, IBizObjectManager bizObjectManager) {
        IObjPropMeta propMeta = getPropMeta(objMeta, propName, BizConstants.TAG_SORTABLE, bizObjectManager);
        if (propMeta != null) {
            if (!propMeta.isSortable()) {
                throw new NopException(ERR_BIZ_PROP_NOT_SORTABLE).param(ARG_BIZ_OBJ_NAME, bizObjName)
                        .param(ARG_PROP_NAME, propMeta.getName());
            }
        } else {
            throw new NopException(ERR_BIZ_UNKNOWN_PROP).param(ARG_BIZ_OBJ_NAME, bizObjName)
                    .param(ARG_PROP_NAME, propName);
        }
    }
}
