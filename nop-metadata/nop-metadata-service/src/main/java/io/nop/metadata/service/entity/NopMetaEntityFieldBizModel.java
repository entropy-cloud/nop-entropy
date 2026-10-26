package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.biz.crud.CrudBizModel;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.context.IServiceContext;
import io.nop.metadata.biz.INopMetaEntityBiz;
import io.nop.metadata.biz.INopMetaEntityFieldBiz;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.service.NopMetadataArgs;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;
import io.nop.metadata.service.NopMetadataHelper;
import io.nop.metadata.service.field.MetaComputedFieldEvaluator;
import io.nop.metadata.service.field.MetaComputedFieldProjector;
import io.nop.metadata.service.search.NopMetaSearchProcessor;
import io.nop.search.api.SearchableDoc;
import io.nop.xlang.ast.Expression;
import jakarta.inject.Inject;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@BizModel("NopMetaEntityField")
public class NopMetaEntityFieldBizModel extends CrudBizModel<NopMetaEntityField> implements INopMetaEntityFieldBiz {
    @Inject
    protected NopMetaSearchProcessor searchService;

    /** 跨聚合访问（plan 353 MD-1）：MetaEntity 经 Biz 接口而非 dao 直连。 */
    @Inject
    protected INopMetaEntityBiz entityBiz;

    public NopMetaEntityFieldBizModel() {
        setEntityName(NopMetaEntityField.class.getName());
    }

    @Override
    public NopMetaEntityField save(@Name("data") Map<String, Object> data, IServiceContext context) {
        // P2-19（plan 2026-08-16-0226-3）：null/empty data 提前委托基类，
        // 统一抛 ERR_BIZ_EMPTY_DATA_FOR_SAVE
        if (CollectionHelper.isEmptyMap(data)) {
            return super.save(data, context);
        }
        String businessDomainId = NopMetadataHelper.stringOf(data, NopMetaEntityField.PROP_NAME_businessDomainId);
        if (businessDomainId == null || businessDomainId.isEmpty()) {
            String metaEntityId = NopMetadataHelper.stringOf(data, NopMetaEntityField.PROP_NAME_metaEntityId);
            if (metaEntityId != null && !metaEntityId.isEmpty()) {
                NopMetaEntity parentEntity = entityBiz.get(metaEntityId, false, context);
                if (parentEntity != null && parentEntity.getBusinessDomainId() != null) {
                    data.put(NopMetaEntityField.PROP_NAME_businessDomainId, parentEntity.getBusinessDomainId());
                }
            }
        }
        Map<String, Object> effectiveData = data;
        if (!data.containsKey(NopMetaEntityField.PROP_NAME_fieldKind)) {
            // fieldKind 缺省 storage（设计 14 §3.2：存量行/缺省写路径行为零变化）
            effectiveData = new java.util.HashMap<>(data);
            effectiveData.put(NopMetaEntityField.PROP_NAME_fieldKind, MetaComputedFieldProjector.FIELD_KIND_STORAGE);
        }
        NopMetaEntityField saved = super.save(effectiveData, context);
        rejectInvalidFieldKind(saved);
        searchService.addToIndex("MetaEntityField", saved.getEntityFieldId(), toSearchableDoc(saved));
        return saved;
    }

    @Override
    public NopMetaEntityField update(@Name("data") Map<String, Object> data, IServiceContext context) {
        NopMetaEntityField updated = super.update(data, context);
        // update 写入面对称防护（改 fieldKind/computeExpr/mapToProp 撞守卫与 save 同族）
        rejectInvalidFieldKind(updated);
        return updated;
    }

    @Override
    public boolean delete(@Name("id") String id, IServiceContext context) {
        NopMetaEntityField before = requireEntity(id, "delete", context);
        boolean deleted = super.delete(id, context);
        searchService.removeFromIndex("MetaEntityField", id);
        return deleted;
    }

