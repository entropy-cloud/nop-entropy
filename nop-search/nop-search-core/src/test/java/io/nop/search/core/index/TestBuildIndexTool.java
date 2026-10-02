package io.nop.search.core.index;

import io.nop.commons.util.StringHelper;
import io.nop.search.api.ISearchEngine;
import io.nop.search.api.SearchRequest;
import io.nop.search.api.SearchResponse;
import io.nop.search.api.SearchableDoc;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WI12 small-module coverage: BuildIndexTool walks a directory and feeds
 * SearchableDocs to the search engine in batch — the default indexable-file
 * filter, the hidden-directory skip, relative-path based doc ids, and the
 * optional doc enhancer hook. The engine is a recording stub (no mockito).
 */
public class TestBuildIndexTool {

    /** Recording ISearchEngine stub: collects batched docs, never touches I/O. */
    static final class RecordingSearchEngine implements ISearchEngine {
        final List<SearchableDoc> added = new ArrayList<>();
        final List<String> addTopics = new ArrayList<>();
        int addDocsCalls;

        @Override
        public SearchResponse search(SearchRequest request) {
            return null;
        }

        @Override
        public SearchableDoc getDoc(String docId) {
            return null;
        }

        @Override
        public List<SearchableDoc> getDocsByTerm(String topic, String term) {
            return Collections.emptyList();
        }

        @Override
        public Map<String, List<String>> analyzeDoc(SearchableDoc doc) {
            return new HashMap<>();
        }

        @Override
        public List<String> analyzeQuery(String query) {
            return Collections.emptyList();
        }

        @Override
        public void refreshBlocking(String topic) {
            // no-op
        }

        @Override
        public void addDocs(String topic, List<SearchableDoc> docs) {
            addDocsCalls++;
            addTopics.add(topic);
            added.addAll(docs);
        }

        @Override
        public void removeDocs(String topic, List<String> docIds) {
            // no-op
        }

        @Override
        public void removeTopic(String topic) {
            // no-op
        }
    }

    @Test
    public void testDefaultIndexableFilterMatchesSourceAndDocExtensionsOnly(@TempDir Path dir)
            throws Exception {
        // NOTE(product finding): isDefaultIndexable keys on the File's own
        // extension (file.getName()) — the path argument is ignored, so the
        // predicate must receive a real File handle.
        File javaFile = writeFile(dir.resolve("A.java"), "class A {}");
        File exeFile = writeFile(dir.resolve("app.exe"), "binary");
        File noExtFile = writeFile(dir.resolve("noext"), "data");
        File txtFile = writeFile(dir.resolve("x.txt"), "text");

        assertTrue(BuildIndexTool.isDefaultIndexable("a.java", javaFile));
        assertTrue(BuildIndexTool.isDefaultIndexable("docs/readme.md", javaFile),
                "any markdown extension is indexable");
        assertTrue(BuildIndexTool.isDefaultIndexable("x.yaml", javaFile));
        assertTrue(BuildIndexTool.isDefaultIndexable("x.yml", javaFile));
        assertTrue(BuildIndexTool.isDefaultIndexable("x.json5", javaFile));
        assertTrue(BuildIndexTool.isDefaultIndexable("x.xml", javaFile));
        assertTrue(BuildIndexTool.isDefaultIndexable("x.json", javaFile));
        assertFalse(BuildIndexTool.isDefaultIndexable("app.exe", exeFile), "unlisted extensions are excluded");
        assertFalse(BuildIndexTool.isDefaultIndexable("noext", noExtFile), "extensionless files are excluded");
        assertFalse(BuildIndexTool.isDefaultIndexable("x.txt", txtFile), "plain text is excluded");
    }

    @Test
    public void testIndexAllCollectsIndexableDocsAndSkipsHiddenDirectories(@TempDir Path dir)
            throws Exception {
        writeFile(dir.resolve("Root.java"), "class Root {}");
        writeFile(dir.resolve("notes.md"), "# notes");
        writeFile(dir.resolve("skip-me.txt"), "not indexable");
        writeFile(dir.resolve(".git").resolve("config"), "ignored=.yaml");

        RecordingSearchEngine engine = new RecordingSearchEngine();
        BuildIndexTool tool = new BuildIndexTool(engine);
        tool.indexAll("my-topic", dir.toFile());

        assertEquals(2, engine.added.size(), "only default-indexable files outside hidden dirs are indexed");
        assertEquals(1, engine.addDocsCalls, "batched docs are flushed exactly once via queue.flush()");
        assertTrue(engine.addTopics.stream().allMatch("my-topic"::equals), "topic is forwarded on every batch");

        Set<String> paths = engine.added.stream().map(SearchableDoc::getPath)
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(paths.contains("Root.java"));
        assertTrue(paths.contains("notes.md"));

        for (SearchableDoc doc : engine.added) {
            assertEquals(StringHelper.md5Hash(doc.getPath()), doc.getId(),
                    "doc id is the md5 of the relative path");
            assertTrue(doc.isStoreContent(), "content is stored inline");
            assertTrue(doc.getFileSize() > 0, "file size is recorded");
            assertTrue(doc.getModifyTime() > 0, "modify time is recorded");
            assertEquals(doc.getName(), doc.getTitle(), "title defaults to the file name");
        }

        SearchableDoc root = engine.added.stream()
                .filter(d -> d.getPath().equals("Root.java")).findFirst().orElseThrow();
        assertEquals("class Root {}", root.getContent(), "file content is read into the doc");
    }

    @Test
    public void testIndexAllAppliesEnhancerHook(@TempDir Path dir) throws Exception {
        writeFile(dir.resolve("a.java"), "class A {}");

        RecordingSearchEngine engine = new RecordingSearchEngine();
        BuildIndexTool tool = new BuildIndexTool(engine);
        tool.setSearchableDocEnhancer((baseDir, doc) -> doc.setSummary("enhanced:" + doc.getName()));
        tool.indexAll("topic", dir.toFile());

        assertEquals(1, engine.added.size());
        assertEquals("enhanced:a.java", engine.added.get(0).getSummary(),
                "the enhancer sees every doc before batching");
    }

    @Test
    public void testIndexAllHonorsCustomFilter(@TempDir Path dir) throws Exception {
        writeFile(dir.resolve("a.java"), "class A {}");
        writeFile(dir.resolve("only-me.custom"), "special");

        RecordingSearchEngine engine = new RecordingSearchEngine();
        BuildIndexTool tool = new BuildIndexTool(engine);
        BiPredicate<String, File> onlyCustom = (path, file) -> path.endsWith(".custom");
        tool.indexAll("topic", dir.toFile(), onlyCustom);

        assertEquals(1, engine.added.size(), "custom filter replaces the default extension filter");
        assertEquals("only-me.custom", engine.added.get(0).getPath());
    }

    @Test
    public void testIndexAllOnEmptyDirectoryFlushesWithoutBatching(@TempDir Path dir) {
        RecordingSearchEngine engine = new RecordingSearchEngine();
        BuildIndexTool tool = new BuildIndexTool(engine);
        tool.indexAll("topic", dir.toFile());

        assertEquals(0, engine.addDocsCalls, "nothing to index means no addDocs call");
        assertTrue(engine.added.isEmpty());
    }

    private static File writeFile(Path target, String content) throws Exception {
        Files.createDirectories(target.getParent());
        Files.write(target, content.getBytes(StandardCharsets.UTF_8));
        return target.toFile();
    }
}
