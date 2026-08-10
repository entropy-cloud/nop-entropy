package io.nop.datav.service.chatbi;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.api.core.beans.LongRangeBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.sql.SQL;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dataset.IDataSet;
import io.nop.dataset.IDataSetMeta;
import io.nop.dataset.IDataRow;
import io.nop.datav.service.query.PanelSqlBuilder;
import io.nop.report.dao.entity.NopReportDataset;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static io.nop.datav.service.NopDatavConfigs.CFG_DATAV_CHATBI_MAX_ROWS;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_CHATBI_DATASET_NOT_FOUND;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_CHATBI_DATASET_NOT_SQL;
import static io.nop.datav.service.NopDatavErrors.ERR_DATAV_QUERY_FAILED;

/**
 * ChatBI 工具：按数据集 sid + 参数 Map 执行查询，返回 columns + rows。
 *
 * <p>对应工具定义 {@code datav-query-dataset.tool.xml}。核心查询语义（与
 * {@code ai-dev/design/nop-datav/ai-design.md} §4 数据流一致）：</p>
 *
 * <ul>
 *   <li>LLM 提供的 params Map <b>直接作为 {@link PanelSqlBuilder#build} 的 params 参数</b>，
 *       key 匹配 dsText 中的 {@code ${paramName}} 占位符，经 {@code ?} 参数化绑定（非字符串拼接）防注入。
 *       <b>不复用 {@code PanelParamEvaluator}</b>，因其依赖 DatasetRef.paramMapping（ChatBI 直接查
 *       NopReportDataset，无 DatasetRef 上下文）。</li>
 *   <li>maxRows 经 {@link LongRangeBean} 在数据集层限行（跨方言 dialect paging，防 OOM）。</li>
 * </ul>
 *
 * <p>数据集不存在 / 非 SQL 类型时返回显式错误 JSON（非 null/空静默返回，见 Minimum Rules #24）。</p>
 */
public class DatavQueryDatasetExecutor implements IToolExecutor {

    public static final String TOOL_NAME = "datav-query-dataset";
    public static final String DS_TYPE_SQL = "sql";

    private IDaoProvider daoProvider;
    private IJdbcTemplate jdbcTemplate;

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    @Inject
    public void setJdbcTemplate(IJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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

            String dsType = ds.getDsType();
            if (!DS_TYPE_SQL.equalsIgnoreCase(dsType)) {
                return FutureHelper.success(AiToolCallResult.errorResult(call.getId(),
                        "Dataset is not a SQL dataset (dsType=" + dsType + "): " + datasetSid
                                + " (errorCode=" + ERR_DATAV_CHATBI_DATASET_NOT_SQL.getErrorCode() + ")"));
            }

            Object paramsVal = input.get("params");
            Map<String, Object> params = toParamsMap(paramsVal);

            Integer maxRows = input.get("maxRows") instanceof Number
                    ? ((Number) input.get("maxRows")).intValue()
                    : CFG_DATAV_CHATBI_MAX_ROWS.get();

            QueryResult qr = doQuery(ds, params, maxRows, datasetSid);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("columns", qr.columns);
            result.put("rows", qr.rows);

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
    private Map<String, Object> toParamsMap(Object paramsVal) {
        if (paramsVal instanceof Map) {
            return (Map<String, Object>) paramsVal;
        }
        if (paramsVal instanceof String) {
            String s = ((String) paramsVal).trim();
            if (s.isEmpty() || "{}".equals(s)) {
                return Collections.emptyMap();
            }
            Object parsed = JsonTool.parseNonStrict(s);
            if (parsed instanceof Map) {
                return (Map<String, Object>) parsed;
            }
        }
        return Collections.emptyMap();
    }

    private QueryResult doQuery(NopReportDataset ds, Map<String, Object> params,
                                 Integer maxRows, String datasetSid) {
        String dsText = ds.getDsText();
        SQL sql = PanelSqlBuilder.build(dsText, params, datasetSid);

        try {
            return jdbcTemplate.executeQuery(sql,
                    maxRows != null && maxRows > 0 ? LongRangeBean.longRange(0, maxRows.longValue()) : null,
                    dset -> {
                        List<String> columns = extractColumnNames(dset.getMeta());
                        List<Map<String, Object>> rows = extractRows(dset);
                        return new QueryResult(columns, rows);
                    });
        } catch (NopException e) {
            throw e;
        } catch (Exception e) {
            throw new NopException(ERR_DATAV_QUERY_FAILED)
                    .param("panelId", datasetSid)
                    .cause(e);
        }
    }

    private static List<String> extractColumnNames(IDataSetMeta meta) {
        int count = meta.getFieldCount();
        if (count == 0) {
            return Collections.emptyList();
        }
        List<String> columns = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            columns.add(meta.getFieldName(i));
        }
        return Collections.unmodifiableList(columns);
    }

    private static List<Map<String, Object>> extractRows(IDataSet ds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        while (ds.hasNext()) {
            IDataRow row = ds.next();
            rows.add(row.toMap());
        }
        return Collections.unmodifiableList(rows);
    }

    private static class QueryResult {
        final List<String> columns;
        final List<Map<String, Object>> rows;

        QueryResult(List<String> columns, List<Map<String, Object>> rows) {
            this.columns = columns;
            this.rows = rows;
        }
    }
}
