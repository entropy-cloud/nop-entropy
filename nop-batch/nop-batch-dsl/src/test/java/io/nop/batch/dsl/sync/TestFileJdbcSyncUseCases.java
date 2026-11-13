package io.nop.batch.dsl.sync;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.core.context.ServiceContextImpl;
import io.nop.core.lang.sql.SQL;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.dataset.rowmapper.SingleColumnRowMapper;
import io.nop.dataset.rowmapper.StringColumnRowMapper;
import io.nop.task.ITask;
import io.nop.task.ITaskFlowManager;
import io.nop.task.ITaskRuntime;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文件和 JDBC 之间数据同步的典型用例配置验证：
 * 1. JDBC -> JDBC
 * 2. JDBC -> CSV
 * 3. CSV -> JDBC（含 processor 字段变换）
 */
@NopTestConfig(localDb = true)
public class TestFileJdbcSyncUseCases extends JunitAutoTestCase {

    static final int ROW_COUNT = 250;
    static final int CSV_ROW_COUNT = 120;

    @Inject
    IJdbcTemplate jdbcTemplate;

    @Inject
    ITaskFlowManager taskFlowManager;

    @BeforeEach
    public void setUp() throws IOException {
        dropTable("sync_src");
        dropTable("sync_jdbc");
        dropTable("sync_csv_jdbc");

        jdbcTemplate.executeUpdate(new SQL("create table sync_src (" +
                "id int not null primary key, name varchar(50) not null, amount int not null)"));

        for (int i = 1; i <= ROW_COUNT; i++) {
            jdbcTemplate.executeUpdate(new SQL("insert into sync_src (id, name, amount) values (" +
                    i + ", 'source-" + i + "', " + (i * 10) + ")"));
        }

        File targetDir = getTargetFile("sync-test");
        deleteRecursively(targetDir);
        assertTrue(targetDir.mkdirs(), "failed to create target dir");
    }

    @Test
    public void testJdbcToJdbc() {
        jdbcTemplate.executeUpdate(new SQL("create table sync_jdbc (" +
                "id int not null primary key, name varchar(50) not null, amount int not null)"));

        runTask("/test/sync/jdbc-to-jdbc.task.xml");

        assertEquals(ROW_COUNT, queryInt("select count(*) from sync_jdbc"));
        assertEquals(ROW_COUNT * (ROW_COUNT + 1) / 2 * 10, queryInt("select sum(amount) from sync_jdbc"));

        Map<String, Object> row = jdbcTemplate.<Map<String, Object>>findAll(new SQL(
                "select id, name, amount from sync_jdbc where id = 1")).get(0);
        assertEquals("source-1", row.get("name"));
    }

    @Test
    public void testJdbcToCsv() throws IOException {
        runTask("/test/sync/jdbc-to-csv.task.xml");

        File outputFile = getTargetFile("sync-test/jdbc-to-csv.csv");
        assertTrue(outputFile.exists(), "csv output should exist");

        List<String> lines = Files.readAllLines(outputFile.toPath(), StandardCharsets.UTF_8);
        assertEquals(ROW_COUNT + 1, lines.size());
        assertEquals("id,name,amount", lines.get(0));
        assertEquals("1,source-1,10", lines.get(1));
        assertEquals("250,source-250,2500", lines.get(250));
    }

    @Test
    public void testCsvToJdbc() throws IOException {
        File inputFile = getTargetFile("sync-test/csv-to-jdbc-input.csv");
        StringBuilder sb = new StringBuilder("id,name,amount\n");
        for (int i = 1; i <= CSV_ROW_COUNT; i++) {
            sb.append(i).append(",csv-").append(i).append(',').append(i * 100).append('\n');
        }
        Files.write(inputFile.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));

        jdbcTemplate.executeUpdate(new SQL("create table sync_csv_jdbc (" +
                "id int not null primary key, name varchar(50) not null, amount int not null)"));

        runTask("/test/sync/csv-to-jdbc.task.xml");

        assertEquals(CSV_ROW_COUNT, queryInt("select count(*) from sync_csv_jdbc"));
        assertEquals("csv-1-imported", queryString("select name from sync_csv_jdbc where id = 1"));
        assertEquals("csv-120-imported", queryString("select name from sync_csv_jdbc where id = 120"));
    }

    void runTask(String taskPath) {
        ITask task = taskFlowManager.parseTask(VirtualFileSystem.instance().getResource(taskPath));
        ITaskRuntime taskRt = taskFlowManager.newTaskRuntime(task, false, new ServiceContextImpl());
        task.execute(taskRt).syncGetOutputs();
    }

    int queryInt(String sql) {
        Object value = jdbcTemplate.findAll(new SQL(sql), SingleColumnRowMapper.INSTANCE).get(0);
        return ((Number) value).intValue();
    }

    String queryString(String sql) {
        return jdbcTemplate.findAll(new SQL(sql), StringColumnRowMapper.INSTANCE).get(0);
    }

    void dropTable(String tableName) {
        try {
            jdbcTemplate.executeUpdate(new SQL("drop table " + tableName));
        } catch (Exception ignored) {
            // table may not exist
        }
    }

    void deleteRecursively(File file) throws IOException {
        if (!file.exists())
            return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        Files.delete(file.toPath());
    }
}
