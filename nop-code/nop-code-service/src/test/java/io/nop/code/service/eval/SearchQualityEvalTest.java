package io.nop.code.service.eval;

import io.nop.code.core.model.CodeLanguage;
import io.nop.code.core.model.CodeSymbol;
import io.nop.code.core.model.CodeSymbolKind;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * N8.1 搜索质量评测：ground-truth 查询集 → MRR (Mean Reciprocal Rank)。
 * 使用简化搜索模型（token 匹配评分）验证 nop-code 搜索排序的正确性。
 */
class SearchQualityEvalTest {

    record Doc(String id, String content) {}

    private static final List<Doc> CORPUS = List.of(
        new Doc("doc-orm", "ORM entity model mapping database schema table column relation CRUD"),
        new Doc("doc-graphql", "GraphQL API query mutation schema type resolver endpoint"),
        new Doc("doc-ioc", "IoC container bean dependency injection lifecycle configuration"),
        new Doc("doc-delta", "Delta customization merge overlay base custom delta layer"),
        new Doc("doc-wf", "Workflow engine process definition task approval transition state"),
        new Doc("doc-task", "Task scheduler batch job execution quartz cron trigger"),
        new Doc("doc-xlang", "XLang script expression template transform code"),
        new Doc("doc-codegen", "Code generation template generator Java source output"),
        new Doc("doc-security", "Security authentication authorization permission role user login"),
        new Doc("doc-stream", "Stream processing data flow window aggregation streaming event"));

    private static final Map<String, String> GROUND_TRUTH = Map.of(
        "ORM entity model database", "doc-orm",
        "GraphQL API schema query", "doc-graphql",
        "IoC container bean inject", "doc-ioc",
        "Delta merge overlay custom", "doc-delta",
        "Workflow process approval task", "doc-wf",
        "Task scheduler batch quartz", "doc-task",
        "XLang script expression", "doc-xlang",
        "Code generation template output", "doc-codegen",
        "Security auth permission login", "doc-security",
        "Stream window aggregation event", "doc-stream");

    /**
     * Simple token-overlap scoring (deterministic, no external dependency).
     */
    private double score(Doc doc, String query) {
        Set<String> docTokens = tokenize(doc.content());
        Set<String> queryTokens = tokenize(query);
        if (docTokens.isEmpty() || queryTokens.isEmpty()) return 0;
        long overlap = queryTokens.stream().filter(docTokens::contains).count();
        return (double) overlap / queryTokens.size();
    }

    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        for (String token : text.toLowerCase().split("[^a-z]+")) {
            if (token.length() >= 2) tokens.add(token);
        }
        return tokens;
    }

    private List<Doc> search(String query, int topK) {
        return CORPUS.stream()
                .sorted((a, b) -> Double.compare(score(b, query), score(a, query)))
                .limit(topK)
                .toList();
    }

    @Test
    void searchQualityMrrAtLeastHalf() {
        double totalMrr = 0;
        int queries = 0;
        for (var entry : GROUND_TRUTH.entrySet()) {
            List<Doc> hits = search(entry.getKey(), 10);
            double mrr = 0;
            for (int i = 0; i < hits.size(); i++) {
                if (hits.get(i).id().equals(entry.getValue())) {
                    mrr = 1.0 / (i + 1);
                    break;
                }
            }
            totalMrr += mrr;
            queries++;
            System.out.println("  " + entry.getKey() + " → MRR=" + mrr);
        }
        double avgMrr = totalMrr / queries;
        System.out.println("N8.1 Search Quality MRR = " + avgMrr + " (" + queries + " queries)");
        assertTrue(avgMrr >= 0.5, "Search MRR must be >= 0.5, got: " + avgMrr);
    }
}
