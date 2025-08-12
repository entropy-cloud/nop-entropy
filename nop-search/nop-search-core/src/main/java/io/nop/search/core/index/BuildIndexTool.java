package io.nop.search.core.index;

import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.Guard;
import io.nop.commons.batch.BatchQueue;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.search.api.ISearchEngine;
import io.nop.search.api.SearchableDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.FileVisitResult;
import java.util.List;
import java.util.function.BiPredicate;

public class BuildIndexTool {
    static final Logger LOG = LoggerFactory.getLogger(BuildIndexTool.class);

    public static final List<String> DEFAULT_INDEXABLE_FILE_EXTS = List.of("java", "md", "yaml", "yml", "json5", "xml", "json");

    private final ISearchEngine searchEngine;

    private ISearchableDocEnhancer searchableDocEnhancer;

    public BuildIndexTool(ISearchEngine searchEngine) {
        this.searchEngine = searchEngine;
    }

    public void setSearchableDocEnhancer(ISearchableDocEnhancer enhancer) {
        this.searchableDocEnhancer = enhancer;
    }

    public void indexAll(String topic, File dir) {
        indexAll(topic, dir, BuildIndexTool::isDefaultIndexable);
    }

    public static boolean isDefaultIndexable(String path, File file) {
        String fileName = file.getName();
        String fileExt = StringHelper.fileExt(fileName);
        return DEFAULT_INDEXABLE_FILE_EXTS.contains(fileExt);
    }

    public void indexAll(String topic, File dir, BiPredicate<String, File> filter) {
        Guard.notNull(filter, "filter");

        File absDir = FileHelper.getAbsoluteFile(dir);
        BatchQueue<SearchableDoc> queue = new BatchQueue<>(3000, docs -> searchEngine.addDocs(topic, docs));
        FileHelper.walk(absDir, file -> {
            if (isInIgnoreList(file))
                return FileVisitResult.SKIP_SUBTREE;

            String path = FileHelper.getRelativePath(absDir, file);

            if (file.isFile() && filter.test(path, file)) {
                try {
                    SearchableDoc doc = new SearchableDoc();
                    doc.setId(StringHelper.md5Hash(path));
                    doc.setPath(path);
                    doc.setName(file.getName());
                    doc.setTitle(file.getName());
                    doc.setContent(FileHelper.readText(file, null));
                    doc.setStoreContent(true);
                    doc.setFileSize(file.length());
                    doc.setModifyTime(file.lastModified());
                    doc.setPublishTime(CoreMetrics.currentTimeMillis());

                    if (searchableDocEnhancer != null)
                        searchableDocEnhancer.enhance(absDir, doc);

                    queue.add(doc);
                } catch (Exception e) {
                    LOG.warn("nop.search.index-doc-fail:topic={},path={}", topic, file.getPath(), e);
                }
            }
            return FileVisitResult.CONTINUE;
        });
        queue.flush();
    }

    protected boolean isInIgnoreList(File file) {
        String fileName = file.getName();
        return fileName.startsWith(".");
    }
}
