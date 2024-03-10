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
import java.util.concurrent.CompletionStage;

public interface ISearchEngine {
    String DEFAULT_TOPIC = "default";

    SearchResponse search(@Name("request") SearchRequest request);

    default void addDoc(@Name("topic") String topic, @Name("doc") SearchableDoc doc) {
        addDocs(topic, Collections.singletonList(doc));
    }

    void addDocs(@Name("topic") String topic, @Name("docs") List<SearchableDoc> docs);

    void removeDocs(@Name("topic") String topic, @Name("names") List<String> names);

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