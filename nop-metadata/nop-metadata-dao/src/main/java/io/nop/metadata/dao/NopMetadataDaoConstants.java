package io.nop.metadata.dao;

public interface NopMetadataDaoConstants extends _NopMetadataDaoConstants{
    /**
     * 实体侧稳定判定所用字典值镜像（审计 MD-2）：dao 模块不可依赖 nop-metadata-core 的
     * _NopMetadataCoreConstants（依赖方向），值与 dict/meta 的 table-type、datasource-status 一致。
     */
    String TABLE_TYPE_ENTITY = "entity";
    String TABLE_TYPE_EXTERNAL = "external";
    String TABLE_TYPE_SQL = "sql";
    String DATASOURCE_STATUS_DISABLED = "DISABLED";

}
