package io.nop.search;

import io.nop.api.core.annotations.autotest.NopTestProperty;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.search.api.ISearchEngine;
import io.nop.search.api.SearchRequest;
import io.nop.search.api.SearchResponse;
import io.nop.search.api.SearchableDoc;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@NopTestProperty(name="nop.search.index-dir",value="./target")
public class TestLuceneSearchEngine extends JunitBaseTestCase {

    @Inject
    ISearchEngine searchEngine;

    @Test
    public void testSearch() {
        searchEngine.removeTopic("test");

        SearchableDoc doc = new SearchableDoc();
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
}
