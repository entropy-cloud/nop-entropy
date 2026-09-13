package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.time.CoreMetrics;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.json.JsonTool;
import io.nop.metadata.biz.INopMetaDataProductBiz;
import io.nop.metadata.biz.INopMetaTagLabelBiz;
import io.nop.metadata.dao.entity.NopMetaDataProduct;
import io.nop.metadata.dao.entity.NopMetaTagLabel;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.metadata.service.NopMetadataErrors.ARG_DATA_PRODUCT_ID;
import static io.nop.metadata.service.NopMetadataErrors.ARG_ENTITY_ID;
import static io.nop.metadata.service.NopMetadataErrors.ARG_ENTITY_TYPE;

@BizModel("NopMetaDataProduct")
public class NopMetaDataProductBizModel extends CrudBizModel<NopMetaDataProduct> implements INopMetaDataProductBiz{

    private static final Set<String> LINKABLE_ASSET_TYPES = Set.of(
            "NopMetaTable", "NopMetaEntity", "NopMetaEntityField",
            "NopMetaTableMeasure", "NopMetaTableDimension"
    );

    /** 跨聚合访问（plan 353 MD-1）：TagLabel 经 Biz 接口而非 dao 直连。 */
    @jakarta.inject.Inject
    protected INopMetaTagLabelBiz tagLabelBiz;

    public NopMetaDataProductBizModel(){
        setEntityName(NopMetaDataProduct.class.getName());
    }

    @BizMutation
    public NopMetaTagLabel linkAsset(@Name("dataProductId") String dataProductId,
                                     @Name("entityType") String entityType,
                                     @Name("entityId") String entityId,
                                     IServiceContext context) {
        // P1-4（plan 2026-08-15-1913-2）：聚合根存在性校验（沿 executeReconciliation 的 requireEntity 惯例）——
        // 不存在/伪造的 dataProductId 在入口显式抛 ErrorCode 异常（聚合根层），与标签层的
        // ERR_LINK_ASSET_NOT_FOUND（unlink 路径）语义区分；不再让任意 dataProductId 成功创建 linkage 行。
        requireEntity(dataProductId, "linkAsset", context);

        if (!LINKABLE_ASSET_TYPES.contains(entityType)) {
            throw new NopMetadataException(NopMetadataErrors.ERR_LINK_ASSET_ENTITY_TYPE_INVALID)
                    .param(ARG_ENTITY_TYPE, entityType);
        }
        // P1-4 裁定：entityId 不做按 entityType 分派的存在性校验——TagLabel 是通用标注表
        // （suggestTags/propagateTags 同样以 (entityType, entityId) 引用资产），资产引用完整性
        // 属标注域通用语义；linkAsset 侧由 LINKABLE_ASSET_TYPES 白名单治理 entityType，
        // entityId 保持不透明引用（与既有模块行为一致，裁定记录 daily log）。
        String metadata = JsonTool.stringify(
                Map.of("dataProductId", dataProductId));
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityType, entityType));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityId, entityId));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_labelType, "Automated"));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_metadata, metadata));
        List<NopMetaTagLabel> existing = tagLabelBiz.findList(q, null, context);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        // P1-5：跨聚合创建改走 TagLabel 属主 save 管线（沿 NopMetaGlossaryTermBizModel#syncTagLabels 的
        // bizObject invoke("save") 先例，签名已核实匹配）——Automated 标签经
        // NopMetaTagLabelBizModel.save → triggerApprovalIfNeeded 获得 state=Suggested + 自动提审
        // （xmeta wf:wfName=tagLabelConfirmApproval，fail-loud），与 GlossaryTerm 传播路径同构；
        // 不再绕过属主管线手工 setState 直写 DAO（旧路径 Automated 标签永不进审批流，行为分裂）。
        String userId = context == null || context.getUserId() == null ? "system" : context.getUserId();
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("source", "Classification");
        data.put("labelType", "Automated");
        data.put("state", "Suggested");
        data.put("entityType", entityType);
        data.put("entityId", entityId);
        data.put("metadata", metadata);
        data.put("reason", "linked from DataProduct " + dataProductId);
        data.put("appliedAt", CoreMetrics.currentTimestamp());
        data.put("appliedBy", userId);
        NopMetaTagLabel saved = (NopMetaTagLabel) bizObjectManager().getBizObject("NopMetaTagLabel")
                .invoke("save", Map.of("data", data), null, context);
        return saved;
    }

    @BizMutation
    public boolean unlinkAsset(@Name("dataProductId") String dataProductId,
                               @Name("entityType") String entityType,
                               @Name("entityId") String entityId,
                               IServiceContext context) {
        // P1-4：先校验聚合根——不存在 dataProductId 的错误语义是"产品不存在"（requireEntity 的
        // entity-not-found），而非"标签不存在"（ERR_LINK_ASSET_NOT_FOUND）；对"存在产品但无标签"
        // 场景错误码保持不变。
        requireEntity(dataProductId, "unlinkAsset", context);

        String metadata = JsonTool.stringify(
                Map.of("dataProductId", dataProductId));
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityType, entityType));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityId, entityId));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_labelType, "Automated"));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_metadata, metadata));
        List<NopMetaTagLabel> labels = tagLabelBiz.findList(q, null, context);
        if (labels.isEmpty()) {
            throw new NopMetadataException(NopMetadataErrors.ERR_LINK_ASSET_NOT_FOUND)
                    .param(ARG_DATA_PRODUCT_ID, dataProductId)
                    .param(ARG_ENTITY_TYPE, entityType)
                    .param(ARG_ENTITY_ID, entityId);
        }
        for (NopMetaTagLabel label : labels) {
            tagLabelBiz.deleteEntity(label, null, context);
        }
        return true;
    }

    @BizQuery
    public List<NopMetaTagLabel> getLinkedAssets(@Name("dataProductId") String dataProductId,
                                                  IServiceContext context) {
        String metadata = JsonTool.stringify(
                Map.of("dataProductId", dataProductId));
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_labelType, "Automated"));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_metadata, metadata));
        return tagLabelBiz.findList(q, null, context);
    }
}
