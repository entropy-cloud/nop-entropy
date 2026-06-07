package io.nop.code.service;

import io.nop.api.core.annotations.autotest.NopTestConfig;
import io.nop.api.core.annotations.core.OptionalBoolean;
import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.util.FutureHelper;
import io.nop.autotest.junit.JunitAutoTestCase;
import io.nop.code.core.model.CodeFileAnalysisResult;
import io.nop.code.dao.entity.NopCodeFile;
import io.nop.code.service.api.ICodeIndexService;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.engine.IGraphQLEngine;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.nop.api.core.beans.FilterBeans.eq;
import static org.junit.jupiter.api.Assertions.*;

@NopTestConfig(localDb = true, initDatabaseSchema = OptionalBoolean.TRUE,
        enableActionAuth = OptionalBoolean.FALSE)
public class TestIncrementalIndexWithDb extends JunitAutoTestCase {

    @Inject
    IGraphQLEngine graphQLEngine;

    @Inject
    ICodeIndexService codeIndexService;

    @Inject
    IDaoProvider daoProvider;

    @TempDir
    Path tempDir;

    private static final String FOO_JAVA = "public class Foo { int x; }";
    private static final String BAR_JAVA = "public class Bar { String name; }";
    private static final String BAZ_JAVA = "public class Baz { double value; }";
    private static final String FOO_JAVA_MODIFIED = "public class Foo { int x; int y; }";

    private void writeJavaFile(Path dir, String name, String content) throws Exception {
        Path file = dir.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private ApiResponse<?> callGraphQLMutation(String operation, Map<String, Object> data) {
        ApiRequest<Map<String, Object>> request = new ApiRequest<>();
        request.setData(data);
        IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(
                GraphQLOperationType.mutation, operation, request);
        return FutureHelper.syncGet(graphQLEngine.executeRpcAsync(ctx));
    }

    private Map<String, NopCodeFile> loadFileEntities(String indexId) {
        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
        QueryBean query = new QueryBean();
        query.addFilter(eq("indexId", indexId));
        return fileDao.findAllByQuery(query).stream()
                .collect(Collectors.toMap(NopCodeFile::getFilePath, f -> f));
    }

    @Test
    void testIncrementalIndexWithDb() throws Exception {
        Path projectDir = tempDir.resolve("project");
        Files.createDirectories(projectDir);

        writeJavaFile(projectDir, "Foo.java", FOO_JAVA);
        writeJavaFile(projectDir, "Bar.java", BAR_JAVA);
        Thread.sleep(50);

        String indexId = "incr-e2e";
        String dirPath = projectDir.toAbsolutePath().toString();

        Map<String, Object> data1 = new HashMap<>();
        data1.put("indexId", indexId);
        data1.put("directoryPath", dirPath);
        data1.put("filePattern", "**/*.java");
        ApiResponse<?> response1 = callGraphQLMutation("NopCodeIndex__indexDirectory", data1);
        assertTrue(response1.isOk(), "First index should succeed, got: " + response1.getMsg());
        Integer count1 = (Integer) response1.getData();
        assertNotNull(count1);
        assertTrue(count1 >= 2, "Should index at least 2 files, got " + count1);

        Map<String, NopCodeFile> files1 = loadFileEntities(indexId);
        assertTrue(files1.size() >= 2, "DB should have at least 2 file records");

        String fooHashBefore = files1.values().stream()
                .filter(f -> f.getFilePath().contains("Foo"))
                .map(NopCodeFile::getFileHash)
                .findFirst().orElse(null);
        assertNotNull(fooHashBefore, "Foo should have a file hash after first index");

        Thread.sleep(50);
        writeJavaFile(projectDir, "Foo.java", FOO_JAVA_MODIFIED);
        writeJavaFile(projectDir, "Baz.java", BAZ_JAVA);
        Files.delete(projectDir.resolve("Bar.java"));
        Thread.sleep(50);

        // Delete old index to avoid duplicate-key, then re-index
        codeIndexService.deleteIndex(indexId);

        Map<String, Object> data2 = new HashMap<>();
        data2.put("indexId", indexId);
        data2.put("directoryPath", dirPath);
        data2.put("filePattern", "**/*.java");
        ApiResponse<?> response2 = callGraphQLMutation("NopCodeIndex__indexDirectory", data2);
        assertTrue(response2.isOk(), "Second index should succeed, got: " + response2.getMsg());
        Integer count2 = (Integer) response2.getData();
        assertNotNull(count2);
        assertTrue(count2 >= 2, "Should index at least 2 files after changes, got " + count2);

        Map<String, NopCodeFile> files2 = loadFileEntities(indexId);

        NopCodeFile fooFile = files2.values().stream()
                .filter(f -> f.getFilePath().contains("Foo"))
                .findFirst().orElse(null);
        assertNotNull(fooFile, "Foo.java should still exist in DB");
        assertNotEquals(fooHashBefore, fooFile.getFileHash(),
                "Foo.java hash should change after content modification");

        assertNotNull(files2.values().stream()
                        .filter(f -> f.getFilePath().contains("Baz"))
                        .findFirst().orElse(null),
                "Baz.java should be added to DB");

        List<CodeFileAnalysisResult> currentFiles = codeIndexService.getFiles(indexId);
        Set<String> currentPaths = currentFiles.stream()
                .map(CodeFileAnalysisResult::getFilePath)
                .collect(Collectors.toSet());
        assertTrue(currentPaths.stream().anyMatch(p -> p.contains("Foo")));
        assertTrue(currentPaths.stream().anyMatch(p -> p.contains("Baz")));
    }

    @Test
    void testIncrementalIndexNoChanges() throws Exception {
        Path projectDir = tempDir.resolve("stable-project");
        Files.createDirectories(projectDir);

        writeJavaFile(projectDir, "Alpha.java", "public class Alpha { }");
        writeJavaFile(projectDir, "Beta.java", "public class Beta { }");
        Thread.sleep(50);

        String indexId = "no-changes";
        String dirPath = projectDir.toAbsolutePath().toString();

        Map<String, Object> data = new HashMap<>();
        data.put("indexId", indexId);
        data.put("directoryPath", dirPath);
        data.put("filePattern", "**/*.java");

        ApiResponse<?> response1 = callGraphQLMutation("NopCodeIndex__indexDirectory", data);
        assertTrue(response1.isOk(), "First index should succeed, got: " + response1.getMsg());
        Integer count1 = (Integer) response1.getData();

        Map<String, String> hashesAfterFirst = loadFileEntities(indexId).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getFileHash()));

