package io.nop.datav.service.chatbi;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
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

/**
 * ChatBI 工具：列出可用数据集（仅 status=活跃 且当前用户可见）。
 *
 * <p>对应工具定义 {@code datav-list-datasets.tool.xml}。输入经 ChatBI tool-calling 路径以 JSON 字符串形式
 * 承载于 {@link AiToolCall#getInput()}（LLM arguments 的 JSON 序列化），executor 用 {@link JsonTool#parseMap}
 * 解析可选 {@code keyword} 过滤参数。</p>
 *
 * <p>可见性（P1-03 修复，plan 2026-08-15-2146-1 裁定 D4 选项 B）：admin 全量；非 admin 仅
 * {@code createdBy == 当前 operator}（身份经 {@link ChatBiToolExecuteContext} 传递）。list 侧为
 * 静默过滤语义（枚举不泄露他人数据集的存在性）。</p>
 *
 * <p>返回 JSON：{@code {"datasets": [{"sid","dsName","description","dsType"}, ...]}}。
 * 数据集不存在/无匹配均返回空数组（非 null），无静默跳过。</p>
 */
public class DatavListDatasetsExecutor implements IToolExecutor {

    public static final String TOOL_NAME = "datav-list-datasets";

    /**
     * status=1 对应 dict {@code core/active-status} 的"活跃"状态（与 TestNopDatavPanelDataE2E 的 setStatus(1) 一致）。
     */
    public static final int STATUS_ACTIVE = 1;

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
            String keyword = input.get("keyword") != null ? String.valueOf(input.get("keyword")) : null;

            // P1-03 裁定 D4 选项 B：数据集可见性（admin 全量；非 admin 仅 createdBy 匹配 operator）
            String operator = ChatBiDatasetVisibility.resolveOperator(context);
            boolean admin = ChatBiDatasetVisibility.resolveAdmin(context);

            List<NopReportDataset> datasets = findActiveDatasets(keyword, operator, admin);

            List<Map<String, Object>> datasetList = new ArrayList<>(datasets.size());
            for (NopReportDataset ds : datasets) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("sid", ds.getSid());
                item.put("dsName", ds.getDsName());
                item.put("description", ds.getDescription() != null ? ds.getDescription() : "");
                item.put("dsType", ds.getDsType());
                datasetList.add(item);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("datasets", datasetList);

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

    /**
     * 查询活跃数据集（P1-03 修复，裁定 D4 选项 B）。
     *
     * <p>可见性过滤与 status 过滤一同下推到 SQL：admin 不加 createdBy 条件（全量）；非 admin 追加
     * {@code createdBy = operator}（枚举不泄露他人数据集的存在性，list 侧为静默过滤语义）。
     * 无身份（operator 空且非 admin）fail-closed 返回空列表——nop-report 数据集无 DAO 层 RLS
     * （nop-report data-auth 为空），可见性由本 executor 显式实施。</p>
     */
    private List<NopReportDataset> findActiveDatasets(String keyword, String operator, boolean admin) {
        if (!ChatBiDatasetVisibility.hasIdentity(operator, admin)) {
            // fail-closed：无身份（直调无 context 且未设置 operator）时不可见任何数据集
            return new ArrayList<>();
        }
        IEntityDao<NopReportDataset> dao = daoProvider.daoFor(NopReportDataset.class);
        // status=1 与 createdBy 过滤下推到 SQL 层，避免加载非活跃/不可见数据集行（含 dsText/dsMeta VARCHAR(131072) 大字段）。
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(NopReportDataset.PROP_NAME_status, STATUS_ACTIVE));
        if (!admin) {
            query.addFilter(FilterBeans.eq(NopReportDataset.PROP_NAME_createdBy, operator));
        }
        @SuppressWarnings("unchecked")
        List<NopReportDataset> active = (List<NopReportDataset>) dao.findAllByQuery(query);
        if (keyword == null || keyword.isEmpty()) {
            return active;
        }
        // keyword 过滤在内存中对已过滤的活跃集执行（数据集通常数量有限，避免拼动态 LIKE 条件）
        String kw = keyword.toLowerCase();
        List<NopReportDataset> filtered = new ArrayList<>(active.size());
        for (NopReportDataset ds : active) {
            String dsName = ds.getDsName() != null ? ds.getDsName() : "";
            String desc = ds.getDescription() != null ? ds.getDescription() : "";
            if (dsName.toLowerCase().contains(kw) || desc.toLowerCase().contains(kw)) {
                filtered.add(ds);
            }
        }
        return filtered;
    }
}
