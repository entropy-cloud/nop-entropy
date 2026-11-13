package io.nop.code.service.impl;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.BeanContainer;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.core.adapter.LanguageAdapterRegistry;
import io.nop.code.core.analyzer.ProjectAnalysisResult;
import io.nop.code.core.analyzer.ProjectAnalyzer;
import io.nop.code.dao.entity.NopCodeIndex;
import io.nop.code.lang.java.JavaLanguageAdapter;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.code.service.entity.NopCodeIndexBizModel;
import io.nop.core.resource.IResourceLoader;
import io.nop.dao.api.IDaoProvider;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static io.nop.code.service.NopCodeErrors.ERR_CODE_INDEX_FILE_COUNT_EXCEEDED;
import static io.nop.code.service.NopCodeErrors.ERR_CODE_INDEX_TOTAL_BYTES_EXCEEDED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * plan 2532 组B 红⑤（W-28 / C5-R2-006 触发面预检，TP-002 触发面 + P3 随批收敛面验收）：admin
 * 触发面（indexDirectory——biz 双入口 triggerFullIndex/indexDirectory 均委托 service 此一处）
 * 规模预检 fail-fast。
 *
 * <p>驱动器形态（设计裁定④）：阈值注入 setter（文件数阈值=2 + 累计字节阈值=1）+ test-project
 * 小夹具（6 个小文件）→ indexDirectory → 断言 NopException fail-fast（错误码可辨识、param 含
 * indexId/path/实测值/上限）且分析未执行（捕获型 analyzer 零调用 + 零 NopCodeIndex 实体落库）；
 * triggerFullIndex 委托链同门一次预检（biz 入口触发同夹具一次失败、分析零调用）。
 *
 * <p>红证形态（设计裁定④）：pre-fix 编译期构造红——阈值注入 setter（{@code setMaxIndexFileCount}
 * / {@code setMaxIndexTotalBytes}）与错误码（{@code ERR_CODE_INDEX_FILE_COUNT_EXCEEDED} /
 * {@code ERR_CODE_INDEX_TOTAL_BYTES_EXCEEDED}）均不存在；post-fix 语义红 = 超规模直通分析。
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestIndexDirectoryScalePrecheck_W_02 extends JunitAutoTestCase {

    @Inject
    ICodeIndexService codeIndexService;

    @Inject
    IDaoProvider daoProvider;

    /** 手工子类代理（零 mockito）：记录分析入口是否被触发（fail-fast 必须发生在分析之前）。 */
    static class RecordingAnalyzer extends ProjectAnalyzer {
        boolean invoked;

        RecordingAnalyzer(LanguageAdapterRegistry registry) {
            super(registry);
        }

        @Override
        public ProjectAnalysisResult analyzeProject(IResourceLoader resourceLoader, String vfsPath,
                                                    String filePattern) {
            invoked = true;
            return super.analyzeProject(resourceLoader, vfsPath, filePattern);
        }
    }

    private RecordingAnalyzer wireRecordingAnalyzer(CodeIndexService service) {
        LanguageAdapterRegistry registry = new LanguageAdapterRegistry();
        registry.registerAdapter(new JavaLanguageAdapter());
        RecordingAnalyzer analyzer = new RecordingAnalyzer(registry);
        service.analyzer = analyzer;
        return analyzer;
    }

    private String testProjectPath() {
        return Paths.get("src/test/resources/test-project/src/main/java").toAbsolutePath().toString();
    }

    @Test
    void testW_02_triggerPointFailsFastOnFileCountExceeded() {
        CodeIndexService service = (CodeIndexService) codeIndexService;
        RecordingAnalyzer analyzer = wireRecordingAnalyzer(service);
        service.setMaxIndexFileCount(2);
        service.setMaxIndexTotalBytes(Long.MAX_VALUE);

        String indexId = "w02-precheck-count";
        NopException ex = assertThrows(NopException.class,
                () -> service.indexDirectory(indexId, testProjectPath(), "**/*.java"),
                "directory with more files than the injected count threshold must fail fast");

        assertEquals("nop.err.code.index-file-count-exceeded", ex.getErrorCode(),
                "file-count precheck must carry an identifiable error code");
        assertEquals(indexId, ex.getParam("indexId"));
        assertEquals(3, ((Number) ex.getParam("fileCount")).intValue(),
                "precheck trips when enumeration exceeds the injected threshold of 2");
        assertEquals(2, ((Number) ex.getParam("limit")).intValue());

        assertFalse(analyzer.invoked, "analysis must not execute when the trigger precheck fails");
        assertNull(daoProvider.daoFor(NopCodeIndex.class).getEntityById(indexId),
                "no index entity may be written when the trigger precheck fails");
    }

    @Test
    void testW_02_triggerPointFailsFastOnTotalBytesExceeded() {
        CodeIndexService service = (CodeIndexService) codeIndexService;
        RecordingAnalyzer analyzer = wireRecordingAnalyzer(service);
        service.setMaxIndexFileCount(50000);
        service.setMaxIndexTotalBytes(1);

        String indexId = "w02-precheck-bytes";
        NopException ex = assertThrows(NopException.class,
                () -> service.indexDirectory(indexId, testProjectPath(), "**/*.java"),
                "directory whose cumulative bytes exceed the injected threshold must fail fast");

        assertEquals("nop.err.code.index-total-bytes-exceeded", ex.getErrorCode(),
                "cumulative-byte precheck must carry an identifiable error code");
        assertEquals(1L, ((Number) ex.getParam("limit")).longValue());
        assertTrue(((Number) ex.getParam("totalBytes")).longValue() > 1,
                "measured total bytes must be reported in the error params");

        assertFalse(analyzer.invoked, "analysis must not execute when the trigger precheck fails");
        assertNull(daoProvider.daoFor(NopCodeIndex.class).getEntityById(indexId),
                "no index entity may be written when the trigger precheck fails");
    }

    @Test
    void testW_02_bizTriggerFullIndexHitsTheSameSingleGate() {
        CodeIndexService service = (CodeIndexService) codeIndexService;
        RecordingAnalyzer analyzer = wireRecordingAnalyzer(service);
        service.setMaxIndexFileCount(2);
        service.setMaxIndexTotalBytes(Long.MAX_VALUE);

        NopCodeIndexBizModel bizModel = BeanContainer.instance()
                .getBeansOfType(NopCodeIndexBizModel.class).values().iterator().next();

        String indexId = "w02-precheck-biz";
        NopException ex = assertThrows(NopException.class,
                () -> bizModel.triggerFullIndex(indexId, testProjectPath()),
                "biz triggerFullIndex delegates to the same service entry and must hit the same gate");

        assertEquals("nop.err.code.index-file-count-exceeded", ex.getErrorCode(),
                "biz entry must surface the service-level precheck error (single enforcement point,"
                        + " no double trigger)");
        assertFalse(analyzer.invoked, "analysis must not execute through the biz delegation chain");
    }
}
