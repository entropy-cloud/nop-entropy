package io.nop.metadata.dao.entity;

import io.nop.api.core.annotations.biz.BizObjName;
import io.nop.metadata.dao.entity._gen._NopMetaEntity;


@BizObjName("NopMetaEntity")
public class NopMetaEntity extends _NopMetaEntity {

    /**
     * 概念缩减（plan 2261）：原逻辑表实体的类型判定助手归一到实体保留类。
     * 判定键为 entityKind（dict meta/entity-kind），常量见 NopMetadataDaoConstants。
     */
    public boolean isPhysical() {
        return io.nop.metadata.dao.NopMetadataDaoConstants.ENTITY_KIND_PHYSICAL.equals(getEntityKind());
    }

    public boolean isSqlView() {
        return io.nop.metadata.dao.NopMetadataDaoConstants.ENTITY_KIND_SQL_VIEW.equals(getEntityKind());
    }

    public boolean isExternal() {
        return io.nop.metadata.dao.NopMetadataDaoConstants.ENTITY_KIND_EXTERNAL.equals(getEntityKind());
    }
}
