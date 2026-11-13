package io.nop.batch.dsl.manager;

import io.nop.batch.core.loader.ResourceRecordLoaderProvider;
import io.nop.batch.dsl.model.BatchExcelReaderModel;
import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.core.lang.eval.IEvalAction;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * C5-001（plan 2516 裁定④；C10-011 接线半）：excel-reader maxCountExpr 接线恢复断言。
 * 缺陷态（红）：FileBatchSupport.newExcelReader 的 maxCountExpr 接线为注释死代码——
 * 已配置 maxCount 的 excel 链路读取条目数不受上限约束（配置被静默忽略）。
 * 修复后（绿）：接线恢复后 maxCountExpr 装配到 ResourceRecordLoaderProvider 并可求值。
 * 行为变更声明：已配置 maxCount 的存量链路从全量读取变为截断读取（超出上限的条目不再进入处理）。
 * 物化路径行为面（collector 全量 List）为已声明包络，残留归 R-G watch-only（owner doc Phase 3 落账）。
 * 继承 JunitAutoTestCase：FileBatchSupport.newExcelReader 内部引用 VirtualFileSystem.instance()，
 * JunitBaseTestCase 不初始化虚拟文件系统（nop.err.core.resource.virtual-file-system-not-initialized）。
 */
@NopTestConfig
public class TestFileBatchSupportExcelMaxCount extends JunitAutoTestCase {


    static Object privateField(Object bean, String name) {
        try {
            Field f = bean.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(bean);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void excelReaderMaxCountExpr_wiredToLoaderProvider() {
        BatchExcelReaderModel model = new BatchExcelReaderModel();
        IEvalAction maxCount = scope -> 3L;
        model.setMaxCountExpr(maxCount);
        model.setFilePath(scope -> "/test/max-count.xlsx");

        ResourceRecordLoaderProvider<Object> loader =
                FileBatchSupport.newExcelReader(model, null, false, null);

        Object wired = privateField(loader, "maxCountExpr");
        assertNotNull(wired,
                "configured maxCountExpr must be wired to the loader provider (C5-001)");
        assertEquals(3L, ((IEvalAction) wired).invoke(null),
                "wired maxCountExpr must evaluate to the configured upper bound");
    }

    @Test
    public void excelReaderWithoutMaxCount_keepsNullExpr() {
        BatchExcelReaderModel model = new BatchExcelReaderModel();
        model.setFilePath(scope -> "/test/no-limit.xlsx");

        ResourceRecordLoaderProvider<Object> loader =
                FileBatchSupport.newExcelReader(model, null, false, null);

        assertNull(privateField(loader, "maxCountExpr"),
                "unset maxCountExpr stays null so loader reads without upper bound");
    }
}
