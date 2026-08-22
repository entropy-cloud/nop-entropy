/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.search;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.search.lucene.LuceneFilterBeanTransformer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import static io.nop.search.api.SearchConstants.FIELD_CONTENT;
import static io.nop.search.api.SearchConstants.FIELD_FILE_SIZE;
import static io.nop.search.api.SearchConstants.FIELD_ID;
import static io.nop.search.api.SearchConstants.FIELD_PATH;
import static io.nop.search.api.SearchConstants.FIELD_PUBLISH_TIME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 数值/路径字段 filter 查询构造的回归测试（内存索引）。
 * 索引结构与 LuceneSearchEngine.buildDocument 保持一致：
 * 数值字段以 LongPoint 建索引（addNumericField），path 仅 StoredField 不建索引。
 */
public class TestLuceneFilterBeanTransformer {
    private Directory directory;
    private DirectoryReader reader;
    private IndexSearcher searcher;

    @BeforeEach
    public void setUp() throws IOException {
        directory = new ByteBuffersDirectory();
        IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(new StandardAnalyzer()));
        addDoc(writer, "a", 100, 1000, "/a.txt");
        addDoc(writer, "b", 200, 2000, "/b.txt");
        addDoc(writer, "c", 300, 3000, "/c.txt");
        writer.close();

        reader = DirectoryReader.open(directory);
        searcher = new IndexSearcher(reader);
    }

    @AfterEach
    public void tearDown() throws IOException {
        reader.close();
        directory.close();
    }

    private void addDoc(IndexWriter writer, String id, long fileSize, long publishTime, String path)
            throws IOException {
        Document doc = new Document();
        doc.add(new StringField(FIELD_ID, id, Field.Store.YES));
        doc.add(new TextField(FIELD_CONTENT, "common text " + id, Field.Store.YES));
        // 与 LuceneSearchEngine.addNumericField 一致: LongPoint + StoredField
        doc.add(new LongPoint(FIELD_FILE_SIZE, fileSize));
        doc.add(new StoredField(FIELD_FILE_SIZE, fileSize));
        doc.add(new LongPoint(FIELD_PUBLISH_TIME, publishTime));
        doc.add(new StoredField(FIELD_PUBLISH_TIME, publishTime));
        // 与 LuceneSearchEngine.buildDocument 一致: path 仅 StoredField，无索引项
        doc.add(new StoredField(FIELD_PATH, path));
        writer.addDocument(doc);
    }

    private Set<String> filter(TreeBean filterBean) throws IOException {
        Query query = new LuceneFilterBeanTransformer().visit(filterBean, DisabledEvalScope.INSTANCE);
        Set<String> ids = new TreeSet<>();
        for (ScoreDoc scoreDoc : searcher.search(query, 10).scoreDocs) {
            ids.add(searcher.storedFields().document(scoreDoc.doc).get(FIELD_ID));
        }
        return ids;
    }

    private static Set<String> ids(String... values) {
        return new TreeSet<>(Arrays.asList(values));
    }

    @Test
    public void testNumericGtExcludesBoundary() throws IOException {
        assertEquals(ids("c"), filter(FilterBeans.gt(FIELD_FILE_SIZE, 200)));
        assertEquals(ids("c"), filter(FilterBeans.gt(FIELD_PUBLISH_TIME, 2000)));
    }

    @Test
    public void testNumericGeIncludesBoundary() throws IOException {
        assertEquals(ids("b", "c"), filter(FilterBeans.ge(FIELD_FILE_SIZE, 200)));
    }

    @Test
    public void testNumericLtExcludesBoundary() throws IOException {
        assertEquals(ids("a"), filter(FilterBeans.lt(FIELD_FILE_SIZE, 200)));
    }

    @Test
    public void testNumericLeIncludesBoundary() throws IOException {
        assertEquals(ids("a", "b"), filter(FilterBeans.le(FIELD_FILE_SIZE, 200)));
    }

    @Test
    public void testNumericEqOnLongPointField() throws IOException {
        assertEquals(ids("b"), filter(FilterBeans.eq(FIELD_FILE_SIZE, 200)));
    }

    @Test
    public void testNumericNeOnLongPointField() throws IOException {
        assertEquals(ids("a", "c"), filter(FilterBeans.ne(FIELD_FILE_SIZE, 200)));
    }

    @Test
    public void testNumericBetweenInclusive() throws IOException {
        assertEquals(ids("a", "b", "c"), filter(FilterBeans.between(FIELD_FILE_SIZE, 100, 300)));
        assertEquals(ids("b"), filter(FilterBeans.between(FIELD_FILE_SIZE, 150, 250)));
    }

    @Test
    public void testNumericBetweenExcludeMin() throws IOException {
        assertEquals(ids("b", "c"), filter(FilterBeans.between(FIELD_FILE_SIZE, 100, 300, true, false)));
    }

    @Test
    public void testPathFilterRejected() {
        NopException ex = assertThrows(NopException.class,
                () -> filter(FilterBeans.eq(FIELD_PATH, "/a.txt")));
        assertEquals(FIELD_PATH, ex.getParam("field"));

        assertThrows(NopException.class, () -> filter(FilterBeans.ne(FIELD_PATH, "/a.txt")));
        assertThrows(NopException.class, () -> filter(FilterBeans.between(FIELD_PATH, "/a.txt", "/c.txt")));
    }
}
