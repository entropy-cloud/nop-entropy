package io.nop.datav.service.chatbi;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.report.dao.entity.NopReportDataset;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_CHATBI_DATASET_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_CHATBI_DATASET_NO_ACCESS;

/**
 * ChatBI 工具：描述指定数据集的字段元数据 + 输入参数定义。
 *
 * <p>对应工具定义 {@code datav-describe-dataset.tool.xml}。输入经 ChatBI tool-calling 路径以 JSON 字符串
 * 承载于 {@link AiToolCall#getInput()}，executor 解析必填 {@code datasetSid}。</p>
 *
 * <p>从 {@link NopReportDataset#getDsMeta()} 解析字段元数据、从 {@link NopReportDataset#getDsConfig()} 解析
 * 输入参数定义。两者均为 JSON 文本（orm.xml 中 stdDomain="json"），解析失败时返回空数组（非静默 null）。</p>
 *
 * <p>数据集不存在 / 不可见（P1-03 裁定 D4：非 owner 且非 admin）时返回显式错误 JSON
 * {@code {"status":"error","errorCode":"...","datasetSid":"..."}}
 * （非 null/空静默返回，见 Minimum Rules #24）。</p>
 */
public class DatavDescribeDatasetExecutor implements IToolExecutor {

    public static final String TOOL_NAME = "datav-describe-dataset";

    private IDaoProvider daoProvider;

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public CompletionStage<AiToolCallResult> executeAsync(AiToolCall call, IToolExecuteContext context) {
        try {
            Map<String, Object> input = parseInput(call);
            String datasetSid = input.get("datasetSid") != null ? String.valueOf(input.get("datasetSid")) : null;

            if (datasetSid == null || datasetSid.isEmpty()) {
                return FutureHelper.success(AiToolCallResult.errorResult(call.getId(),
                        "datasetSid is required"));
            }

            IEntityDao<NopReportDataset> dao = daoProvider.daoFor(NopReportDataset.class);
            NopReportDataset ds = dao.getEntityById(datasetSid);
            if (ds == null) {
                return FutureHelper.success(AiToolCallResult.errorResult(call.getId(),
                        "Dataset not found: " + datasetSid
                                + " (errorCode=" + ERR_DATAV_CHATBI_DATASET_NOT_FOUND.getErrorCode() + ")"));
            }

            // P1-03 修复（裁定 D4 选项 B，与 query executor 一致）：describe 暴露数据集 schema/参数定义，
            // 同属数据集可达性面——admin 全量；非 admin 仅 createdBy 匹配当前 operator，不可达显式拒绝。
            String operator = ChatBiDatasetVisibility.resolveOperator(context);
            boolean admin = ChatBiDatasetVisibility.resolveAdmin(context);
            if (!ChatBiDatasetVisibility.isVisible(ds, operator, admin)) {
                return FutureHelper.success(AiToolCallResult.errorResult(call.getId(),
                        "Dataset is not visible to the current user: " + datasetSid
                                + " (errorCode=" + ERR_DATAV_CHATBI_DATASET_NO_ACCESS.getErrorCode()
                                + ", userName=" + (operator != null ? operator : "<null>") + ")"));
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sid", ds.getSid());
            result.put("dsName", ds.getDsName());
            result.put("description", ds.getDescription() != null ? ds.getDescription() : "");
            result.put("dsType", ds.getDsType());
            result.put("fields", parseFields(ds.getDsMeta()));
            result.put("params", parseParams(ds.getDsConfig()));

            String json = JsonTool.stringify(result);
            return FutureHelper.success(AiToolCallResult.successResult(call.getId(), json));
        } catch (Exception e) {
            return FutureHelper.success(AiToolCallResult.errorResult(call.getId(), e.toString()));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseInput(AiToolCall call) {
        String inputText = call.getInput();
        if (inputText == null || inputText.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Object parsed = JsonTool.parseNonStrict(inputText);
        if (parsed instanceof Map) {
            return (Map<String, Object>) parsed;
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseFields(String dsMeta) {
        List<Map<String, Object>> fields = new ArrayList<>();
        if (dsMeta == null || dsMeta.isEmpty() || "{}".equals(dsMeta.trim())) {
            return fields;
        }
        try {
            Object parsed = JsonTool.parseNonStrict(dsMeta);
            if (parsed instanceof Map) {
                Map<String, Object> meta = (Map<String, Object>) parsed;
                Object fieldsVal = meta.get("fields");
                if (fieldsVal instanceof List) {
                    for (Object f : (List<?>) fieldsVal) {
                        if (f instanceof Map) {
                            fields.add(new LinkedHashMap<>((Map<String, Object>) f));
                        }
                    }
                } else if (fieldsVal == null) {
                    for (Map.Entry<String, Object> e : meta.entrySet()) {
                        if ("columns".equalsIgnoreCase(e.getKey()) && e.getValue() instanceof List) {
                            for (Object f : (List<?>) e.getValue()) {
                                if (f instanceof Map) {
                                    fields.add(new LinkedHashMap<>((Map<String, Object>) f));
                                }
                            }
                        }
                    }
                }
            } else if (parsed instanceof List) {
                for (Object f : (List<?>) parsed) {
                    if (f instanceof Map) {
                        fields.add(new LinkedHashMap<>((Map<String, Object>) f));
                    }
                }
            }
        } catch (Exception ignore) {
            // dsMeta 解析失败返回空字段清单（非静默 null），executor 不抛
        }
        return fields;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseParams(String dsConfig) {
        List<Map<String, Object>> params = new ArrayList<>();
        if (dsConfig == null || dsConfig.isEmpty() || "{}".equals(dsConfig.trim())) {
            return params;
        }
        try {
            Object parsed = JsonTool.parseNonStrict(dsConfig);
            if (parsed instanceof Map) {
                Map<String, Object> config = (Map<String, Object>) parsed;
                Object paramsVal = config.get("params");
                if (paramsVal instanceof List) {
                    for (Object p : (List<?>) paramsVal) {
                        if (p instanceof Map) {
                            params.add(new LinkedHashMap<>((Map<String, Object>) p));
                        }
                    }
                }
            }
        } catch (Exception ignore) {
            // dsConfig 解析失败返回空参数清单
        }
        return params;
    }
}
