/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.dsl.manager;

import io.nop.batch.dsl.model.BatchLoaderModel;
import io.nop.batch.dsl.model.BatchOrmReaderModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2546 R4-12（C7-006 / W-02）：任务级 saveState 对非 file/excel reader 的续传缺口判定。
 * <p>
 * 放在 dsl 模块——{@code BatchLoaderModel} 在 {@code nop-batch-dsl}，依赖为 dsl→core 单向，
 * core 测试树无法 import。
 */
public class TestCraR412DslPins {

    /** pin02：orm/jdbc/generator/bean/provider/adapter 均无续传能力 → true。 */
    @Test
    public void pin02_nonFileReadersWithoutResume() {
        BatchLoaderModel loader = new BatchLoaderModel();

        BatchOrmReaderModel orm = new BatchOrmReaderModel();
        orm.setEntityName("io.nop.Test");
        loader.setOrmReader(orm);
        assertTrue(ModelBasedBatchTaskBuilderFactory.isSaveStateUnsupportedReader(Boolean.TRUE, loader),
                "任务级 saveState + orm-reader 必须告警（completedIndex 不推进）");
    }

    /** pin02b：任务级 saveState=false 时不告警。 */
    @Test
    public void pin02b_noWarnWhenSaveStateOff() {
        BatchLoaderModel loader = new BatchLoaderModel();
        BatchOrmReaderModel orm = new BatchOrmReaderModel();
        orm.setEntityName("io.nop.Test");
        loader.setOrmReader(orm);
        assertFalse(ModelBasedBatchTaskBuilderFactory.isSaveStateUnsupportedReader(Boolean.FALSE, loader));
        assertFalse(ModelBasedBatchTaskBuilderFactory.isSaveStateUnsupportedReader(null, loader));
    }

    /** pin02c：file/excel reader 有真续传能力 → 不告警。 */
    @Test
    public void pin02c_fileReaderHasResume() {
        BatchLoaderModel loader = new BatchLoaderModel();
        loader.setFileReader(new io.nop.batch.dsl.model.BatchFileReaderModel());
        assertFalse(ModelBasedBatchTaskBuilderFactory.isSaveStateUnsupportedReader(Boolean.TRUE, loader),
                "file-reader 的 completedIndex 会推进，不应告警");
    }
}
