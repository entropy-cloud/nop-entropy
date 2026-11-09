/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.exp;

import io.nop.batch.core.BatchTaskBuilder;
import io.nop.batch.core.IBatchTaskContext;
import io.nop.batch.core.impl.BatchTaskContextImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * plan 2546 R4-12（C8-004 / W-07）**结构钉**（pin08d）：
 * `ImportDbTool.runTask` 必须把表名接进 `builder.taskName(...)`——否则 import 表任务全部落
 * `task.name=default`，表级可观测性丢失（ExportDbTool 早有同形接线，Import 侧此前零命中）。
 * <p>
 * 该路径的完整端到端需要真实 DB（Exporter 会建 Hikari 池并同步 schema），故以**源码契约 +
 * builder 行为**双面钉承载：① 源码存在 `builder.taskName(tableConfig.getName())`；
 * ② `BatchTaskBuilder.taskName` 确实把名字透传到任务上下文（可离线验证）。
 * 两腿合起来构成"接线可达"的证据，provenance 有限已在 plan 载体表如实标注为结构钉。
 */
public class TestCraR412ImportTaskNameWiring {

    /** 腿1（源码契约）：ImportDbTool 必须存在 taskName 接线。 */
    @Test
    public void pin08d_sourceWiresTaskName() throws Exception {
        String src = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get("src/main/java/io/nop/batch/exp/ImportDbTool.java")),
                java.nio.charset.StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertTrue(
                src.contains("builder.taskName(tableConfig.getName())"),
                "ImportDbTool.runTask 必须接 builder.taskName(tableConfig.getName())（否则 task.name 落 default）");
    }

    /** 腿2（行为）：builder.taskName 确实透传到执行上下文的 taskName。 */
    @Test
    public void pin08d_builderTaskNameReachesContext() {
        BatchTaskBuilder<String, String> builder = new BatchTaskBuilder<>();
        builder.taskName("my_table");
        builder.loader(ctx -> (batchSize, chunkCtx) -> new java.util.ArrayList<>());
        builder.consumer(ctx -> (items, chunkCtx) -> {
        });
        builder.concurrency(1);

        IBatchTaskContext ctx = new BatchTaskContextImpl();
        io.nop.api.core.util.FutureHelper.syncGet(builder.buildTask().executeAsync(ctx));
        assertEquals("my_table", ctx.getTaskName(),
                "builder.taskName 必须成为任务上下文 taskName（metrics tag 依赖它）");
    }
}
