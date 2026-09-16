package io.nop.metadata.dao;

public interface NopMetadataDaoConstants extends _NopMetadataDaoConstants{
    /**
     * 实体侧稳定判定所用字典值镜像（审计 MD-2）：dao 模块不可依赖 nop-metadata-core 的
     * _NopMetadataCoreConstants（依赖方向），值与 dict/meta 的 entity-kind、datasource-status 一致。
     */
    String ENTITY_KIND_PHYSICAL = "PHYSICAL";
    String ENTITY_KIND_EXTERNAL = "EXTERNAL";
    String ENTITY_KIND_SQL_VIEW = "SQL_VIEW";
    String DATASOURCE_STATUS_DISABLED = "DISABLED";

}
