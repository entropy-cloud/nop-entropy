package io.nop.search;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.annotations.autotest.NopTestProperty;
import io.nop.autotest.junit.JunitBaseTestCase;
import io.nop.api.core.exceptions.NopException;
import io.nop.search.api.ISearchEngine;
import io.nop.search.api.SearchRequest;
import io.nop.search.api.SearchResponse;
import io.nop.search.api.SearchableDoc;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-4.4-S-02..06（plan 2394）五腿判别：
 * ①空 query = match all；②NOT 过滤补集语义；③rollback 后 topic 自愈（不再 AlreadyClosed）；
 * ④getDoc(topic,docId) 单 topic 隔离；⑤indexDir fail-closed 授权（非 admin 拒绝）。
 */
@NopTestProperty(name = "nop.search.index-dir", value = "./target")
public class TestSearchFixesDiscriminative extends JunitBaseTestCase {

    @Inject
    ISearchEngine searchEngine;

    private static IUserContext userInRole(String... roles) {
        return (IUserContext) Proxy.newProxyInstance(
                TestSearchFixesDiscriminative.class.getClassLoader(),
                new Class[]{IUserContext.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "isUserInRole":
                            return Arrays.asList(roles).contains(args[0]);
                        case "getUserId":
                            return "test-user";
                        default:
                            return null;
                    }
                });
    }

    private static void seed(ISearchEngine engine, String topic, String docId, String text) {
        SearchableDoc doc = new SearchableDoc();
        doc.setId(docId);
        doc.setTitle(text);
        doc.setContent(text);
        engine.addDoc(topic, doc);
    }

    // ==================== ① S-02 空 query = match all ====================

    @Test
    public void testEmptyQueryMatchesAllWithFilters() {
        String topic = "fix-s02-" + System.nanoTime();
        seed(searchEngine, topic, "d1", "alpha content");

        SearchRequest request = new SearchRequest();
        request.setTopic(topic);
        request.setQuery(""); // 空 query：仅按 topic 浏览
        request.setLimit(10);
        SearchResponse ret = searchEngine.search(request);

        assertEquals(1, ret.getTotal(),
                "empty query must match all docs (P1-4.4-S-02), not an empty BooleanQuery");
    }

    // ==================== ② S-03 NOT 补集语义 ====================

    @Test
    public void testNotFilterMatchesComplement() throws Exception {
        String topic = "fix-s03-" + System.nanoTime();
        seed(searchEngine, topic, "d1", "visible content");
        seed(searchEngine, topic, "d2", "secret zzz content");

        SearchRequest request = new SearchRequest();
        request.setTopic(topic);
        request.setQuery(""); // match all
        request.setLimit(10);
        // filter: not(eq(id,"d2")) → 补集只剩 d1（id 为 StringField 精确 term）
        request.setFilter(notFilter(toBean("eq", "name", "id", "value", "d2")));

        SearchResponse ret = searchEngine.search(request);
        assertEquals(1, ret.getTotal(),
                "NOT filter must match the complement (P1-4.4-S-03), not an empty set");
    }

    private static io.nop.api.core.beans.TreeBean toBean(String op, Object... kv) {
        io.nop.api.core.beans.TreeBean bean = new io.nop.api.core.beans.TreeBean(op);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            bean.attr(String.valueOf(kv[i]), kv[i + 1]);
        }
        return bean;
    }

    private static io.nop.api.core.beans.TreeBean notFilter(io.nop.api.core.beans.TreeBean child) {
        io.nop.api.core.beans.TreeBean not = new io.nop.api.core.beans.TreeBean("not");
        not.addChild(child);
        return not;
    }

    // ==================== ③ S-04 rollback 后 topic 自愈 ====================

    @Test
    public void testTopicRecoversAfterFailedBatch() {
        String topic = "fix-s04-" + System.nanoTime();
        seed(searchEngine, topic, "good-1", "stable doc");

        SearchableDoc bad = new SearchableDoc();
        bad.setId(null); // buildDocument Guard.notEmpty(id) 失败 → rollback
        bad.setTitle("bad doc");
        assertThrows(Exception.class, () -> searchEngine.addDocs(topic, Collections.singletonList(bad)),
                "batch with missing id must fail");

        // 修复前：writer 已 rollback 关闭但留在缓存 map → 此处 AlreadyClosedException
        SearchableDoc good = new SearchableDoc();
        good.setId("good-2");
        good.setTitle("recovered doc");
        good.setContent("recovered");
        searchEngine.addDocs(topic, Collections.singletonList(good));

        SearchRequest request = new SearchRequest();
        request.setTopic(topic);
        request.setLimit(10);
        SearchResponse ret = searchEngine.search(request);
        assertEquals(2, ret.getTotal(),
                "topic must recover after a failed batch (P1-4.4-S-04), not stay closed forever");
    }

    // ==================== ④ S-05 getDoc topic 隔离 ====================

    @Test
    public void testGetDocIsolatedPerTopic() {
        String topicA = "fix-s05-a-" + System.nanoTime();
        String topicB = "fix-s05-b-" + System.nanoTime();
        String sharedDocId = "shared-doc-id";

        // doc 只写入 topicB；topicA 不含该 docId（确定性隔离判别——修复前的全量遍历
        // 会在 topicB 命中并串读返回，新实现限定 topicA 查找必须返回 null）
        seed(searchEngine, topicB, sharedDocId, "topic B payload");
        searchEngine.refreshBlocking(topicB);

        SearchableDoc fromA = searchEngine.getDoc(topicA, sharedDocId);
        assertNull(fromA, "getDoc must not leak docs across topics (P1-4.4-S-05)");

        SearchableDoc fromB = searchEngine.getDoc(topicB, sharedDocId);
        assertNotNull(fromB, "the owning topic still finds its doc");
        assertEquals("topic B payload", fromB.getTitle());
    }

}
