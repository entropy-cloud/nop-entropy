
package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataHelper;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.commons.util.CollectionHelper;
import io.nop.core.context.IServiceContext;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.biz.INopMetaEntityBiz;
import io.nop.metadata.biz.INopMetaEntityDimensionBiz;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaEntity;
import io.nop.metadata.dao.entity.NopMetaEntityDimension;
import io.nop.metadata.dao.entity.NopMetaEntityJoin;
import io.nop.metadata.service.field.MetaEntityFieldResolver;
import io.nop.metadata.service.NopMetadataException;
import jakarta.inject.Inject;

import java.util.Map;

/**
 * 表维度 BizModel（架构基线 §2.5.2 D1/D2/D3 / plan 0700-2 item 1.3 + plan 0228-3 跨表扩展）：
 * 基线 CRUD + save 字段引用校验。
 *
 * <p>save 校验（item 1.1 裁定的 save override 落点 + plan 0228-3 跨表范围扩展）：保存 Dimension 时校验
 * {@code entityFieldId} 字段引用属于该表可达字段集合（语义按 entityKind 重载：entity→NopMetaEntityField 主键，
 * 可达集合 = {@code baseEntity ∪ join 直连可达 rightEntity}，见 §2.5.2 D3；external/sql→字段名）。
 * 不合法显式失败抛 inline {@link ErrorCode}（不静默存入悬空引用）。Dimension 与 Measure 共享
 * {@code MetaEntityFieldResolver.validateFieldReference}，跨表扩展一并生效。
 *
 * <p>跳过校验的情形：{@code entityFieldId} 为 null。{@code dimensionType} 已由 dict
 * {@code meta/dimension-type} 校验；{@code granularity} 为自由 string（item 1.1 D1 裁定，文档约定值，
 * 不新增 dict 约束），不在此校验。
 */
@BizModel("NopMetaEntityDimension")
public class NopMetaEntityDimensionBizModel extends CrudBizModel<NopMetaEntityDimension>
        implements INopMetaEntityDimensionBiz {


    /** 跨表类型字段解析器（无状态）。 */
    private final MetaEntityFieldResolver fieldResolver = new MetaEntityFieldResolver();

    /** 跨聚合访问（plan 353 MD-1）：MetaEntity 读取经 Biz 接口而非 dao 直连。 */
    @Inject
    protected INopMetaEntityBiz tableBiz;

    public NopMetaEntityDimensionBizModel() {
        setEntityName(NopMetaEntityDimension.class.getName());
    }

    /**
     * save override（item 1.1 裁定的 save override 新模式 + plan 0228-3 跨表扩展）：持久化前校验
     * {@code entityFieldId} 字段引用，entity 表的引用可校验通过 NopMetaEntityJoin 直连可达的 rightEntity 字段
     * （跨表指标，架构基线 §2.5.2 D3）。
     *
     * <p>校验通过后委托 {@code super.save(...)} 走默认持久化逻辑。字段集合解析失败由解析器显式抛 ErrorCode。
     */
    @Override
    public NopMetaEntityDimension save(@Name("data") Map<String, Object> data, IServiceContext context) {
        // P2-19（plan 2026-08-16-0226-3）：null/empty data 提前委托基类，
        // 统一抛 ERR_BIZ_EMPTY_DATA_FOR_SAVE
        // （不 NPE 抢先——下方 stringOf 会先解引用 data）
        if (CollectionHelper.isEmptyMap(data)) {
            return super.save(data, context);
        }
        String metaEntityId = NopMetadataHelper.stringOf(data, NopMetaEntityDimension.PROP_NAME_metaEntityId);
        String entityFieldId = NopMetadataHelper.stringOf(data, NopMetaEntityDimension.PROP_NAME_entityFieldId);
        inheritBusinessDomain(data, metaEntityId, context);
        validateDimensionField(metaEntityId, entityFieldId, context);
        return super.save(data, context);
    }

    private void inheritBusinessDomain(Map<String, Object> data, String metaEntityId, IServiceContext context) {
        String businessDomainId = NopMetadataHelper.stringOf(data, NopMetaEntityDimension.PROP_NAME_businessDomainId);
        if ((businessDomainId == null || businessDomainId.isEmpty())
                && metaEntityId != null && !metaEntityId.isEmpty()) {
            NopMetaEntity table = tableBiz.get(metaEntityId, false, context);
            if (table != null && table.getBusinessDomainId() != null) {
                data.put(NopMetaEntityDimension.PROP_NAME_businessDomainId, table.getBusinessDomainId());
            }
        }
    }

    private void validateDimensionField(String metaEntityId, String entityFieldId, IServiceContext context) {
        if (metaEntityId == null || metaEntityId.isEmpty()) {
            return;
        }
        NopMetaEntity table = tableBiz.get(metaEntityId, false, context);
        if (table == null) {
            throw new NopMetadataException(NopMetadataErrors.ERR_DIMENSION_TABLE_NOT_FOUND).param("metaEntityId", metaEntityId);
        }
        // resolver 边界：MetaEntityFieldResolver API 消费 IEntityDao（resolver 包不在 MD-1 转换范围），保留 dao 直连（plan 353 MD-1 裁定）
        IEntityDao<NopMetaEntityField> fieldDao = daoFor(NopMetaEntityField.class);
        // joinDao 用于 entity 表跨表可达 rightEntityId 集合解析（§2.5.2 D3）+ external/sql name-based 可达列名集合并集（§2.5.2 D4）
        IEntityDao<NopMetaEntityJoin> joinDao = daoFor(NopMetaEntityJoin.class);
        // tableDao 用于 external/sql 表解析 table 端点 NopMetaEntity 列结构（§2.5.2 D4，同上 resolver 边界）
        IEntityDao<NopMetaEntity> tableDao = daoFor(NopMetaEntity.class);
        fieldResolver.validateFieldReference(table, entityFieldId, fieldDao, joinDao, tableDao,
                NopMetadataErrors.ERR_DIMENSION_FIELD_NOT_FOUND, "dimension");
    }

}
