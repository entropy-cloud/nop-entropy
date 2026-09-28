package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataHelper;
import io.nop.api.core.annotations.core.Name;
import io.nop.biz.crud.CrudBizModel;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.context.IServiceContext;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.biz.INopMetaEntityBiz;
import io.nop.metadata.biz.INopMetaEntityJoinBiz;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaEntityJoin;
import io.nop.metadata.service.field.MetaEntityFieldResolver;
import io.nop.metadata.service.field.ResolvedTableField;
import io.nop.metadata.service.NopMetadataException;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Inject;

/**
 * 实体关联 BizModel（架构基线 §2.5.2 D2/D4 / plan 0700-2 item 1.4；plan 2261 概念缩减——
 * 原 entity/table 双端点收敛为纯实体端点）：基线 CRUD + save 端点一致性校验。
 *
 * <p>save 校验：保存 Join 时按端点校验——{@code leftEntityId}/{@code rightEntityId} 对应
 * {@link NopMetaEntity} 存在 + {@code leftField}/{@code rightField} 属于该实体字段集合
 * （经 entity→{@link NopMetaEntityField} 解析）。
 */
@BizModel("NopMetaEntityJoin")
public class NopMetaEntityJoinBizModel extends CrudBizModel<NopMetaEntityJoin> implements INopMetaEntityJoinBiz {

    @Inject
    protected INopMetaEntityBiz entityBiz;

    private final MetaEntityFieldResolver fieldResolver = new MetaEntityFieldResolver();

    public NopMetaEntityJoinBizModel() {
        setEntityName(NopMetaEntityJoin.class.getName());
    }

    @Override
    public NopMetaEntityJoin save(@Name("data") Map<String, Object> data, IServiceContext context) {
        // （不 NPE 抢先——validateJoin 内 stringOf 会先解引用 data）
        if (CollectionHelper.isEmptyMap(data)) {
            return super.save(data, context);
        }
        validateJoin(data, context);
        return super.save(data, context);
    }

    private void validateJoin(Map<String, Object> data, IServiceContext context) {
        String metaEntityId = NopMetadataHelper.stringOf(data,
                NopMetaEntityJoin.PROP_NAME_metaEntityId);
        if (metaEntityId == null || metaEntityId.isEmpty()) {
            return;
        }
        // P1-6（plan 2026-08-15-1913-3 轨 1）：update 路径 joinId 自 data map 下沉穿参
        String joinId = NopMetadataHelper.stringOf(data,
                NopMetaEntityJoin.PROP_NAME_joinId);
        validateJoinSide(metaEntityId, joinId, "left",
                NopMetadataHelper.stringOf(data,
                        NopMetaEntityJoin.PROP_NAME_leftEntityId),
                NopMetadataHelper.stringOf(data,
                        NopMetaEntityJoin.PROP_NAME_leftField),
                context);
        validateJoinSide(metaEntityId, joinId, "right",
                NopMetadataHelper.stringOf(data,
                        NopMetaEntityJoin.PROP_NAME_rightEntityId),
                NopMetadataHelper.stringOf(data,
                        NopMetaEntityJoin.PROP_NAME_rightField),
                context);
    }

    /**
     * 校验单侧端点（plan 2261：纯实体端点 mandatory + 字段归属）。
     *
     * @param joinId 既有 Join 主键（update 路径自身份下沉，create 路径为空）
     */
    private void validateJoinSide(String metaEntityId, final String joinId,
                                  String side, String entityId, String field, IServiceContext context) {
        boolean hasEntity = entityId != null && !entityId.isEmpty();
        if (!hasEntity) {
            // 端点 mandatory（原 entity/table 二选一放宽随表概念删除收敛为 entity-only）
            throw new NopMetadataException(NopMetadataErrors.ERR_JOIN_ENTITY_ID_NULL)
                    .param("metaEntityId", metaEntityId).param("side", side);
        }
        // resolver 边界：MetaEntityFieldResolver API 消费 IEntityDao（保留 dao 直连，plan 353 MD-1 裁定）
        validateEntityEndpoint(metaEntityId, side, entityId, field,
                daoFor(NopMetaEntityField.class), context);
    }

    /** entity 端点校验：实体存在 + 字段属于该实体字段集合。 */
    private void validateEntityEndpoint(String metaEntityId, String side, String entityId, String field,
                                        IEntityDao<NopMetaEntityField> fieldDao, IServiceContext context) {
        NopMetaEntity entity = entityBiz.get(entityId, false, context);
        if (entity == null) {
            throw new NopMetadataException(NopMetadataErrors.ERR_JOIN_ENTITY_NOT_FOUND)
                    .param("metaEntityId", metaEntityId).param("side", side).param("entityId", entityId);
        }
        if (field == null || field.isEmpty()) {
            // 字段名空——交由 super.save 走框架校验，此处不重复报错
            return;
        }
        // 校验字段属于该实体可解析字段集合（plan 2261 修复：按 entityKind 分派——PHYSICAL 读
        // NopMetaEntityField 行，EXTERNAL/SQL_VIEW 解析 externalColumns/sourceSql；与执行路径同源，
        // 修复原实现"外部/SQL 视图端点恒因无字段行失败"的写路径回归）
        Set<String> fieldNames = new LinkedHashSet<>();
        for (ResolvedTableField f : fieldResolver.resolve(entity, fieldDao)) {
            fieldNames.add(f.getName());
        }
        if (!fieldNames.contains(field)) {
            throw new NopMetadataException(NopMetadataErrors.ERR_JOIN_FIELD_NOT_IN_ENTITY)
                    .param("metaEntityId", metaEntityId).param("side", side)
                    .param("entityId", entityId).param("field", field)
                    .param("availableFields", fieldNames);
        }
    }
}
