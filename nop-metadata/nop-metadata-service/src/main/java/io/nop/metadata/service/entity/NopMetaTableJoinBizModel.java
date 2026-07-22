/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */

package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.biz.INopMetaTableJoinBiz;
import io.nop.metadata.core._NopMetadataCoreConstants;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTableJoin;
import io.nop.metadata.service.field.MetaTableFieldResolver;
import io.nop.metadata.service.field.ResolvedTableField;
import io.nop.metadata.service.NopMetadataException;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 表关联 BizModel（架构基线 §2.5.2 D2/D4 / plan 0700-2 item 1.4 + plan 0700-1 sql/external 端点扩展）：
 * 基线 CRUD + save 端点一致性校验。
 *
 * <p>save 校验（item 1.1 裁定的 save override 落点 + plan 0700-1 D1 端点建模放宽）：保存 Join 时按端点类型校验：
 * <ul>
 *   <li><b>entity 端点</b>（{@code leftEntityId}/{@code rightEntityId} 非空）：对应 {@link NopMetaEntity} 存在 +
 *       {@code leftField}/{@code rightField} 属于该实体字段集合（经 entity→{@link NopMetaEntityField} 解析）。</li>
 *   <li><b>table 端点</b>（{@code leftTableId}/{@code rightTableId} 非空，plan 0700-1 新增）：对应 {@link NopMetaTable}
 *       存在且 {@code tableType ∈ {external, sql}}（entity-type 逻辑表作为端点应走 entity 路径，见 D1）+
 *       {@code leftField}/{@code rightField} 属于该表可解析列集合（external→buildSql JSON columnName；
 *       sql→SELECT 解析）。</li>
 *   <li><b>端点互斥</b>（D1）：同一端点的 {@code entityId}/{@code tableId} 同时非空 → 显式失败；同时为空 → 显式失败
 *       （端点 mandatory 从 entityId-only 放宽为「entity/table 二选一」，即 {@code NopMetadataErrors.ERR_JOIN_ENTITY_ID_NULL} 的放宽裁定）。</li>
 * </ul>
 * 不一致显式失败抛 inline {@link ErrorCode}。{@code joinType} 已由 dict {@code meta/join-type} 校验。
 *
 * <p>可用性边界：本 BizModel 交付「建模 + 防悬空校验」。sql/external 端点的 JOIN 查询执行属 successor plan 0700-2。
 */
@BizModel("NopMetaTableJoin")
public class NopMetaTableJoinBizModel extends CrudBizModel<NopMetaTableJoin> implements INopMetaTableJoinBiz {


    /** 跨表类型字段解析器（无状态）。 */
    private final MetaTableFieldResolver fieldResolver = new MetaTableFieldResolver();

    public NopMetaTableJoinBizModel() {
        setEntityName(NopMetaTableJoin.class.getName());
    }

    /**
     * save override（item 1.1 裁定的 save override 新模式 + plan 0700-1 端点建模放宽）：持久化前校验
     * left/right 端点存在 + 字段归属（entity 端点或 external/sql table 端点）。
     *
     * <p>校验通过后委托 {@code super.save(...)} 走默认持久化逻辑。端点/字段集合解析失败显式抛 ErrorCode
     * （不静默存入悬空关联、不静默跳过校验）。
     */
    @Override
    public NopMetaTableJoin save(@Name("data") Map<String, Object> data, IServiceContext context) {
        String metaTableId = stringOf(data, NopMetaTableJoin.PROP_NAME_metaTableId);
        String leftEntityId = stringOf(data, NopMetaTableJoin.PROP_NAME_leftEntityId);
        String rightEntityId = stringOf(data, NopMetaTableJoin.PROP_NAME_rightEntityId);
        String leftTableId = stringOf(data, NopMetaTableJoin.PROP_NAME_leftTableId);
        String rightTableId = stringOf(data, NopMetaTableJoin.PROP_NAME_rightTableId);
        String leftField = stringOf(data, NopMetaTableJoin.PROP_NAME_leftField);
        String rightField = stringOf(data, NopMetaTableJoin.PROP_NAME_rightField);
        validateJoin(metaTableId, leftEntityId, rightEntityId, leftTableId, rightTableId,
                leftField, rightField);
        return super.save(data, context);
    }

    private void validateJoin(String metaTableId, String leftEntityId, String rightEntityId,
                              String leftTableId, String rightTableId,
                              String leftField, String rightField) {
        if (metaTableId == null || metaTableId.isEmpty()) {
            return;
        }
        IEntityDao<NopMetaEntityField> fieldDao = daoFor(NopMetaEntityField.class);
        IEntityDao<NopMetaTable> tableDao = daoFor(NopMetaTable.class);
        validateJoinSide(metaTableId, "left", leftEntityId, leftTableId, leftField, fieldDao, tableDao);
        validateJoinSide(metaTableId, "right", rightEntityId, rightTableId, rightField, fieldDao, tableDao);
    }

