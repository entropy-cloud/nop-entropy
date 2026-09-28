package io.nop.ai.rag.vector;

import io.nop.ai.api.chat.IChatService;
import io.nop.ai.core.api.document.AiDocument;
import io.nop.ai.core.api.embedding.IEmbeddingModel;
import io.nop.ai.core.api.support.VectorData;
import io.nop.ai.core.api.vectorstore.IVectorStore;
import io.nop.ai.core.api.vectorstore.VectorQueryBean;
import io.nop.ai.core.api.vectorstore.VectorStoreOptions;
import io.nop.ai.rag.ingest.RagIngestService;
import io.nop.ai.rag.search.RagSearchService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * N7.2 GraphRAG integration proof: nop-code GraphWikiExporter community articles
 * (simulated as text) → K3 RagIngestService → RagSearchService. Minimal bridge
 * demonstrating that nop-code graph data can be consumed by the RAG pipeline.
 */
class TestGraphRagIntegration {

    private static final String WIKI_1 = """
            # Community: Nop Core Architecture
            The reversible computation engine transforms software structures
            through delta customization and code generation pipeline.
            """;
    private static final String WIKI_2 = """
            # Community: ORM Layer
            The ORM provides entity-model mapping with automatic CRUD generation
            driving both database schema and GraphQL API surface.
            """;

    static class KeywordEmbedding implements IEmbeddingModel {
        @Override
        public CompletableFuture<VectorData> embedAsync(AiDocument doc,
                io.nop.ai.core.api.embedding.EmbeddingOptions options) {
            return CompletableFuture.completedFuture(embed(doc.getContent()));
        }

        @Override
        public CompletableFuture<List<VectorData>> embedAllAsync(List<AiDocument> docs,
                io.nop.ai.core.api.embedding.EmbeddingOptions options) {
            List<VectorData> result = new java.util.ArrayList<>();
            for (AiDocument doc : docs) result.add(embed(doc.getContent()));
            return CompletableFuture.completedFuture(result);
        }

        static VectorData embed(String text) {
            String lower = text == null ? "" : text.toLowerCase();
            double[] v = new double[16];
            for (String token : lower.split("[^a-z]+")) {
                if (token.length() >= 2) v[Math.floorMod(token.hashCode(), 16)] += 1;
            }
            double norm = 0;
            for (double x : v) norm += x * x;
            norm = Math.sqrt(norm);
            if (norm > 0) for (int i = 0; i < 16; i++) v[i] /= norm;
            VectorData vd = new VectorData();
            vd.setVector(v);
            return vd;
        }
    }

    @Test
    void graphWikiFeedsRagPipeline() {
        IVectorStore<VectorData> store = new InMemoryVectorStore();
        IEmbeddingModel embedding = new KeywordEmbedding();
        RagIngestService ingest = new RagIngestService(
                new io.nop.ai.core.commons.splitter.SimpleTextSplitter(), embedding, store);
        RagSearchService search = new RagSearchService(embedding, store);

        ingest.ingest("graph-wiki", "wiki-core-arch", WIKI_1);
        ingest.ingest("graph-wiki", "wiki-orm", WIKI_2);

        var hits = search.search("graph-wiki", "reversible computation delta customization", 5);
        assertFalse(hits.isEmpty());
        assertTrue(hits.get(0).getContent().contains("reversible computation"),
                "top hit must be the core-architecture article");
    }

    @Test
    void semanticEdgeTextIsSearchable() {
        IVectorStore<VectorData> store = new InMemoryVectorStore();
        IEmbeddingModel embedding = new KeywordEmbedding();
        RagIngestService ingest = new RagIngestService(
                new io.nop.ai.core.commons.splitter.SimpleTextSplitter(), embedding, store);
        RagSearchService search = new RagSearchService(embedding, store);

        ingest.ingest("semantic-edges", "edge-1",
                "SEMANTICALLY_SIMILAR_TO: demo.Animal and demo.Dog are similar animal types");
        var hits = search.search("semantic-edges", "animal dog similar types", 3);
        assertFalse(hits.isEmpty(), "semantic edge text must be searchable");
    }
}
