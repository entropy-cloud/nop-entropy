
package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.metadata.biz.INopMetaEntityBiz;
import io.nop.metadata.biz.INopMetaEntityFieldBiz;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataHelper;
import io.nop.metadata.service.search.NopMetaSearchProcessor;
import io.nop.search.api.SearchableDoc;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@BizModel("NopMetaEntity")
public class NopMetaEntityBizModel extends CrudBizModel<NopMetaEntity> implements INopMetaEntityBiz {

    @Inject
    protected NopMetaSearchProcessor searchService;

    /** 跨聚合访问（plan 353 MD-1）：MetaEntityField 经 Biz 接口而非 dao 直连。 */
    @Inject
    protected INopMetaEntityFieldBiz entityFieldBiz;

    public NopMetaEntityBizModel() {
        setEntityName(NopMetaEntity.class.getName());
    }

    @Override
    public NopMetaEntity save(@Name("data") Map<String, Object> data, IServiceContext context) {
        NopMetaEntity saved = super.save(data, context);
        searchService.addToIndex("MetaEntity", saved.getMetaEntityId(), toSearchableDoc(saved));
        return saved;
    }

    @Override
    public boolean delete(@Name("id") String id, IServiceContext context) {
        NopMetaEntity before = requireEntity(id, "delete", context);
        // AR-08（plan 2026-08-06-0553-3 Phase 3）：删除前收集子实体（MetaEntityField）id，
        // 删除后一并 removeFromIndex——级联删除的字段索引残留（搜索返回已删实体）清理。
        List<String> fieldIds = collectEntityFieldIds(id, context);
        boolean deleted = super.delete(id, context);
        // check2 P2-06：主实体清理与子实体清理统一 best-effort（此前仅字段级有 safeRemoveFromIndex
        // 保护，主实体级 fail-closed 异常会回滚 DB 删除 → "实体留存、索引已删"分裂）
        safeRemoveFromIndex("MetaEntity", id);
        for (String fid : fieldIds) {
            safeRemoveFromIndex("MetaEntityField", fid);
        }
        return deleted;
    }

    /** 收集实体被级联删除的字段 id（NopMetaEntityField by metaEntityId，跨聚合经 Biz 接口，plan 353 MD-1）。 */
    private List<String> collectEntityFieldIds(String metaEntityId, IServiceContext context) {
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaEntityField.PROP_NAME_metaEntityId, metaEntityId));
        List<NopMetaEntityField> fields = entityFieldBiz.findList(q, null, context);
        List<String> ids = new ArrayList<>(fields.size());
        for (NopMetaEntityField f : fields) {
            ids.add(f.getEntityFieldId());
        }
        return ids;
    }

    private void safeRemoveFromIndex(String entityType, String id) {
        try {
            searchService.removeFromIndex(entityType, id);
        } catch (RuntimeException e) {
            // 索引清理为 best-effort（fail-closed 可抛）：失败留 WARN，不掩盖删除结果
            org.slf4j.LoggerFactory.getLogger(NopMetaEntityBizModel.class)
                    .warn("delete index cleanup failed for entityType={} id={}, errorCode={}",
                            entityType, id, NopMetadataErrors.ERR_ENTITY_SYNC_ISOLATED.getErrorCode(), e);
        }
    }

    private SearchableDoc toSearchableDoc(NopMetaEntity entity) {
        return NopMetadataHelper.toSearchableDoc(entity);
    }
}