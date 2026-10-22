package io.nop.report.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.core.lang.sql.SQL;
import io.nop.core.resource.tpl.ITemplateOutput;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.ooxml.xlsx.util.ExcelSheetData;
import io.nop.report.core.engine.IReportEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI7：迭代式导出端到端（游标分页 → ExcelSheetData 迭代 → xlsx 落盘）。
 * 懒消费以「构造 renderer 不消费迭代器 + 全部拉取发生在 generateToFile 期间」断言验证；
 * 性能基线用 -Dnop.test.large-dataset=true 启用（Assumptions 控制，仓库 surefire 无 excludedGroups）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestLargeDatasetExport extends JunitAutoTestCase {

    static final int PAGE_SIZE = 10;

    @Inject
    IReportEngine reportEngine;

    @Inject
    IJdbcTemplate jdbcTemplate;

    int totalRows;
    List<String> events = new ArrayList<>();

    void initTable(int rows) {
        jdbcTemplate.executeUpdate(new SQL(
                "create table if not exists big_table(sid varchar(32), name varchar(100))"));
        jdbcTemplate.executeUpdate(new SQL("delete from big_table"));
        for (int i = 0; i < rows; i += 500) {
            StringBuilder sb = new StringBuilder("insert into big_table values ");
            for (int j = i; j < Math.min(i + 500, rows); j++) {
                if (j > i)
                    sb.append(',');
                sb.append("('").append(j).append("','n").append(j).append("')");
            }
            jdbcTemplate.executeUpdate(new SQL(sb.toString()));
        }
        totalRows = rows;
    }

    /** 懒分页迭代器：next 批次仅在需要时拉取 */
    Iterator<ExcelSheetData> pagedIterator() {
        return new Iterator<>() {
            int offset = 0;
            int index = 0;

            @Override
            public boolean hasNext() {
                return offset < totalRows;
            }

            @Override
            public ExcelSheetData next() {
                events.add("fetched:" + index);
                List<Map<String, Object>> rows = jdbcTemplate.findAll(new SQL(
                        "select sid, name from big_table limit " + PAGE_SIZE + " offset " + offset),
                        jdbcTemplate.getRowMapper(null, true));
                offset += PAGE_SIZE;

                ExcelSheetData data = new ExcelSheetData();
                data.setName("Page" + index);
                data.setHeaders(Arrays.asList("sid", "name"));
                data.setData(rows);
                index++;
                return data;
            }
        };
    }

    @Test
    public void testIterativeExportLazyConsumption() throws Exception {
        runIterativeExport(30);
    }

    /** 性能基线：-Dnop.test.large-dataset=true 时启用（宽松预算防回归锚点，基线值记 logs） */
    @Test
    public void testLargeExportBaseline() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("nop.test.large-dataset"));
        long begin = System.currentTimeMillis();
        runIterativeExport(10_000);
        long elapsed = System.currentTimeMillis() - begin;
        assertTrue(elapsed < 60_000, "baseline budget: " + elapsed + "ms");
        System.out.println("BASELINE iterative xlsx export (10k rows): " + elapsed + "ms");
    }

    void runIterativeExport(int rows) throws Exception {
        initTable(rows);

        ITemplateOutput output = reportEngine.getRendererForExcelData(pagedIterator(),
                io.nop.core.resource.VirtualFileSystem.instance()
                        .getResource(io.nop.ooxml.xlsx.XlsxConstants.SIMPLE_DATA_TEMPLATE_PATH));

        // 懒消费证据 1：构造 renderer 时不消费迭代器（非构造期全量物化）
        assertEquals(0, events.size(), "renderer construction must not consume the iterator");

        File out = new File("target/large-dataset-export.xlsx");
        long begin = System.currentTimeMillis();
        output.generateToFile(out, io.nop.xlang.api.XLang.newEvalScope());
        long elapsed = System.currentTimeMillis() - begin;

        assertTrue(out.exists());
        assertTrue(out.length() > 0);

        // 懒消费证据 2：全部拉取发生在 generateToFile 执行期间（逐 sheet 消费，ExcelTemplate.generateToDir 循环）
        int expectedPages = (rows + PAGE_SIZE - 1) / PAGE_SIZE;
        assertEquals(expectedPages, events.stream().filter(e -> e.startsWith("fetched:")).count());
        System.out.println("BASELINE iterative xlsx export (" + rows + " rows, " + expectedPages + " pages): "
                + elapsed + "ms");
    }
}
