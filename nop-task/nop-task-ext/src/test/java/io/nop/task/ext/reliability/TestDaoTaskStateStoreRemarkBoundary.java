package io.nop.task.ext.reliability;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.task.ITaskRuntime;
import io.nop.task.ITaskState;
import io.nop.task.dao.entity.NopTaskInstance;
import io.nop.task.dao.store.DaoTaskStateStore;
import io.nop.task.impl.TaskRuntimeImpl;
import io.nop.xlang.api.XLang;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * plan 364 Phase 3 [维度01-04]：task 级 remark / errMsg 写入守卫与列宽对齐。
 *
 * <p>缺陷（修复前）：result JSON 以 4000 为守卫阈值写入 REMARK VARCHAR(200)——长度 201~4000 时
 * 严格 SQL 模式 saveTaskState 报错、非严格模式被截断成非法 JSON（resume 解析失败静默降级）。
 * 修复后阈值取 REMARK_MAX_LEN=200（与列 precision 对齐），超长跳过并告警。
 *
 * <p>errMsg 处置：task 级 ERR_MSG 为 VARCHAR(500)，超长描述截断至列宽（完整诊断仍经
 * errorBeanData 持久化，不影响 resume 重抛）。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE)
public class TestDaoTaskStateStoreRemarkBoundary extends JunitBaseTestCase {

    @Inject
    IDaoProvider daoProvider;

    private DaoTaskStateStore store;
    private ITaskRuntime taskRt;
    private ITaskState taskState;
    private int taskNameSeq;

    @BeforeEach
    public void setUpStore() {
        store = new DaoTaskStateStore();
        store.setDaoProvider(daoProvider);

        TaskRuntimeImpl runtime = new TaskRuntimeImpl(null, store, null, XLang.newEvalScope(), false);
        taskState = store.newTaskState("testRemarkBoundaryTask" + (++taskNameSeq), 0, runtime);
        runtime.setTaskState(taskState);
        taskRt = runtime;
    }

    /** 构造序列化后长度恰为 targetLen 的 result JSON（{"k":"xxx..."} 骨架 8 字符 + 字符串体）。 */
    private Object resultOfJsonLength(int targetLen) {
        int pad = Math.max(1, targetLen - 8);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("k", "x".repeat(pad));
        String json = JsonTool.serialize(map, false);
        // 微调 padding 使序列化长度精确命中目标（防骨架长度假设漂移）
        while (json.length() > targetLen && pad > 1) {
            map.put("k", "x".repeat(--pad));
            json = JsonTool.serialize(map, false);
        }
        while (json.length() < targetLen) {
            map.put("k", "x".repeat(++pad));
            json = JsonTool.serialize(map, false);
        }
        assertEquals(targetLen, json.length(), "test fixture must produce exact serialized length");
        return map;
    }

    private NopTaskInstance reloadTaskEntity() {
        IEntityDao<NopTaskInstance> dao = daoProvider.daoFor(NopTaskInstance.class);
        return dao.getEntityById(taskState.getTaskInstanceId());
    }

    @Test
    public void remarkAt199_persisted() {
        taskState.setResultValue(resultOfJsonLength(199));
        store.saveTaskState(taskRt);
        NopTaskInstance entity = reloadTaskEntity();
        assertEquals(199, entity.getRemark().length(),
                "result JSON at 200-char column boundary minus 1 must persist");
    }

    @Test
    public void remarkAt200_persisted() {
        taskState.setResultValue(resultOfJsonLength(200));
        store.saveTaskState(taskRt);
        NopTaskInstance entity = reloadTaskEntity();
        assertEquals(200, entity.getRemark().length(),
                "result JSON exactly at REMARK VARCHAR(200) width must persist");
    }

    @Test
    public void remarkAt201_skippedWithoutDbError() {
        // 修复前：201 长度 JSON 通过 4000 守卫写入 VARCHAR(200)——严格模式 DB 报错 / 非严格截断成非法 JSON
        taskState.setResultValue(resultOfJsonLength(201));
        store.saveTaskState(taskRt);
        NopTaskInstance entity = reloadTaskEntity();
        assertNull(entity.getRemark(),
                "result JSON exceeding REMARK column width must be skipped (with warn), not corrupt the row");
    }

    @Test
    public void errMsgOverColumnLimit_truncatedTo500_fullDiagInErrorBeanData() {
        taskState.exception(new RuntimeException("x".repeat(800)));
        store.saveTaskState(taskRt);

        NopTaskInstance entity = reloadTaskEntity();
        org.junit.jupiter.api.Assertions.assertNotNull(entity.getErrMsg());
        assertEquals(500, entity.getErrMsg().length(),
                "task-level errMsg must be truncated to ERR_MSG VARCHAR(500) width");
        org.junit.jupiter.api.Assertions.assertNotNull(entity.getErrorBeanData());
        org.junit.jupiter.api.Assertions.assertTrue(entity.getErrorBeanData().contains("x".repeat(100)),
                "full diagnostics remain in errorBeanData (truncation affects display column only)");
    }
}
