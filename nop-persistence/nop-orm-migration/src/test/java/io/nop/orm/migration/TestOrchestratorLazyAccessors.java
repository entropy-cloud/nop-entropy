/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.orm.migration;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.5-C1-008（plan 2554 裁定 G）载体：编排器惰性字段残余收敛——
 * ①五惰性字段 volatile 结构钉（反射 Modifier 检查）；
 * ②doAbort / setChainStopJoinTimeoutMs 直读点改经 synchronized 访问器的源级钉；
 * ③stop 路径（:268/:333/:636 javadoc 豁免面：只停本进程已启动链，访问器惰性构造会引入
 * 「停止时建 manager」误行为）故意直读保持原样的源级钉。
 */
public class TestOrchestratorLazyAccessors {

    private static final String[] LAZY_FIELDS = {
            "stateStore", "mapping", "fullJob", "incrementalManager", "verifier"
    };

    @Test
    public void testLazyFieldsAreVolatile() throws Exception {
        for (String name : LAZY_FIELDS) {
            Field field = OrmMigrationOrchestrator.class.getDeclaredField(name);
            assertTrue(Modifier.isVolatile(field.getModifiers()),
                    "lazy field '" + name + "' must be volatile (2.5-C1-008)");
        }
    }

    @Test
    public void testDoAbortAndTimeoutSetterReadViaAccessor() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/io/nop/orm/migration/OrmMigrationOrchestrator.java"));
        // 裁定 G：doAbort 直读点改走 synchronized 访问器（锁外裸读非 volatile 发布面收敛）
        int doAbortAt = source.indexOf("private void doAbort");
        int doAbortEnd = source.indexOf("\n    }\n", doAbortAt);
        assertTrue(doAbortAt > 0 && doAbortEnd > doAbortAt);
        String doAbort = source.substring(doAbortAt, doAbortEnd);
        assertTrue(doAbort.contains("incrementalManager()"),
                "doAbort must read the lazy manager via the synchronized accessor");
        assertFalse(doAbort.contains("= this.incrementalManager"),
                "doAbort must not read the bare field (pre-fix shape gone)");
        // setChainStopJoinTimeoutMs 同面收敛
        int setterAt = source.indexOf("public void setChainStopJoinTimeoutMs");
        assertTrue(setterAt > 0);
        int setterBody = source.indexOf("public void setIncrementalManager", setterAt);
        assertTrue(source.substring(setterAt, setterBody).contains("incrementalManager()"),
                "setChainStopJoinTimeoutMs must consume the synchronized accessor");
    }

    @Test
    public void testStopPathIntentionalDirectReadsExempted() throws Exception {
        // 豁免面钉：stop 路径三处故意直读（doRunIncremental 重入复核 / doRunVerify 死链门禁 /
        // stopIncrementalChains）零改写——直读语义 = 只停本进程已启动链
        String source = Files.readString(Path.of(
                "src/main/java/io/nop/orm/migration/OrmMigrationOrchestrator.java"));
        int directReads = source.split("= this\\.incrementalManager", -1).length - 1;
        assertEquals(3, directReads,
                "the three javadoc-exempt stop-path direct reads must stay untouched, got "
                        + directReads);
        // 且五字段 volatile 化声明在字段面（读面豁免以 volatile 可见性兜底）
        assertTrue(source.contains("private volatile IncrementalJobManager incrementalManager;"));
    }
}
