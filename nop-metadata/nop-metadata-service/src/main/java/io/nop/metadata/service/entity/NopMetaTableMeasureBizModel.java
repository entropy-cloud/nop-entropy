package io.nop.metadata.service.entity;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.biz.crud.CrudBizModel;
import io.nop.core.context.IServiceContext;
import io.nop.dao.api.IEntityDao;
import io.nop.metadata.biz.INopMetaTableMeasureBiz;
import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.dao.entity.NopMetaTable;
import io.nop.metadata.dao.entity.NopMetaTableJoin;
import io.nop.metadata.dao.entity.NopMetaTableMeasure;
import io.nop.metadata.service.field.ExpressionMeasureValidator;
import io.nop.metadata.service.field.MetaTableFieldResolver;

import java.util.Map;

/**
 * 表指标 BizModel（架构基线 §2.5.2 D2/D3 / plan 0700-2 item 1.3 + plan 0228-3 跨表扩展）：
 * 基线 CRUD + save 字段引用校验。
 *
 * <p>save 校验（item 1.1 裁定的 save override 落点 + plan 0228-3 跨表范围扩展）：保存 Measure 时校验
 * {@code entityFieldId} 字段引用属于该表可达字段集合（语义按 tableType 重载：entity→NopMetaEntityField 主键，
 * 可达集合 = {@code baseEntity ∪ join 直连可达 rightEntity}，见 §2.5.2 D3；external/sql→字段名）。
 * 不合法显式失败抛 inline {@link ErrorCode}（不静默存入悬空引用）。
 *
 * <p><b>expression 型 save-time 校验（§4.4.2 D12.5，plan 2026-07-18-1400-1 落地）</b>：当 {@code entityFieldId}
 * 为空且 {@code expression} 非空时，调 {@link ExpressionMeasureValidator#validateStatic} 做 save-time 宽松校验
 * （关键字黑名单 + parse 结构 + 容量；标识符宽松接受裸名 / {@code l.}/{@code r.} 前缀，不校验列存在性 / 端点归属——
 * 这些延迟到 query-time loader，它知道运行时上下文：单表 vs JOIN）。
 *
 * <p>跳过校验的情形：{@code entityFieldId} 为 null 且 {@code expression} 为空（数据错误由 mandatory 校验抛错）。
 * {@code aggFunc} 已由 dict {@code meta/agg-func} 校验，不重复。
 */
@BizModel("NopMetaTableMeasure")
public class NopMetaTableMeasureBizModel extends CrudBizModel<NopMetaTableMeasure> implements INopMetaTableMeasureBiz {

    static final ErrorCode ERR_MEASURE_TABLE_NOT_FOUND =
            ErrorCode.define("metadata.measure-table-not-found",
                    "MetaTable not found for measure save: {metaTableId}", "metaTableId");
    static final ErrorCode ERR_MEASURE_FIELD_NOT_FOUND =
            ErrorCode.define("metadata.measure-field-not-found",
                    "Measure field reference does not belong to the table's reachable fields/entities: "
                            + "{metaTableId} entityFieldId={entityFieldId} ({refKind}); "
                            + "availableFields={availableFields} allowedEntityIds={allowedEntityIds}",
                    "metaTableId", "entityFieldId", "refKind", "availableFields", "allowedEntityIds");

    /** 跨表类型字段解析器（无状态，与 NopMetaTableBizModel 共用同一解析逻辑）。 */
    private final MetaTableFieldResolver fieldResolver = new MetaTableFieldResolver();

    public NopMetaTableMeasureBizModel() {
        setEntityName(NopMetaTableMeasure.class.getName());
    }

    /**
     * save override（item 1.1 裁定的 save override 新模式 + plan 0228-3 跨表扩展 + plan 2026-07-18-1400-1 expression save-time）：
     * 持久化前校验 {@code entityFieldId} 字段引用（entity 表可校验通过 NopMetaTableJoin 直连可达的 rightEntity 字段，
     * 跨表指标，架构基线 §2.5.2 D3）；以及 expression 文本（当 entityFieldId 为空且 expression 非空时）。
     *
     * <p>校验通过后委托 {@code super.save(...)} 走默认持久化逻辑（不破坏既有 CRUD 契约）。
     * 字段集合解析失败（baseEntityId null / buildSql 损坏 / sourceSql 不可解析）由解析器显式抛 ErrorCode。
     */
    @Override
    public NopMetaTableMeasure save(@Name("data") Map<String, Object> data, IServiceContext context) {
        String metaTableId = stringOf(data, NopMetaTableMeasure.PROP_NAME_metaTableId);
        String entityFieldId = stringOf(data, NopMetaTableMeasure.PROP_NAME_entityFieldId);
        String measureName = stringOf(data, NopMetaTableMeasure.PROP_NAME_measureName);
        String expression = stringOf(data, NopMetaTableMeasure.PROP_NAME_expression);
        validateMeasureField(metaTableId, entityFieldId, measureName, expression);
        return super.save(data, context);
    }

    /**
     * 加载目标表 + 校验 entityFieldId 字段引用（entityFieldId 为空时跳过；用于 expression 型指标——
     * 此处接入 D12.5 expression save-time 校验）。
     *
     * @param measureName 错误上下文（expression 校验需要）
     * @param expression  expression 文本（entityFieldId 为空 + expression 非空时触发 D12.5 校验）
     */
    private void validateMeasureField(String metaTableId, String entityFieldId, String measureName, String expression) {
        if (metaTableId == null || metaTableId.isEmpty()) {
            // metaTableId 为 mandatory 列，框架会在 super.save 做必填校验；此处不重复报错
            return;
        }
        // D12.5 expression save-time 校验（entityFieldId 为空 + expression 非空 → expression 型指标）
        if ((entityFieldId == null || entityFieldId.isEmpty())
                && expression != null && !expression.trim().isEmpty()) {
            ExpressionMeasureValidator.validateStatic(expression,
                    ExpressionMeasureValidator.ValidationOptions.saveTimeLoose(),
                    metaTableId, measureName == null ? "<unknown>" : measureName);
        }
        IEntityDao<NopMetaTable> tableDao = daoFor(NopMetaTable.class);
        NopMetaTable table = tableDao.getEntityById(metaTableId);
        if (table == null) {
            throw new NopException(ERR_MEASURE_TABLE_NOT_FOUND).param("metaTableId", metaTableId);
        }
        IEntityDao<NopMetaEntityField> fieldDao = daoFor(NopMetaEntityField.class);
        // joinDao 用于 entity 表跨表可达 rightEntityId 集合解析（§2.5.2 D3）+ external/sql name-based 可达列名集合并集（§2.5.2 D4）
        IEntityDao<NopMetaTableJoin> joinDao = daoFor(NopMetaTableJoin.class);
        // tableDao（上面已加载目标表）亦用于 external/sql 表解析 table 端点 NopMetaTable 列结构（§2.5.2 D4）
        // entityFieldId 为 null（expression 型）时 validateFieldReference 内部跳过校验
        fieldResolver.validateFieldReference(table, entityFieldId, fieldDao, joinDao, tableDao,
                ERR_MEASURE_FIELD_NOT_FOUND, "measure");
    }

    private static String stringOf(Map<String, Object> data, String key) {
        Object v = data.get(key);
        return v == null ? null : v.toString();
    }
}
