/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.search;

import io.nop.api.core.annotations.autotest.NopTestProperty;
import io.nop.api.core.time.CoreMetrics;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.commons.batch.BatchQueue;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.search.api.ISearchEngine;
import io.nop.search.api.SearchRequest;
import io.nop.search.api.SearchResponse;
import io.nop.search.api.SearchableDoc;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.FileVisitResult;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@NopTestProperty(name = "nop.search.index-dir", value = "./target")
public class TestLuceneSearchEngine extends JunitBaseTestCase {

    @Inject
    ISearchEngine searchEngine;

    @Test
    public void testSearch() {
        searchEngine.removeTopic("test");

        SearchableDoc doc = new SearchableDoc();
        doc.setId("123");
        doc.setName("123");
        doc.setTitle("测试标题");
        doc.setContent("测试内容");
        doc.setStoreContent(true);

        searchEngine.addDoc("test", doc);

        SearchRequest request = new SearchRequest();
        request.setTopic("test");
        request.setQuery("测试");
        request.setLimit(100);
        SearchResponse ret = searchEngine.search(request);
        assertEquals(1, ret.getTotal());
        assertEquals("<B>测</B><B>试</B>标题", ret.getItems().get(0).getTitle());
        searchEngine.removeTopic("test");
    }

    @Disabled
    @Test
    public void indexAll() {
        File dir = new File(getModuleDir(), "../..");
        BatchQueue<SearchableDoc> queue = new BatchQueue<>(3000, docs -> searchEngine.addDocs("nop", docs));
        FileHelper.walk(dir, file -> {
            if (file.getName().endsWith("tests"))
                return FileVisitResult.SKIP_SUBTREE;

            String path = file.getPath().substring(dir.getPath().length()).replace('\\', '/');

            if (file.getName().endsWith(".java")) {
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

                    queue.add(doc);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return FileVisitResult.CONTINUE;
        });
        queue.flush();
        searchEngine.refreshBlocking("nop");

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        SearchRequest request = new SearchRequest();
        request.setQuery("graphqldocument");
        request.setLimit(1000);
        request.setTopic("nop");
        SearchResponse response = searchEngine.search(request);
        System.out.println(JsonTool.serialize(response, true));

        String docPath = "/nop-converter/src/main/java/io/nop/converter/impl/DslDocumentConverter.java";
        SearchableDoc doc = searchEngine.getDoc(StringHelper.md5Hash(docPath));
        System.out.println(JsonTool.serialize(doc, true));

        Map<String, List<String>> tokens = searchEngine.analyzeDoc(doc);
        System.out.println("tokens="+JsonTool.serialize(tokens, true));

        List<String> queryTokens = searchEngine.analyzeQuery("ResourceComponentManager");
        System.out.println(queryTokens);

        List<SearchableDoc> docs = searchEngine.getDocsByTerm("nop", "ResourceComponentManager");
        System.out.println(JsonTool.serialize(docs, true));
    }
}