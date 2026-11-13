/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.search.api;

import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.util.FutureHelper;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Search engine abstraction (plan 2563 MA-01 thread contract declaration).
 *
 * <p><b>Thread contract</b>: implementations must coordinate per-topic concurrent access such
 * that
 * <ul>
 * <li>writes on the same topic ({@code addDocs}/{@code removeDocs}/{@code removeTopic}) are
 * serialized per topic;</li>
 * <li>read spans (acquire..release of a topic's searcher) exclude topic removal: after
 * {@code removeTopic} or a failed-batch {@code rollback} closes a topic's SearcherManager,
 * no in-flight read may observe {@code AlreadyClosedException};</li>
 * <li>after {@code destroy}, further access on any topic is rejected with a typed error
 * instead of silently rebuilding index resources.</li>
 * </ul>
 */
public interface ISearchEngine {
    String DEFAULT_TOPIC = "default";

    SearchResponse search(@Name("request") SearchRequest request);

    /**
     * P1-4.4-S-05（plan 2394）：增加 topic 参数限定查找范围——docId 是相对路径 md5，
     * 不同 topic 会产生相同 docId；无 topic 的全量遍历取首个命中即跨 topic 串读。
     */
    SearchableDoc getDoc(@Name("topic") String topic, @Name("docId") String docId);

    List<SearchableDoc> getDocsByTerm(String topic, String term);

    Map<String, List<String>> analyzeDoc(SearchableDoc doc);

    List<String> analyzeQuery(String query);

    void refreshBlocking(String topic);

    default void addDoc(@Name("topic") String topic, @Name("doc") SearchableDoc doc) {
        addDocs(topic, Collections.singletonList(doc));
    }

    void addDocs(@Name("topic") String topic, @Name("docs") List<SearchableDoc> docs);

    void removeDocs(@Name("topic") String topic, @Name("docIds") List<String> dodIds);

    void removeTopic(@Name("topic") String topic);

    default CompletionStage<SearchResponse> searchAsync(@Name("request") SearchRequest request) {
        return FutureHelper.futureCall(() -> search(request));
    }

    default CompletionStage<Void> addDocAsync(@Name("topic") String topic, @Name("doc") SearchableDoc doc) {
        return FutureHelper.futureRun(() -> addDoc(topic, doc));
    }

    default CompletionStage<Void> addDocsAsync(@Name("topic") String topic, @Name("docs") List<SearchableDoc> docs) {
        return FutureHelper.futureRun(() -> addDocs(topic, docs));
    }
}