    /**
     * 校验单侧端点（D1 entity/table 二选一互斥 + 字段归属）。
     *
     * <p>端点解析：{@code entityId}/{@code tableId} 同时非空 → 互斥失败；同时为空 → 端点 mandatory 失败
     * （{@code NopMetadataErrors.ERR_JOIN_ENTITY_ID_NULL} 放宽为 entity/table 二选一）。entity 端点走实体字段集合校验，
     * table 端点走表可解析列集合校验（tableType 须 external/sql）。
     */
    private void validateJoinSide(String metaTableId, String side, String entityId, String tableId,
                                  String field, IEntityDao<NopMetaEntityField> fieldDao,
                                  IEntityDao<NopMetaTable> tableDao) {
        boolean hasEntity = entityId != null && !entityId.isEmpty();
        boolean hasTable = tableId != null && !tableId.isEmpty();
        if (hasEntity && hasTable) {
            // entity/table 互斥：同一端点同时设置 entityId 和 tableId——显式失败（不静默猜测语义）
            throw new NopMetadataException(NopMetadataErrors.ERR_JOIN_ENDPOINT_BOTH_SET)
                    .param("metaTableId", metaTableId).param("side", side)
                    .param("entityId", entityId).param("tableId", tableId);
        }
        if (!hasEntity && !hasTable) {
            // 端点 mandatory：entity/table 二选一（放宽原 NopMetadataErrors.ERR_JOIN_ENTITY_ID_NULL 的 entityId-only 语义）
            throw new NopMetadataException(NopMetadataErrors.ERR_JOIN_ENTITY_ID_NULL)
                    .param("metaTableId", metaTableId).param("side", side);
        }
        if (hasEntity) {
            validateEntityEndpoint(metaTableId, side, entityId, field, fieldDao);
        } else {
            validateTableEndpoint(metaTableId, side, tableId, field, tableDao, fieldDao);
        }
    }

    /** entity 端点校验：实体存在 + 字段属于该实体字段集合。 */
    private void validateEntityEndpoint(String metaTableId, String side, String entityId, String field,
                                        IEntityDao<NopMetaEntityField> fieldDao) {
        IEntityDao<NopMetaEntity> entityDao = daoFor(NopMetaEntity.class);
        NopMetaEntity entity = entityDao.getEntityById(entityId);
        if (entity == null) {
            throw new NopMetadataException(NopMetadataErrors.ERR_JOIN_ENTITY_NOT_FOUND)
                    .param("metaTableId", metaTableId).param("side", side).param("entityId", entityId);
        }
        if (field == null || field.isEmpty()) {
            // 字段名空——交由 super.save 走框架校验，此处不重复报错
            return;
        }
        // 校验字段属于该实体字段集合（经 entity→NopMetaEntityField 解析；空集由 resolver 显式失败）
        Set<String> fieldNames = new LinkedHashSet<>();
        for (ResolvedTableField f : fieldResolver.resolveEntityFieldsByEntityId(entityId, fieldDao)) {
            fieldNames.add(f.getName());
        }
        if (!fieldNames.contains(field)) {
            throw new NopMetadataException(NopMetadataErrors.ERR_JOIN_FIELD_NOT_IN_ENTITY)
                    .param("metaTableId", metaTableId).param("side", side)
                    .param("entityId", entityId).param("field", field)
                    .param("availableFields", fieldNames);
        }
    }

    /**
     * table 端点校验（plan 0700-1 D1）：表存在 + tableType ∈ {external, sql} + 字段属于该表可解析列集合。
     *
     * <p>tableType=entity 的 NopMetaTable 作为 table 端点显式失败（应走 entityId 路径，避免解析路径混合）。
     * 列集合解析失败（buildSql 损坏 / sourceSql 不可解析）由 resolver 显式抛 ErrorCode（不静默空集放行）。
     */
    private void validateTableEndpoint(String metaTableId, String side, String tableId, String field,
                                       IEntityDao<NopMetaTable> tableDao,
                                       IEntityDao<NopMetaEntityField> fieldDao) {
        NopMetaTable table = tableDao.getEntityById(tableId);
        if (table == null) {
            throw new NopMetadataException(NopMetadataErrors.ERR_JOIN_TABLE_NOT_FOUND)
                    .param("metaTableId", metaTableId).param("side", side).param("tableId", tableId);
        }
        String tableType = table.getTableType();
        if (!_NopMetadataCoreConstants.TABLE_TYPE_EXTERNAL.equals(tableType)
                && !_NopMetadataCoreConstants.TABLE_TYPE_SQL.equals(tableType)) {
            // table 端点仅允许 external/sql；entity-type 逻辑表应走 entityId 路径（D1）
            throw new NopMetadataException(NopMetadataErrors.ERR_JOIN_TABLE_TYPE_NOT_ALLOWED)
                    .param("metaTableId", metaTableId).param("side", side)
                    .param("tableId", tableId).param("tableType", String.valueOf(tableType));
        }
        if (field == null || field.isEmpty()) {
            // 字段名空——交由 super.save 走框架校验，此处不重复报错
            return;
        }
        // 校验字段属于该表可解析列集合（external→buildSql JSON；sql→SELECT 解析；空集/损坏由 resolver 显式失败）
        Set<String> columnNames = fieldResolver.resolveFieldNames(table, fieldDao);
        if (!columnNames.contains(field)) {
            throw new NopMetadataException(NopMetadataErrors.ERR_JOIN_FIELD_NOT_IN_TABLE)
                    .param("metaTableId", metaTableId).param("side", side)
                    .param("tableId", tableId).param("field", field)
                    .param("availableFields", columnNames);
        }
    }

    private static String stringOf(Map<String, Object> data, String key) {
        Object v = data.get(key);
        return v == null ? null : v.toString();
    }
}
