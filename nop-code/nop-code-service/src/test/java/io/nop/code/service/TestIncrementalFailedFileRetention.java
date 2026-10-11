package io.nop.code.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.core.adapter.LanguageAdapterRegistry;
import io.nop.code.core.analyzer.ICodeFileAnalyzer;
import io.nop.code.core.analyzer.ILanguageAdapter;
import io.nop.code.core.model.CodeFileAnalysisResult;
import io.nop.code.core.model.CodeLanguage;
import io.nop.code.dao.entity.NopCodeFile;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.code.service.impl.CodeIndexService;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1-6.7-010 (plan 2401): a changed file whose analysis fails (analyzer throws
 * or returns null) must keep its previous records + fingerprint instead of being
 * silently marked as synced; the next incremental run must retry it.
 */
@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestIncrementalFailedFileRetention extends JunitAutoTestCase {

    private static final String EXT = ".fake";

    enum Mode {OK, THROW, NULL}

    static class ToggleAnalyzer implements ICodeFileAnalyzer {
        volatile Mode mode = Mode.OK;

        @Override
        public CodeLanguage getLanguage() {
            return CodeLanguage.JAVA;
        }

        @Override
        public List<String> getFileExtensions() {
            return List.of(EXT);
        }

        @Override
        public CodeFileAnalysisResult analyze(String filePath, String sourceCode) {
            if (mode == Mode.THROW) {
                throw new IllegalStateException("injected analyze failure");
            }
            if (mode == Mode.NULL) {
                return null;
            }
            CodeFileAnalysisResult result = new CodeFileAnalysisResult();
            result.setFilePath(filePath);
            result.setSourceCode(sourceCode);
            result.setLanguage(CodeLanguage.JAVA);
            result.setLineCount(sourceCode == null ? 0 : sourceCode.split("\r?\n").length);
            return result;
        }
    }

    static class FakeAdapter implements ILanguageAdapter {
        private final ICodeFileAnalyzer analyzer;

        FakeAdapter(ICodeFileAnalyzer analyzer) {
            this.analyzer = analyzer;
        }

        @Override
        public CodeLanguage getLanguage() {
            return CodeLanguage.JAVA;
        }

        @Override
        public ICodeFileAnalyzer getFileAnalyzer() {
            return analyzer;
        }

        @Override
        public List<String> getFileExtensions() {
            return List.of(EXT);
        }

        @Override
        public List<String> getExcludePatterns() {
            return List.of();
        }
    }

    @Inject
    ICodeIndexService codeIndexService;

    @Inject
    IDaoProvider daoProvider;

    @TempDir
    Path tempDir;

    private String fileHash(String indexId) {
        IEntityDao<NopCodeFile> dao = daoProvider.daoFor(NopCodeFile.class);
        QueryBean q = new QueryBean();
        q.addFilter(FilterBeans.eq("indexId", indexId));
        for (NopCodeFile file : dao.findAllByQuery(q)) {
            if (file.getFilePath() != null && file.getFilePath().endsWith(EXT)) {
                return file.getFileHash();
            }
        }
        return null;
    }

    private void runRetentionScenario(String indexId, Mode failureMode) throws Exception {
        Path dir = tempDir.resolve("proj-" + indexId);
        Files.createDirectories(dir);
        Path file = dir.resolve("X" + EXT);
        Files.writeString(file, "v1");

        ToggleAnalyzer analyzer = new ToggleAnalyzer();
        CodeIndexService svc = (CodeIndexService) codeIndexService;
        LanguageAdapterRegistry registry = new LanguageAdapterRegistry();
        svc.setRegistry(registry);
        // Register AFTER setRegistry (which re-registers BeanContainer adapters) and reuse
        // the target adapter's CodeLanguage as the map key so it replaces it deterministically.
        registry.registerAdapter(new FakeAdapter(analyzer));

        assertTrue(svc.indexDirectory(indexId, dir.toAbsolutePath().toString(), null) >= 1,
                "initial index must contain the " + EXT + " file");
        String hashV1 = fileHash(indexId);
        assertNotNull(hashV1, "initial fingerprint must be persisted");
        String vfsPath = "file:" + dir.toAbsolutePath().toString().replace('\\', '/');

        analyzer.mode = failureMode;
        Files.writeString(file, "v2-changed-content");

        int changed = svc.triggerIncrementalIndex(indexId, vfsPath, null);
        assertEquals(0, changed, "a failed analysis must not be counted as a successfully indexed file");
        assertEquals(hashV1, fileHash(indexId),
                "the failed file must keep its previous fingerprint (not marked as synced)");

        analyzer.mode = Mode.OK;
        int retried = svc.triggerIncrementalIndex(indexId, vfsPath, null);
        assertTrue(retried >= 1, "the next incremental run must retry the previously failed file");
        assertNotEquals(hashV1, fileHash(indexId), "the retry must persist the new content fingerprint");
    }

    @Test
    void analyzerExceptionKeepsFingerprintAndIsRetried() throws Exception {
        runRetentionScenario("idx-fail-throw", Mode.THROW);
    }

    @Test
    void analyzerNullResultKeepsFingerprintAndIsRetried() throws Exception {
        runRetentionScenario("idx-fail-null", Mode.NULL);
    }
}
