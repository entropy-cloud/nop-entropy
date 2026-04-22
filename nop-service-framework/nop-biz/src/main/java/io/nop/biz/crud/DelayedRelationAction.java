/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.biz.crud;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.orm.IOrmEntity;
import io.nop.orm.model.IEntityRelationModel;
import io.nop.xlang.xmeta.ObjRelationWriteMode;

public class DelayedRelationAction {
    private String propName;
    private ObjRelationWriteMode writeMode;
    private String bizAction;
    private String targetBizObjName;
    private Object payload;
    private FieldSelectionBean selection;
    private IOrmEntity parentEntity;
    private IEntityRelationModel relationModel;
    private int order;

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

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }
}
