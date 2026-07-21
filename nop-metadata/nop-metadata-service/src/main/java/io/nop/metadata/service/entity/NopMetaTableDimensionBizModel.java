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
import io.nop.metadata.biz.INopMetaTableDimensionBiz;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTableDimension;
import io.nop.metadata.dao.entity.NopMetaTableJoin;
import io.nop.metadata.service.field.MetaTableFieldResolver;

import java.util.Map;

/**
 * 表维度 BizModel（架构基线 §2.5.2 D1/D2/D3 / plan 0700-2 item 1.3 + plan 0228-3 跨表扩展）：
 * 基线 CRUD + save 字段引用校验。
 *
 * <p>save 校验（item 1.1 裁定的 save override 落点 + plan 0228-3 跨表范围扩展）：保存 Dimension 时校验
 * {@code entityFieldId} 字段引用属于该表可达字段集合（语义按 tableType 重载：entity→NopMetaEntityField 主键，
 * 可达集合 = {@code baseEntity ∪ join 直连可达 rightEntity}，见 §2.5.2 D3；external/sql→字段名）。
 * 不合法显式失败抛 inline {@link ErrorCode}（不静默存入悬空引用）。Dimension 与 Measure 共享
 * {@code MetaTableFieldResolver.validateFieldReference}，跨表扩展一并生效。
 *
 * <p>跳过校验的情形：{@code entityFieldId} 为 null。{@code dimensionType} 已由 dict
 * {@code meta/dimension-type} 校验；{@code granularity} 为自由 string（item 1.1 D1 裁定，文档约定值，
 * 不新增 dict 约束），不在此校验。
 */
@BizModel("NopMetaTableDimension")
public class NopMetaTableDimensionBizModel extends CrudBizModel<NopMetaTableDimension>
        implements INopMetaTableDimensionBiz {


    /** 跨表类型字段解析器（无状态）。 */
    private final MetaTableFieldResolver fieldResolver = new MetaTableFieldResolver();

    public NopMetaTableDimensionBizModel() {
        setEntityName(NopMetaTableDimension.class.getName());
    }

    /**
     * save override（item 1.1 裁定的 save override 新模式 + plan 0228-3 跨表扩展）：持久化前校验
     * {@code entityFieldId} 字段引用，entity 表的引用可校验通过 NopMetaTableJoin 直连可达的 rightEntity 字段
     * （跨表指标，架构基线 §2.5.2 D3）。
     *
     * <p>校验通过后委托 {@code super.save(...)} 走默认持久化逻辑。字段集合解析失败由解析器显式抛 ErrorCode。
     */
    @Override
    public NopMetaTableDimension save(@Name("data") Map<String, Object> data, IServiceContext context) {
        String metaTableId = stringOf(data, NopMetaTableDimension.PROP_NAME_metaTableId);
        String entityFieldId = stringOf(data, NopMetaTableDimension.PROP_NAME_entityFieldId);
        inheritBusinessDomain(data, metaTableId);
        validateDimensionField(metaTableId, entityFieldId);
        return super.save(data, context);
    }

    private void inheritBusinessDomain(Map<String, Object> data, String metaTableId) {
        String businessDomainId = stringOf(data, NopMetaTableDimension.PROP_NAME_businessDomainId);
        if ((businessDomainId == null || businessDomainId.isEmpty())
                && metaTableId != null && !metaTableId.isEmpty()) {
            IEntityDao<NopMetaTable> tableDao = daoFor(NopMetaTable.class);
            NopMetaTable table = tableDao.getEntityById(metaTableId);
            if (table != null && table.getBusinessDomainId() != null) {
                data.put(NopMetaTableDimension.PROP_NAME_businessDomainId, table.getBusinessDomainId());
            }
        }
    }

    private void validateDimensionField(String metaTableId, String entityFieldId) {
        if (metaTableId == null || metaTableId.isEmpty()) {
            return;
        }
        IEntityDao<NopMetaTable> tableDao = daoFor(NopMetaTable.class);
        NopMetaTable table = tableDao.getEntityById(metaTableId);
        if (table == null) {
            throw new NopException(NopMetadataErrors.ERR_DIMENSION_TABLE_NOT_FOUND).param("metaTableId", metaTableId);
        }
        IEntityDao<NopMetaEntityField> fieldDao = daoFor(NopMetaEntityField.class);
        // joinDao 用于 entity 表跨表可达 rightEntityId 集合解析（§2.5.2 D3）+ external/sql name-based 可达列名集合并集（§2.5.2 D4）
        IEntityDao<NopMetaTableJoin> joinDao = daoFor(NopMetaTableJoin.class);
        // tableDao（上面已加载目标表）亦用于 external/sql 表解析 table 端点 NopMetaTable 列结构（§2.5.2 D4）
        fieldResolver.validateFieldReference(table, entityFieldId, fieldDao, joinDao, tableDao,
                NopMetadataErrors.ERR_DIMENSION_FIELD_NOT_FOUND, "dimension");
    }

    private static String stringOf(Map<String, Object> data, String key) {
        Object v = data.get(key);
        return v == null ? null : v.toString();
    }
}
