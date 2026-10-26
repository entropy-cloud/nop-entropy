package io.nop.metadata.service.field;

import io.nop.metadata.dao.entity.NopMetaEntityField;
import io.nop.metadata.service.NopMetadataArgs;
import io.nop.metadata.service.NopMetadataErrors;
import io.nop.metadata.service.NopMetadataException;
import io.nop.xlang.api.ExprEvalAction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询输出投影解析（设计 14 §3.2，plan 2026-10-26-2047-1 Phase 2）：
 * computed 字段经 XLang 表达式在返回行上求值，alias 字段以别名列名出现；
 * 不下推 SQL，storage 字段行为零变化。
 *
 * <p>调用方在 queryData 取数后、返回 DTO 前调用；fields 传入该实体的目录字段行全集
 * （本类自行跳过 storage/缺省行）。computed 求值失败显式抛
 * {@code field-computed-eval-failed}，不静默置 null。
 */
public class MetaComputedFieldProjector {

    public static final String FIELD_KIND_STORAGE = "storage";
    public static final String FIELD_KIND_COMPUTED = "computed";
    public static final String FIELD_KIND_ALIAS = "alias";

    private MetaComputedFieldProjector() {
    }

    public static boolean isStorageKind(String fieldKind) {
        return fieldKind == null || FIELD_KIND_STORAGE.equals(fieldKind);
    }

    public static void project(List<NopMetaEntityField> fields, List<Map<String, Object>> items) {
        if (fields == null || fields.isEmpty() || items == null || items.isEmpty()) {
            return;
        }
        Map<String, ExprEvalAction> computedActions = null;
        Map<String, String> aliasTargets = null;
        for (NopMetaEntityField field : fields) {
            String kind = field.getFieldKind();
            if (isStorageKind(kind)) {
                continue;
            }
            if (FIELD_KIND_COMPUTED.equals(kind)) {
                if (computedActions == null) {
                    computedActions = new HashMap<>();
                }
                try {
                    computedActions.put(field.getFieldName(),
                            MetaComputedFieldEvaluator.compile(MetaComputedFieldEvaluator.parse(field.getComputeExpr())));
                } catch (Exception e) {
                    // 存量行中无法编译的表达式（如校验器水位变化）同样显式报错，不静默跳过该列
                    throw new NopMetadataException(NopMetadataErrors.ERR_FIELD_COMPUTED_EVAL_FAILED)
                            .param(NopMetadataArgs.ARG_FIELD_NAME, field.getFieldName())
                            .param(NopMetadataArgs.ARG_ERROR, e.getMessage());
                }
            } else if (FIELD_KIND_ALIAS.equals(kind)) {
                if (aliasTargets == null) {
                    aliasTargets = new HashMap<>();
                }
                aliasTargets.put(field.getFieldName(), field.getMapToProp());
            }
        }
        if ((computedActions == null || computedActions.isEmpty())
                && (aliasTargets == null || aliasTargets.isEmpty())) {
            return;
        }
        for (Map<String, Object> row : items) {
            if (aliasTargets != null) {
                for (Map.Entry<String, String> entry : aliasTargets.entrySet()) {
                    row.put(entry.getKey(), row.get(entry.getValue()));
                }
            }
            if (computedActions != null) {
                for (Map.Entry<String, ExprEvalAction> entry : computedActions.entrySet()) {
                    Object value;
                    try {
                        value = MetaComputedFieldEvaluator.evaluate(entry.getValue(), row);
                    } catch (Exception e) {
                        throw new NopMetadataException(NopMetadataErrors.ERR_FIELD_COMPUTED_EVAL_FAILED)
                                .param(NopMetadataArgs.ARG_FIELD_NAME, entry.getKey())
                                .param(NopMetadataArgs.ARG_ERROR, e.getMessage());
                    }
                    row.put(entry.getKey(), value);
                }
            }
        }
    }
}