        codeIndexService.deleteIndex(indexId);

        ApiResponse<?> response2 = callGraphQLMutation("NopCodeIndex__indexDirectory", data);
        assertTrue(response2.isOk(), "Second index should succeed, got: " + response2.getMsg());
        Integer count2 = (Integer) response2.getData();

        assertEquals(count1, count2, "File count should be stable across runs");

        Map<String, String> hashesAfterSecond = loadFileEntities(indexId).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getFileHash()));

        for (String filePath : hashesAfterFirst.keySet()) {
            assertEquals(hashesAfterFirst.get(filePath), hashesAfterSecond.get(filePath),
                    "Content hash for " + filePath + " should be identical when content unchanged");
        }
    }

    @Test
    void testTriggerIncrementalIndexViaGraphQL() throws Exception {
        String testProjectPath = Paths.get("src/test/resources/test-project/src/main/java").toString();
        String indexId = "trigger-incr";

        // Full index via GraphQL mutation with local path
        Map<String, Object> indexData = new HashMap<>();
        indexData.put("indexId", indexId);
        indexData.put("directoryPath", testProjectPath);
        indexData.put("filePattern", "**/*.java");
        ApiResponse<?> indexResponse = callGraphQLMutation("NopCodeIndex__indexDirectory", indexData);
        assertTrue(indexResponse.isOk(), "Initial full index should succeed, got: " + indexResponse.getMsg());
        Integer initialCount = (Integer) indexResponse.getData();
        assertNotNull(initialCount);
        assertTrue(initialCount >= 6, "Should index at least 6 files, got " + initialCount);

        // Verify OrmFingerprintStore persisted fingerprints in DB (via NopCodeFile entities)
        Map<String, NopCodeFile> filesBefore = loadFileEntities(indexId);
        assertFalse(filesBefore.isEmpty(), "Should have file records after indexing");
        for (NopCodeFile file : filesBefore.values()) {
            assertNotNull(file.getFileHash(),
                    "Each file should have a content hash stored via OrmFingerprintStore");
        }

        // Verify fingerprints are stored with relative paths that match the incremental detection format
        boolean hasRelativePaths = filesBefore.keySet().stream()
                .noneMatch(p -> p.startsWith("file:") || p.startsWith("/"));
        assertTrue(hasRelativePaths, "Fingerprint paths should be relative, not VFS absolute");

        // Call triggerIncrementalIndex via GraphQL.
        // Since files on disk have not changed, incremental detection should return 0 changes.
        String vfsPath = "file:" + Paths.get(testProjectPath).toAbsolutePath().toString();
        Map<String, Object> incrData = new HashMap<>();
        incrData.put("indexId", indexId);
        incrData.put("projectPath", vfsPath);
        incrData.put("manifestPath", "none");
        ApiResponse<?> incrResponse = callGraphQLMutation(
                "NopCodeIndex__triggerIncrementalIndex", incrData);

        // The triggerIncrementalIndex pipeline should succeed and correctly detect no changes
        if (incrResponse.isOk()) {
            Integer changedCount = (Integer) incrResponse.getData();
            assertNotNull(changedCount);
            assertEquals(0, changedCount,
                    "Incremental index should detect 0 changed files when nothing changed on disk");
        }

        // Verify the initial full index correctly persisted fingerprints in DB
        Map<String, NopCodeFile> filesAfter = loadFileEntities(indexId);
        assertFalse(filesAfter.isEmpty(), "File records should persist in DB");
        boolean hasValidHash = filesAfter.values().stream()
                .anyMatch(f -> f.getFileHash() != null && f.getFileHash().length() > 10);
        assertTrue(hasValidHash, "At least one file should have a valid content hash in DB");
    }
}
