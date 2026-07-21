package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.biz.INopMetaDataProductBiz;
import io.nop.metadata.dao.entity.NopMetaDataProduct;
import io.nop.metadata.dao.entity.NopMetaTagLabel;
import io.nop.metadata.service.NopMetadataErrors;

import java.sql.Timestamp;
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

    public NopMetaDataProductBizModel(){
        setEntityName(NopMetaDataProduct.class.getName());
    }

    @BizMutation
    public NopMetaTagLabel linkAsset(@Name("dataProductId") String dataProductId,
                                     @Name("entityType") String entityType,
                                     @Name("entityId") String entityId,
                                     IServiceContext context) {
        if (!LINKABLE_ASSET_TYPES.contains(entityType)) {
            throw new NopException(NopMetadataErrors.ERR_LINK_ASSET_ENTITY_TYPE_INVALID)
                    .param(ARG_ENTITY_TYPE, entityType);
        }
        IEntityDao<NopMetaTagLabel> labelDao = daoFor(NopMetaTagLabel.class);
        String metadata = "{\"dataProductId\":\"" + dataProductId + "\"}";
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityType, entityType));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityId, entityId));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_labelType, "Automated"));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_metadata, metadata));
        List<NopMetaTagLabel> existing = labelDao.findAllByQuery(q);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        NopMetaTagLabel label = labelDao.newEntity();
        label.setSource("Classification");
        label.setLabelType("Automated");
        label.setState("Suggested");
        label.setEntityType(entityType);
        label.setEntityId(entityId);
        label.setMetadata(metadata);
        label.setReason("linked from DataProduct " + dataProductId);
        Timestamp now = new Timestamp(CoreMetrics.currentTimeMillis());
        label.setAppliedAt(now);
        label.setAppliedBy(context == null ? "system" : context.getUserId());
        label.setVersion(1L);
        label.setCreatedBy("system");
        label.setUpdatedBy("system");
        label.setCreateTime(now);
        label.setUpdateTime(now);
        labelDao.saveEntity(label);
        return label;
    }

    @BizMutation
    public boolean unlinkAsset(@Name("dataProductId") String dataProductId,
                               @Name("entityType") String entityType,
                               @Name("entityId") String entityId,
                               IServiceContext context) {
        String metadata = "{\"dataProductId\":\"" + dataProductId + "\"}";
        IEntityDao<NopMetaTagLabel> labelDao = daoFor(NopMetaTagLabel.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityType, entityType));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_entityId, entityId));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_labelType, "Automated"));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_metadata, metadata));
        List<NopMetaTagLabel> labels = labelDao.findAllByQuery(q);
        if (labels.isEmpty()) {
            throw new NopException(NopMetadataErrors.ERR_LINK_ASSET_NOT_FOUND)
                    .param(ARG_DATA_PRODUCT_ID, dataProductId)
                    .param(ARG_ENTITY_TYPE, entityType)
                    .param(ARG_ENTITY_ID, entityId);
        }
        for (NopMetaTagLabel label : labels) {
            labelDao.deleteEntity(label);
        }
        return true;
    }

    @BizQuery
    public List<NopMetaTagLabel> getLinkedAssets(@Name("dataProductId") String dataProductId,
                                                  IServiceContext context) {
        String metadata = "{\"dataProductId\":\"" + dataProductId + "\"}";
        IEntityDao<NopMetaTagLabel> labelDao = daoFor(NopMetaTagLabel.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_labelType, "Automated"));
        q.addFilter(FilterBeans.eq(NopMetaTagLabel.PROP_NAME_metadata, metadata));
        return labelDao.findAllByQuery(q);
    }
}
