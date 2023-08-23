/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.biz.crud;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.BizConstants;
import io.nop.biz.api.IBizObject;
import io.nop.biz.api.IBizObjectManager;
import io.nop.commons.util.StringHelper;
import io.nop.core.type.IGenericType;
import io.nop.orm.component.JsonOrmComponent;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.IObjPropMeta;
import io.nop.xlang.xmeta.ISchema;

import java.util.ArrayList;
import java.util.List;

import static io.nop.biz.BizErrors.ARG_BIZ_OBJ_NAME;
import static io.nop.biz.BizErrors.ARG_DISPLAY_NAME;
import static io.nop.biz.BizErrors.ARG_META_PATH;
import static io.nop.biz.BizErrors.ARG_PROP_NAME;
import static io.nop.biz.BizErrors.ERR_BIZ_INVALID_META_PATH;
import static io.nop.biz.BizErrors.ERR_BIZ_PROP_IS_NOT_COLLECTION;
import static io.nop.biz.BizErrors.ERR_BIZ_PROP_IS_NOT_MAP;

public class BizSchemaHelper {
    public static String getBizObjNameFromMetaPath(String path) {
        if (StringHelper.isEmpty(path))
            return null;

        if (!path.endsWith(BizConstants.FILE_POSTFIX_XMETA)) {
            throw new NopException(ERR_BIZ_INVALID_META_PATH)
                    .param(ARG_META_PATH, path);
        }

        return StringHelper.fileNameNoExt(path);
    }

    public static ISchema getPropSchema(IObjPropMeta propMeta, boolean list, IBizObjectManager bizObjectManager,
                                        String baseBizObjName) {
        if (propMeta == null)
            return null;

        ISchema schema = null;
        String bizObjName = propMeta.getBizObjName();

        if(bizObjName == null && list)
            bizObjName = propMeta.getItemBizObjName();

        if (bizObjName != null) {
            IBizObject bizObj = bizObjectManager.getBizObject(bizObjName);
            IObjMeta objMeta = bizObj.getObjMeta();
            schema = objMeta == null ? null : objMeta.getRootSchema();
        }

        if (schema == null) {
            schema = propMeta.getSchema();
        }

        if (schema != null) {
            if (list) {
                if (schema.getItemSchema() != null) {
                    schema = schema.getItemSchema();
                } else {
                    if (!isCollection(propMeta))
                        throw newError(ERR_BIZ_PROP_IS_NOT_COLLECTION, propMeta).param(ARG_BIZ_OBJ_NAME, baseBizObjName);
                    if (schema instanceof IObjMeta)
                        return schema;
                    return null;
                }
            } else {
                if (!isAllowMap(propMeta))
                    throw newError(ERR_BIZ_PROP_IS_NOT_MAP, propMeta).param(ARG_BIZ_OBJ_NAME, baseBizObjName);
            }
        }
        return schema;
    }

    public static NopException newError(ErrorCode errorCode, IObjPropMeta propMeta) {
        return new NopException(errorCode).source(propMeta).param(ARG_PROP_NAME, propMeta.getName())
                .param(ARG_DISPLAY_NAME, propMeta.getDisplayName());
    }

    private static boolean isCollection(IObjPropMeta propMeta) {
        IGenericType type = propMeta.getType();
        if (type != null) {
            if (type.isCollectionLike())
                return true;
            if (type.getRawClass() == JsonOrmComponent.class)
                return true;
            return false;
        } else {
            return true;
        }
    }

    private static boolean isAllowMap(IObjPropMeta propMeta) {
        IGenericType type = propMeta.getType();
        if (type == null)
            return true;

        if (type.isCollectionLike())
            return false;

        if (type.getStdDataType().isSimpleType())
            return false;

        return true;
    }

    public static List<CascadePropMeta> getCascadeProps(IObjMeta objMeta) {
        List<CascadePropMeta> props = new ArrayList<>();
        if (objMeta == null)
            return props;

        for (IObjPropMeta propMeta : objMeta.getProps()) {
            if (!propMeta.containsTag(BizConstants.TAG_CASCADE_DELETE))
                continue;

            String kind = (String) propMeta.prop_get(BizConstants.EXT_KIND);
            if (kind == null)
                continue;

            String refBizObj = propMeta.getBizObjName();
            if (refBizObj == null)
                continue;

            boolean toMany = BizConstants.PROP_KIND_TO_MANY.equals(kind);
            if (!toMany) {
                if (!BizConstants.PROP_KIND_TO_ONE.equals(kind))
                    continue;
                toMany = false;
            }
            props.add(new CascadePropMeta(propMeta, true, refBizObj, toMany));
        }
        return props;
    }
}
