package io.nop.batch.exp;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.batch.exp.config.ExportDbConfig;
import io.nop.batch.exp.config.JdbcConnectionConfig;
import io.nop.core.lang.sql.SQL;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;
import io.nop.dao.DaoConfigs;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dataset.record.IRecordInput;
import io.nop.report.core.record.ExcelResourceIO;
import io.nop.xlang.xdsl.DslModelHelper;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@NopTestConfig(localDb = true)
public class TestExportDbTool extends JunitBaseTestCase {

    @Inject
    IJdbcTemplate jdbcTemplate;

    @Test
    public void testExportXlsx() throws IOException {
        initData();
        insertData();

        ExportDbTool tool = new ExportDbTool();

        IResource resource = classpathResource("config/test.export-db-xlsx.xml");
        ExportDbConfig config = (ExportDbConfig) DslModelHelper.loadDslModel(resource);

        JdbcConnectionConfig conn = new JdbcConnectionConfig();
        conn.setDriverClassName(DaoConfigs.CFG_DATASOURCE_DRIVER_CLASS_NAME.get());
        conn.setJdbcUrl(DaoConfigs.CFG_DATASOURCE_JDBC_URL.get());
        conn.setUsername(DaoConfigs.CFG_DATASOURCE_USERNAME.get());
        conn.setPassword(DaoConfigs.CFG_DATASOURCE_PASSWORD.get());
        config.setJdbcConnection(conn);

        File outputDir = new File(getTargetDir(), "export-xlsx-output");
        outputDir.mkdirs();
        config.setOutputDir(outputDir.getAbsolutePath());

        tool.setConfig(config);
        tool.execute();

        File xlsxFile = new File(outputDir, "test_export.xlsx");
        assertTrue(xlsxFile.exists(), "xlsx file should exist");

        ExcelResourceIO<Map<String, Object>> excelIO = new ExcelResourceIO<>();
        excelIO.setHeaders(List.of("ID", "NAME", "VAL"));

        IRecordInput<Map<String, Object>> input = excelIO.openInput(new FileResource(xlsxFile), null);
        input.beforeRead(new HashMap<>());

        List<Map<String, Object>> rows = input.readAll();
        assertEquals(2, rows.size());

        Map<String, Object> row0 = rows.get(0);
        assertEquals(1, row0.get("ID"));
        assertEquals("Alice", row0.get("NAME"));
        assertEquals("100", row0.get("VAL"));

        Map<String, Object> row1 = rows.get(1);
        assertEquals(2, row1.get("ID"));
        assertEquals("Bob", row1.get("NAME"));
        assertEquals("200", row1.get("VAL"));

        input.close();
    }

    /**
     * C8-002（plan 2516 裁定③；C10-004）：export-db 缺省 batchSize=0 缺陷路径回归。
     * 缺陷态：batchSize=0 直传 builder，被 BatchChunkProcessor 静默钳位到 1 → 每行一 chunk
     * （chunk 计数与 per-chunk INFO/状态写放大，2 行即 2 个 chunk）。修复后：ExportDbTool 复制
     * ImportDbTool 的 >0 守卫（守卫对称），缺省 0 不再进入钳位路径，builder 落缺省 batchSize=100，
     * 数据零丢失。钳位面（batchSize<=0 → 1 + 每任务一次 WARN）由 nop-batch-core
     * BatchChunkProcessor.adjustBatchSize 承接（exp 模块无 chunk 计数注入面，放大面断言登记为
     * 变异/回退论证：守卫消除 Export 链唯一的 0 值投递路径，与 ImportDbTool 守卫同型）。
     */
    @Test
    public void testExportDefaultBatchSize_guardSymmetry() throws IOException {
        initData();
        insertData();

        ExportDbTool tool = new ExportDbTool();

        IResource resource = classpathResource("config/test.export-db-xlsx.xml");
        ExportDbConfig config = (ExportDbConfig) DslModelHelper.loadDslModel(resource);
        // 显式构造缺陷缺省路径：batchSize=0（装配缺省值）
        config.setBatchSize(0);

        JdbcConnectionConfig conn = new JdbcConnectionConfig();
        conn.setDriverClassName(DaoConfigs.CFG_DATASOURCE_DRIVER_CLASS_NAME.get());
        conn.setJdbcUrl(DaoConfigs.CFG_DATASOURCE_JDBC_URL.get());
        conn.setUsername(DaoConfigs.CFG_DATASOURCE_USERNAME.get());
        conn.setPassword(DaoConfigs.CFG_DATASOURCE_PASSWORD.get());
        config.setJdbcConnection(conn);

        File outputDir = new File(getTargetDir(), "export-default-batchsize-output");
        outputDir.mkdirs();
        config.setOutputDir(outputDir.getAbsolutePath());

        tool.setConfig(config);
        tool.execute();

        // 守卫落缺省后数据零丢失（chunk 放大消除不改变导出结果正确性）
        File xlsxFile = new File(outputDir, "test_export.xlsx");
        assertTrue(xlsxFile.exists(), "xlsx file should exist");

        ExcelResourceIO<Map<String, Object>> excelIO = new ExcelResourceIO<>();
        excelIO.setHeaders(List.of("ID", "NAME", "VAL"));
        IRecordInput<Map<String, Object>> input = excelIO.openInput(new FileResource(xlsxFile), null);
        input.beforeRead(new HashMap<>());
        assertEquals(2, input.readAll().size(), "all rows must be exported under default batchSize");
        input.close();
    }

    void initData() {
        jdbcTemplate.executeUpdate(new SQL(
                "create table test_export (" +
                        "  id INT not null," +
                        "  name VARCHAR(50) not null," +
                        "  val VARCHAR(100)" +
                        ")"
        ));
    }

    void insertData() {
        jdbcTemplate.executeUpdate(new SQL(
                "insert into test_export (id, name, val) values (1, 'Alice', '100')"
        ));
        jdbcTemplate.executeUpdate(new SQL(
                "insert into test_export (id, name, val) values (2, 'Bob', '200')"
        ));
    }
}
