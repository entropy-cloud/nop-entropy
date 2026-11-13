package io.nop.dyn.service.codegen;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MA-06 dyn 侧（C1-005，plan 2525 裁定⑦）：DynOrmModelProvider.entityModelLoader 懒初始化
 * 非 volatile + 检查-然后-行动无同步——双实例竞态 + JMM 可见性窗。修复 = volatile + DCL。
 * 单行面站点，结构钉承载（行为红需并发构造器拦截，现类无注入点——沿结构钉先例）。
 */
public class TestDynOrmModelProviderLazyInit_MA06 {

    @Test
    public void testEntityModelLoaderVolatileWithDcl() throws Exception {
        String code = Files.readString(
                Path.of("src/main/java/io/nop/dyn/service/codegen/DynOrmModelProvider.java"));

        assertTrue(code.contains("private volatile IDynamicEntityModelProvider entityModelLoader"),
                "C1-005: entityModelLoader must be volatile — without it a second thread may read"
                        + " a stale null and construct a duplicate loader instance");
        assertTrue(code.contains("synchronized (this)"),
                "C1-005: getEntityModelLoader must guard construction with synchronized recheck (DCL)");
        assertTrue(code.contains("循环依赖规避语义保持"),
                "C1-005: the circular-dependency-avoidance comment must be preserved with the DCL rewrite");
    }
}
