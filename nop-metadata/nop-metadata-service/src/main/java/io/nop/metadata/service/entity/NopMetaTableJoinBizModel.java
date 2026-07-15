package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.biz.INopMetaTableJoinBiz;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaTableJoin;
import io.nop.metadata.service.field.MetaTableFieldResolver;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 表关联 BizModel（架构基线 §2.5.2 D2 / plan 0700-2 item 1.4）：基线 CRUD + save 实体/字段一致性校验。
 *
 * <p>save 校验（item 1.1 裁定的 save override 落点）：保存 Join 时校验：
 * <ul>
 *   <li>{@code leftEntityId} / {@code rightEntityId} 对应 {@link NopMetaEntity} 存在（不静默存入孤儿关联）</li>
 *   <li>{@code leftField} 属于 {@code leftEntityId} 实体字段集合、{@code rightField} 属于 {@code rightEntityId}
 *       实体字段集合（经 entity→{@link NopMetaEntityField} 解析）</li>
 * </ul>
 * 不一致显式失败抛 inline {@link ErrorCode}。{@code joinType} 已由 dict {@code meta/join-type} 校验。
 *
 * <p>首版范围（item 1.1 D2 裁定）：仅 entity 实体关联。sql/external 表的 join 语义为 follow-up
 * （Non-Blocking Follow-up，P4 前增量）。
 */
@BizModel("NopMetaTableJoin")
public class NopMetaTableJoinBizModel extends CrudBizModel<NopMetaTableJoin> implements INopMetaTableJoinBiz {

    static final ErrorCode ERR_JOIN_ENTITY_NOT_FOUND =
            ErrorCode.define("metadata.join-entity-not-found",
                    "Join references non-existent MetaEntity: {metaTableId} side={side} entityId={entityId}",
                    "metaTableId", "side", "entityId");
    static final ErrorCode ERR_JOIN_FIELD_NOT_IN_ENTITY =
            ErrorCode.define("metadata.join-field-not-in-entity",
                    "Join field does not belong to the referenced entity's field set: "
                            + "{metaTableId} side={side} entityId={entityId} field={field}; available={availableFields}",
                    "metaTableId", "side", "entityId", "field", "availableFields");
    static final ErrorCode ERR_JOIN_ENTITY_ID_NULL =
            ErrorCode.define("metadata.join-entity-id-null",
                    "Join side has null entityId (first version requires entity join only): "
                            + "{metaTableId} side={side}",
                    "metaTableId", "side");

    /** 跨表类型字段解析器（无状态）。 */
    private final MetaTableFieldResolver fieldResolver = new MetaTableFieldResolver();

    public NopMetaTableJoinBizModel() {
        setEntityName(NopMetaTableJoin.class.getName());
    }

    /**
     * save override（item 1.1 裁定的 save override 新模式）：持久化前校验 left/right 实体存在 + 字段归属。
     *
     * <p>校验通过后委托 {@code super.save(...)} 走默认持久化逻辑。实体/字段集合解析失败显式抛 ErrorCode
     * （不静默存入悬空关联、不静默跳过校验）。
     */
    @Override
    public NopMetaTableJoin save(@Name("data") Map<String, Object> data, IServiceContext context) {
        String metaTableId = stringOf(data, NopMetaTableJoin.PROP_NAME_metaTableId);
        String leftEntityId = stringOf(data, NopMetaTableJoin.PROP_NAME_leftEntityId);
        String rightEntityId = stringOf(data, NopMetaTableJoin.PROP_NAME_rightEntityId);
        String leftField = stringOf(data, NopMetaTableJoin.PROP_NAME_leftField);
        String rightField = stringOf(data, NopMetaTableJoin.PROP_NAME_rightField);
        validateJoin(metaTableId, leftEntityId, rightEntityId, leftField, rightField);
        return super.save(data, context);
    }

    private void validateJoin(String metaTableId, String leftEntityId, String rightEntityId,
                              String leftField, String rightField) {
        if (metaTableId == null || metaTableId.isEmpty()) {
            return;
        }
        IEntityDao<NopMetaEntityField> fieldDao = daoFor(NopMetaEntityField.class);
        validateJoinSide(metaTableId, "left", leftEntityId, leftField, fieldDao);
        validateJoinSide(metaTableId, "right", rightEntityId, rightField, fieldDao);
    }

    /** 校验单侧：实体存在 + 字段属于该实体字段集合。 */
    private void validateJoinSide(String metaTableId, String side, String entityId, String field,
                                  IEntityDao<NopMetaEntityField> fieldDao) {
        if (entityId == null || entityId.isEmpty()) {
            // 首版要求 entity join，entityId 为 mandatory 引用——显式失败（不静默跳过）
            throw new NopException(ERR_JOIN_ENTITY_ID_NULL)
                    .param("metaTableId", metaTableId).param("side", side);
        }
        IEntityDao<NopMetaEntity> entityDao = daoFor(NopMetaEntity.class);
        NopMetaEntity entity = entityDao.getEntityById(entityId);
        if (entity == null) {
            throw new NopException(ERR_JOIN_ENTITY_NOT_FOUND)
                    .param("metaTableId", metaTableId).param("side", side).param("entityId", entityId);
        }
        if (field == null || field.isEmpty()) {
            // 字段名空——交由 super.save 走框架校验，此处不重复报错
            return;
        }
        // 校验字段属于该实体字段集合（经 entity→NopMetaEntityField 解析；空集由 resolver 显式失败）
        Set<String> fieldNames = new LinkedHashSet<>();
        for (io.nop.metadata.service.field.ResolvedTableField f
                : fieldResolver.resolveEntityFieldsByEntityId(entityId, fieldDao)) {
            fieldNames.add(f.getName());
        }
        if (!fieldNames.contains(field)) {
            throw new NopException(ERR_JOIN_FIELD_NOT_IN_ENTITY)
                    .param("metaTableId", metaTableId).param("side", side)
                    .param("entityId", entityId).param("field", field)
                    .param("availableFields", fieldNames);
        }
    }

    private static String stringOf(Map<String, Object> data, String key) {
        Object v = data.get(key);
        return v == null ? null : v.toString();
    }
}
