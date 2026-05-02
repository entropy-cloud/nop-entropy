/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.crud;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.api.IBizObject;
import io.nop.biz.api.IBizObjectManager;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import io.nop.dao.api.IDaoProvider;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmEntitySet;
import io.nop.orm.OrmConstants;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.xlang.xmeta.ObjRelationWriteMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.nop.biz.BizConstants.METHOD_DELETE;
import static io.nop.biz.BizErrors.ARG_BIZ_OBJ_NAME;
import static io.nop.biz.BizErrors.ARG_PROP_NAME;
import static io.nop.biz.BizErrors.ARG_PROP_VALUE;
import static io.nop.biz.BizErrors.ERR_BIZ_UNKNOWN_REF_ENTITY_WITH_PROP;

public class DelayedRelationAction implements IDelayedAction {
    private final IBizObjectManager bizObjectManager;
    private final IDaoProvider daoProvider;

    private String propName;
    private ObjRelationWriteMode writeMode;
    private String bizAction;
    private String targetBizObjName;
    private Object payload;
    private FieldSelectionBean selection;
    private IOrmEntity parentEntity;
    private IEntityRelationModel relationModel;
    private int order;

    public DelayedRelationAction(IBizObjectManager bizObjectManager, IDaoProvider daoProvider) {
        this.bizObjectManager = bizObjectManager;
        this.daoProvider = daoProvider;
    }

    @Override
    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public String getPropName() {
        return propName;
    }

    public void setPropName(String propName) {
        this.propName = propName;
    }

    public ObjRelationWriteMode getWriteMode() {
        return writeMode;
    }

    public void setWriteMode(ObjRelationWriteMode writeMode) {
        this.writeMode = writeMode;
    }

    public String getBizAction() {
        return bizAction;
    }

    public void setBizAction(String bizAction) {
        this.bizAction = bizAction;
    }

    public String getTargetBizObjName() {
        return targetBizObjName;
    }

    public void setTargetBizObjName(String targetBizObjName) {
        this.targetBizObjName = targetBizObjName;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    public FieldSelectionBean getSelection() {
        return selection;
    }

    public void setSelection(FieldSelectionBean selection) {
        this.selection = selection;
    }

    public IOrmEntity getParentEntity() {
        return parentEntity;
    }

    public void setParentEntity(IOrmEntity parentEntity) {
        this.parentEntity = parentEntity;
    }

    public IEntityRelationModel getRelationModel() {
        return relationModel;
    }

    public void setRelationModel(IEntityRelationModel relationModel) {
        this.relationModel = relationModel;
    }

    @Override
    public void execute(IServiceContext context) {
        if (writeMode != ObjRelationWriteMode.BIZ) {
            return;
        }

        if (StringHelper.isEmpty(targetBizObjName)) {
            throw new NopException(ERR_BIZ_UNKNOWN_REF_ENTITY_WITH_PROP)
                    .param(ARG_BIZ_OBJ_NAME, parentEntity != null ? parentEntity.orm_entityName() : null)
                    .param(ARG_PROP_NAME, propName)
                    .param(ARG_PROP_VALUE, relationModel.getRefEntityName());
        }

        if (relationModel.isToOneRelation()) {
            applyToOne(context);
        } else {
            applyToMany(context);
        }
    }

    private void applyToOne(IServiceContext context) {
        if ("unlink".equals(bizAction)) {
            parentEntity.orm_propValueByName(propName, null);
            return;
        }

        Object result = invokeBizAction(context);
        if (METHOD_DELETE.equals(bizAction)) {
            parentEntity.orm_propValueByName(propName, null);
            return;
        }

        IOrmEntity refEntity = resolveResult(result);
        if (refEntity != null) {
            parentEntity.orm_propValueByName(propName, refEntity);
        }
    }

    private void applyToMany(IServiceContext context) {
        IOrmEntitySet<IOrmEntity> refSet = parentEntity.orm_refEntitySet(propName);
        refSet.orm_forceLoad();

        if ("unlink".equals(bizAction)) {
            return;
        }

        Object result = invokeBizAction(context);
        if (METHOD_DELETE.equals(bizAction)) {
            removeEntityFromSet(refSet);
            return;
        }

        IOrmEntity refEntity = resolveResult(result);
        if (refEntity != null) {
            refSet.add(refEntity);
        }
    }

    private Object invokeBizAction(IServiceContext context) {
        IBizObject targetBizObj = bizObjectManager.getBizObject(targetBizObjName);
        if (METHOD_DELETE.equals(bizAction)) {
            Object id = extractId();
            if (StringHelper.isEmptyObject(id)) {
                return null;
            }
            Map<String, Object> req = new HashMap<>();
            req.put(OrmConstants.PROP_ID, StringHelper.toString(id, null));
            targetBizObj.invoke(METHOD_DELETE, req, null, context);
            return null;
        }

        Map<String, Object> req = buildRequest();
        return targetBizObj.invoke(bizAction, req, selection, context);
    }

    private Map<String, Object> buildRequest() {
        if (payload instanceof Map) {
            return new LinkedHashMap<>((Map<String, Object>) payload);
        }

        Map<String, Object> req = new LinkedHashMap<>();
        if (!StringHelper.isEmptyObject(payload)) {
            req.put(OrmConstants.PROP_ID, payload);
        }
        return req;
    }

    private IOrmEntity resolveResult(Object result) {
        if (result instanceof IOrmEntity) {
            return (IOrmEntity) result;
        }

        Object id = extractId();
        if (StringHelper.isEmptyObject(id)) {
            return null;
        }
        return (IOrmEntity) daoProvider.dao(relationModel.getRefEntityName()).loadEntityById(id);
    }

    private Object extractId() {
        if (StringHelper.isEmptyObject(payload)) {
            return null;
        }

        if (payload instanceof Map) {
            return ((Map<String, Object>) payload).get(OrmConstants.PROP_ID);
        }
        return payload;
    }

    private void removeEntityFromSet(IOrmEntitySet<IOrmEntity> refSet) {
        Object id = extractId();
        if (StringHelper.isEmptyObject(id)) {
            return;
        }

        List<IOrmEntity> toRemove = new ArrayList<>();
        for (IOrmEntity entity : refSet) {
            if (Objects.equals(entity.orm_idString(), StringHelper.toString(id, null))) {
                toRemove.add(entity);
            }
        }
        refSet.removeAll(toRemove);
    }
}
