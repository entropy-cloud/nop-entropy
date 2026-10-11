package io.nop.autotest.core.data;

import io.nop.autotest.core.execute.AutoTestCaseDataSaver;
import io.nop.autotest.core.split.TestCaseJsonDataSplitter;
import io.nop.dao.api.IDaoProvider;
import io.nop.orm.dao.IOrmEntityDao;
import io.nop.orm.model.IColumnModel;
import io.nop.orm.model.IEntityModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-6.8-003/005/006（plan 2393）：测试数据目录契约三判别——
 * ①splitter 表产物落 <dir>/input|output/tables/（消费方契约），splitter→AutoTestCaseData 往返可达；
 * ②variant 路径统一 variants/<v>/…——getOutputTableFiles(variant) 能读到 Saver 写入面产物；
 * ③removeInputTable default 守卫——基础表不被录制流程静默删除。
 */
public class TestAutoTestDataDirContract {

    @TempDir
    File dir;

    // ==================== Proxy stub：IEntityModel 只触达 getTableName/getColumns ====================

    private static IOrmEntityDao<?> daoFor(String tableName) {
        IEntityModel model = (IEntityModel) Proxy.newProxyInstance(
                TestAutoTestDataDirContract.class.getClassLoader(),
                new Class[]{IEntityModel.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getTableName":
                            return tableName;
                        case "getColumns":
                            return List.of(column("id"), column("name"));
                        default:
                            return defaultFor(method.getReturnType());
                    }
                });
        return (IOrmEntityDao<?>) Proxy.newProxyInstance(
                TestAutoTestDataDirContract.class.getClassLoader(),
                new Class[]{IOrmEntityDao.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getEntityModel":
                            return model;
                        default:
                            return defaultFor(method.getReturnType());
                    }
                });
    }

    private static IColumnModel column(String code) {
        return (IColumnModel) Proxy.newProxyInstance(
                TestAutoTestDataDirContract.class.getClassLoader(),
                new Class[]{IColumnModel.class},
                (proxy, method, args) -> "getCode".equals(method.getName()) ? code : defaultFor(method.getReturnType()));
    }

    private static Object defaultFor(Class<?> type) {
        if (type == boolean.class)
            return false;
        if (type == int.class)
            return 0;
        if (type == long.class)
            return 0L;
        return null;
    }

    private static IDaoProvider singleTableProvider() {
        return (IDaoProvider) Proxy.newProxyInstance(
                TestAutoTestDataDirContract.class.getClassLoader(),
                new Class[]{IDaoProvider.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("daoForTable"))
                        return daoFor((String) args[0]);
                    if (method.getName().equals("dao"))
                        return daoFor((String) args[0]);
                    return defaultFor(method.getReturnType());
                });
    }

    private static Map<String, Object> rowData(String id, String name) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("name", name);
        return row;
    }

    // ==================== ① P1-6.8-003 splitter→loader 往返 ====================

    @Test
    public void testSplitterTablesLandInConsumerContractDirs() throws Exception {
        TestCaseJsonDataSplitter splitter = new TestCaseJsonDataSplitter(singleTableProvider()).silent(true);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("tables", Map.of("T1", List.of(rowData("1", "a"))));
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("tables", Map.of("T1", List.of(rowData("1", "a"))));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("input", input);
        data.put("output", output);

        splitter.splitToDir(data, dir);

        assertTrue(new File(dir, "input/tables/T1.csv").exists(),
                "input table must land at <dir>/input/tables/ (P1-6.8-003)");
        assertTrue(new File(dir, "output/tables/T1.csv").exists(),
                "output table must land at <dir>/output/tables/ (P1-6.8-003)");
        assertFalse(new File(dir, "tables/input/T1.csv").exists(),
                "reversed layout <dir>/tables/input/ must not be produced");

        // 往返：消费方按自身契约枚举读到产物
        AutoTestCaseData caseData = new AutoTestCaseData(dir, null);
        assertEquals(1, caseData.getInputTableFiles(null).size(), "loader reads the splitter's input table");
        assertEquals(1, caseData.getOutputTableFiles(null).size(), "loader reads the splitter's output table");
    }

    // ==================== ② P1-6.8-005 variant 路径统一 ====================

    @Test
    public void testVariantOutputTablesReachTheCheckerScanFace() throws Exception {
        // 基础表 + Saver 写入形态的 variant 输出表（variants/<v>/output/tables/）
        File base = new File(dir, "input/tables/T1.csv");
        Files.createDirectories(base.getParentFile().toPath());
        Files.write(base.toPath(), "id,name\n1,a\n".getBytes());

        File varOut = new File(dir, "variants/v1/output/tables/T1.csv");
        Files.createDirectories(varOut.getParentFile().toPath());
        Files.write(varOut.toPath(), "id,name,__chg\n1,a,U\n".getBytes());

        AutoTestCaseData caseData = new AutoTestCaseData(dir, null);
        List<File> outs = caseData.getOutputTableFiles("v1");
        assertTrue(outs.contains(varOut),
                "variant output table written by the saver must reach the checker scan face (P1-6.8-005)");

        // variant input 表（variants/<v>/input/tables/）同样可达
        File varIn = new File(dir, "variants/v1/input/tables/T2.csv");
        Files.createDirectories(varIn.getParentFile().toPath());
        Files.write(varIn.toPath(), "id,name\n2,b\n".getBytes());
        List<File> ins = caseData.getInputTableFiles("v1");
        assertTrue(ins.contains(varIn), "variant input table must be enumerated");
        assertTrue(ins.contains(base), "base table merged with variant table");
    }

    // ==================== ③ P1-6.8-006 removeInputTable default 守卫 ====================

    @Test
    public void testRemoveInputTableSkipsDefaultBaseTable() throws Exception {
        File base = new File(dir, "input/tables/T1.csv");
        Files.createDirectories(base.getParentFile().toPath());
        Files.write(base.toPath(), "id,name\n1,a\n".getBytes());

        AutoTestCaseData caseData = new AutoTestCaseData(dir, null);
        AutoTestCaseDataSaver saver = new AutoTestCaseDataSaver(null, caseData, null);

        java.lang.reflect.Method m = AutoTestCaseDataSaver.class.getDeclaredMethod("removeInputTable",
                io.nop.orm.model.IEntityModel.class);
        m.setAccessible(true);
        m.invoke(saver, daoFor("T1").getEntityModel());

        assertTrue(base.exists(), "default base table must NOT be deleted by the recording flow (P1-6.8-006)");
    }

    @Test
    public void testRemoveInputTableDeletesVariantTableForNonDefaultVariant() throws Exception {
        File varTable = new File(dir, "variants/v1/input/tables/T1.csv");
        Files.createDirectories(varTable.getParentFile().toPath());
        Files.write(varTable.toPath(), "id,name\n1,a\n".getBytes());

        AutoTestCaseData caseData = new AutoTestCaseData(dir, null);
        AutoTestCaseDataSaver saver = new AutoTestCaseDataSaver("v1", caseData, null);

        java.lang.reflect.Method m = AutoTestCaseDataSaver.class.getDeclaredMethod("removeInputTable",
                io.nop.orm.model.IEntityModel.class);
        m.setAccessible(true);
        m.invoke(saver, daoFor("T1").getEntityModel());

        assertFalse(varTable.exists(), "variant-local table is deleted for non-default variant");
    }
}
