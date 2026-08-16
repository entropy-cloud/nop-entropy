package io.nop.datav.service.entity;

import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.api.core.annotations.autotest.NopTestProperty;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.datav.service.chatbi.ChatBiToolExecuteContext;
import io.nop.datav.service.chatbi.DatavQueryDatasetExecutor;
import io.nop.report.dao.entity.NopReportDataset;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P1-04 回归测试（plan 2026-08-15-2146-2 Phase 4）：ChatBI maxRows 服务端硬钳制。
 *
 * <p>缺陷机制：修复前 maxRows 由 LLM 入参直取（{@code CFG_DATAV_CHATBI_MAX_ROWS} 默认 1000 只是
 * 缺省值而非上限），且 {@code maxRows > 0} 才限行——传 0/负数时 range=null 全表物化；巨值直取无
 * 上界。schemaJson 无 maximum（LLM 可任意放大）。</p>
 *
 * <p><b>修复后钳制语义</b>：入参钳制到 {@code [1, CFG_DATAV_CHATBI_MAX_ROWS]}——null/<=0 落配置
 * 缺省，&gt;0 取 min(入参, 配置)。本类经 {@code @NopTestProperty} 把配置压到 3，数据集 5 行，
 * 断言三态（0/负数/巨值）与边界值（=上限放行、低于上限按入参）。</p>
 *
 * <p>mutate-fail：若回退「入参直取」，maxRows=1000000 返回 5 行（>3）→ 巨值断言失败；
 * 若回退「maxRows>0 才限行」，maxRows=0 返回 5 行 → 零值断言失败。</p>
 */
@NopTestProperty(name = "nop.datav.chatbi.max-rows", value = "3")
public class TestDatavQueryDatasetMaxRowsClamp extends AbstractNopDatavTest {

    private static final int CONFIGURED_CAP = 3;

    @Inject
    IDaoProvider daoProvider;

    private void seedFiveRowDataset() {
        try {
            jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin().name("drop:TEST_DATAV_P104")
                    .sql("drop table TEST_DATAV_P104").end());
        } catch (Exception ignored) {
            // 表不存在则忽略
        }
        jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin().name("create:TEST_DATAV_P104")
                .sql("create table TEST_DATAV_P104(ID int)").end());
        for (int i = 1; i <= 5; i++) {
            jdbcTemplate.executeUpdate(io.nop.core.lang.sql.SQL.begin().name("insert:TEST_DATAV_P104")
                    .sql("insert into TEST_DATAV_P104(ID) values(").param(i).sql(")").end());
        }

        NopReportDataset ds = new NopReportDataset();
        ds.setSid("ds-p104-clamp");
        ds.setDsName("ds-p104-clamp");
        ds.setIsSingleRow(false);
        ds.setDsType("sql");
        ds.setDsText("select ID as id from TEST_DATAV_P104 order by ID");
        ds.setDsMeta("{}");
        ds.setStatus(1);
        ds.setVersion(0);
        ds.setCreatedBy("test");
        ds.setCreateTime(new Timestamp(System.currentTimeMillis()));
        ds.setUpdatedBy("test");
        ds.setUpdateTime(new Timestamp(System.currentTimeMillis()));
        daoProvider.daoFor(NopReportDataset.class).saveEntityDirectly(ds);
    }

    private List<Map<String, Object>> queryWithMaxRows(Object maxRows) {
        DatavQueryDatasetExecutor executor = new DatavQueryDatasetExecutor();
        executor.setDaoProvider(daoProvider);
        executor.setJdbcTemplate(jdbcTemplate);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("datasetSid", "ds-p104-clamp");
        if (maxRows != null) {
            input.put("maxRows", maxRows);
        }
        AiToolCall call = new AiToolCall();
        call.setToolName(DatavQueryDatasetExecutor.TOOL_NAME);
        call.setInput(JsonTool.stringify(input));

        AiToolCallResult result = executor.executeAsync(call,
                new ChatBiToolExecuteContext(null, "test", false)).toCompletableFuture().join();
        assertEquals("success", result.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) JsonTool.parseNonStrict(result.getOutput().getBody());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) parsed.get("rows");
        return rows;
    }

    /**
     * 三态钳制（plan Exit Criteria：0/负数/巨值）+ 缺省入参：
     * 0 → 配置值；-1 → 配置值；1000000 → 配置值；缺失（null）→ 配置值。
     */
    @Test
    public void testMaxRowsZeroNegativeHugeAndMissingAllClampToConfig() {
        seedFiveRowDataset();
        assertEquals(CONFIGURED_CAP, queryWithMaxRows(0).size(),
                "maxRows=0 falls back to config cap (no unbounded materialization)");
        assertEquals(CONFIGURED_CAP, queryWithMaxRows(-1).size(),
                "negative maxRows falls back to config cap");
        assertEquals(CONFIGURED_CAP, queryWithMaxRows(1000000).size(),
                "huge maxRows clamped down to config cap (server-side hard limit)");
        assertEquals(CONFIGURED_CAP, queryWithMaxRows(null).size(),
                "missing maxRows falls back to config cap");
    }

    /**
     * 边界值：=上限放行（不误伤）；低于上限按入参（min 语义保留合法小值）。
     */
    @Test
    public void testMaxRowsBoundaryAndLowerValueRespected() {
        seedFiveRowDataset();
        assertEquals(CONFIGURED_CAP, queryWithMaxRows(CONFIGURED_CAP).size(),
                "maxRows == cap passes (boundary allowed)");
        assertEquals(2, queryWithMaxRows(2).size(),
                "maxRows below cap respected as-is (min semantics)");
    }
}