    /**
     * kind 互斥守卫（设计 14 §3.2/§3.6，save/update 双入口对称，命中 fail-loud 回滚）：
     * computed 无表达式拒绝、storage 带 expr/mapToProp 拒绝、alias 目标非法（不存在/
     * 自引用/非 storage）统一 {@code field-alias-target-unknown} 拒绝；computed 表达式
     * save-time 校验 = SIMPLE 特性集可解析 + 根标识符 ⊆ 实体字段集。
     */
    private void rejectInvalidFieldKind(NopMetaEntityField field) {
        String fieldKind = field.getFieldKind();
        if (fieldKind == null) {
            // DAO 直写行可无 fieldKind（旧数据语义 = storage）；守卫按 storage 审查
            fieldKind = MetaComputedFieldProjector.FIELD_KIND_STORAGE;
        } else if (!MetaComputedFieldProjector.isStorageKind(fieldKind)
                && !MetaComputedFieldProjector.FIELD_KIND_COMPUTED.equals(fieldKind)
                && !MetaComputedFieldProjector.FIELD_KIND_ALIAS.equals(fieldKind)) {
            // defense-in-depth：GraphQL 入口的非法 kind 已被 xmeta dict 校验拒绝
            throw new NopMetadataException(NopMetadataErrors.ERR_FIELD_KIND_UNKNOWN)
                    .param(NopMetadataArgs.ARG_FIELD_KIND, fieldKind);
        }

        String computeExpr = field.getComputeExpr();
        String mapToProp = field.getMapToProp();
        boolean hasExpr = computeExpr != null && !computeExpr.trim().isEmpty();
        boolean hasMapTo = mapToProp != null && !mapToProp.trim().isEmpty();

        if (MetaComputedFieldProjector.FIELD_KIND_STORAGE.equals(fieldKind)) {
            if (hasExpr || hasMapTo) {
                throw new NopMetadataException(NopMetadataErrors.ERR_FIELD_STORAGE_EXPR_FORBIDDEN)
                        .param(NopMetadataArgs.ARG_FIELD_NAME, field.getFieldName());
            }
            return;
        }

        if (MetaComputedFieldProjector.FIELD_KIND_COMPUTED.equals(fieldKind)) {
            if (!hasExpr) {
                throw new NopMetadataException(NopMetadataErrors.ERR_FIELD_COMPUTED_EXPR_REQUIRED)
                        .param(NopMetadataArgs.ARG_FIELD_NAME, field.getFieldName());
            }
            if (hasMapTo) {
                throw new NopMetadataException(NopMetadataErrors.ERR_FIELD_KIND_EXTRA_FORBIDDEN)
                        .param(NopMetadataArgs.ARG_FIELD_KIND, fieldKind)
                        .param(NopMetadataArgs.ARG_FIELD_NAME, field.getFieldName());
            }
            validateComputedExpr(field, computeExpr);
            return;
        }

        // alias：带 computeExpr 属 kind 互斥违反；无目标 = 目标不存在面（统一目标非法码）
        if (hasExpr) {
            throw new NopMetadataException(NopMetadataErrors.ERR_FIELD_KIND_EXTRA_FORBIDDEN)
                    .param(NopMetadataArgs.ARG_FIELD_KIND, fieldKind)
                    .param(NopMetadataArgs.ARG_FIELD_NAME, field.getFieldName());
        }
        validateAliasTarget(field, mapToProp);
    }

    /** save-time 校验 = 可解析 + 根标识符白名单 ⊆ 实体字段集（对齐 ExpressionMeasureValidator 白名单思路）。 */
    private void validateComputedExpr(NopMetaEntityField field, String computeExpr) {
        Expression expr;
        try {
            expr = MetaComputedFieldEvaluator.parse(computeExpr);
        } catch (Exception e) {
            throw new NopMetadataException(NopMetadataErrors.ERR_FIELD_COMPUTED_EXPR_UNPARSEABLE)
                    .param(NopMetadataArgs.ARG_FIELD_NAME, field.getFieldName());
        }
        Set<String> fieldNames = new HashSet<>();
        for (NopMetaEntityField sibling : loadSiblingFields(field)) {
            fieldNames.add(sibling.getFieldName());
        }
        for (String identifier : MetaComputedFieldEvaluator.collectRootIdentifiers(expr)) {
            if (!fieldNames.contains(identifier)) {
                throw new NopMetadataException(NopMetadataErrors.ERR_FIELD_COMPUTED_IDENTIFIER_UNKNOWN)
                        .param(NopMetadataArgs.ARG_FIELD_NAME, field.getFieldName())
                        .param(NopMetadataArgs.ARG_IDENTIFIER, identifier);
            }
        }
    }

    /** alias 目标必须为同实体的另一 storage 字段（不存在/自引用/非 storage 统一拒绝码）。 */
    private void validateAliasTarget(NopMetaEntityField field, String mapToProp) {
        if (mapToProp == null || mapToProp.equals(field.getFieldName())) {
            throw new NopMetadataException(NopMetadataErrors.ERR_FIELD_ALIAS_TARGET_UNKNOWN)
                    .param(NopMetadataArgs.ARG_FIELD_NAME, field.getFieldName())
                    .param(NopMetadataArgs.ARG_MAP_TO_PROP, mapToProp);
        }
        for (NopMetaEntityField sibling : loadSiblingFields(field)) {
            if (!sibling.getFieldName().equals(mapToProp)) {
                continue;
            }
            if (!MetaComputedFieldProjector.isStorageKind(sibling.getFieldKind())) {
                break;
            }
            return;
        }
        throw new NopMetadataException(NopMetadataErrors.ERR_FIELD_ALIAS_TARGET_UNKNOWN)
                .param(NopMetadataArgs.ARG_FIELD_NAME, field.getFieldName())
                .param(NopMetadataArgs.ARG_MAP_TO_PROP, mapToProp);
    }

    private Iterable<NopMetaEntityField> loadSiblingFields(NopMetaEntityField field) {
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaEntityField.PROP_NAME_metaEntityId, field.getMetaEntityId()));
        return dao().findAllByQuery(q);
    }

    private SearchableDoc toSearchableDoc(NopMetaEntityField entity) {
        return NopMetadataHelper.toSearchableDoc(entity);
    }
}
