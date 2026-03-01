/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.search.lucene;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.search.api.ISearchEngine;
import io.nop.search.api.SearchHit;
import io.nop.search.api.SearchRequest;
import io.nop.search.api.SearchResponse;
import io.nop.search.api.SearchableDoc;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.charfilter.HTMLStripCharFilterFactory;
import org.apache.lucene.analysis.core.LowerCaseFilterFactory;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.pattern.PatternReplaceCharFilterFactory;
import org.apache.lucene.analysis.standard.StandardTokenizerFactory;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.MultiTerms;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.queryparser.flexible.standard.config.PointsConfig;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.search.highlight.DefaultEncoder;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.ScoreDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// Optional embedding support via ITextEmbedding interface
import io.nop.search.api.ITextEmbedding;

import static io.nop.search.api.SearchConstants.FIELD_CONTENT;
import static io.nop.search.api.SearchConstants.FIELD_FILE_SIZE;
import static io.nop.search.api.SearchConstants.FIELD_ID;
import static io.nop.search.api.SearchConstants.FIELD_MODIFY_TIME;
import static io.nop.search.api.SearchConstants.FIELD_NAME;
import static io.nop.search.api.SearchConstants.FIELD_PATH;
import static io.nop.search.api.SearchConstants.FIELD_PUBLISH_TIME;
import static io.nop.search.api.SearchConstants.FIELD_SUMMARY;
import static io.nop.search.api.SearchConstants.FIELD_TAG;
import static io.nop.search.api.SearchConstants.FIELD_BIZ_KEY;

import static io.nop.search.lucene.LuceneErrors.ARG_TOPIC;
import static io.nop.search.lucene.LuceneErrors.ERR_LUCENE_OPEN_INDEX_FAIL;
import static io.nop.search.lucene.LuceneErrors.ERR_LUCENE_VECTOR_SEARCH_NOT_IMPLEMENTED;
import static io.nop.search.lucene.LuceneErrors.ERR_LUCENE_HYBRID_SEARCH_NOT_IMPLEMENTED;
import static io.nop.search.api.SearchConstants.FIELD_TITLE;

import io.nop.search.api.SearchType;
import static io.nop.search.lucene.LuceneErrors.ARG_TOPIC;
import static io.nop.search.lucene.LuceneErrors.ERR_LUCENE_OPEN_INDEX_FAIL;

public class LuceneSearchEngine implements ISearchEngine {
    static final Logger LOG = LoggerFactory.getLogger(LuceneSearchEngine.class);

    private static final String FIELD_EMBEDDING = "embedding";

    private Analyzer analyzer;
    private final Map<String, Directory> indexDirs = new ConcurrentHashMap<>();
    private final Map<String, IndexWriter> indexWriters = new ConcurrentHashMap<>();
    private final Map<String, SearcherManager> searcherManagers = new ConcurrentHashMap<>();
    private LuceneConfig config;
    private File rootPath;

    /**
     * Optional embedding provider for generating vector embeddings.
     * If not set, a hash-based mock embedding will be used.
     */
    private ITextEmbedding textEmbedding;

    @Inject
    public void setConfig(LuceneConfig config) {
        this.config = config;
    }

    /**
     * Optional injection of text embedding provider.
     * If not injected, hash-based mock embeddings will be used.
     */
    public void setTextEmbedding(ITextEmbedding textEmbedding) {
        this.textEmbedding = textEmbedding;
    }

    @PostConstruct
    public void init() {
        if (config == null) {
            config = new LuceneConfig();
        }

        this.rootPath = FileHelper.resolveFile(config.getIndexDir());
        try {
            this.analyzer = buildAnalyzer();
        } catch (Exception e) {
            throw new NopException(ERR_LUCENE_OPEN_INDEX_FAIL, e);
        }
    }

    @PreDestroy
    public void destroy() {
        // Close all searcher managers
        searcherManagers.values().forEach(manager -> {
            try {
                if (manager != null) {
                    manager.close();
                }
            } catch (IOException e) {
                LOG.error("nop.search.close-searcher-manager-fail", e);
            }
        });
        searcherManagers.clear();

        // Close all writers
        indexWriters.values().forEach(writer -> {
            try {
                if (writer != null) {
                    writer.close();
                }
            } catch (IOException e) {
                LOG.error("nop.search.close-writer-fail", e);
            }
        });
        indexWriters.clear();

        // Close all directories
        indexDirs.values().forEach(directory -> {
            try {
                if (directory != null) {
                    directory.close();
                }
            } catch (IOException e) {
                LOG.error("nop.search.close-dir-fail", e);
            }
        });
        indexDirs.clear();

        // Close analyzer
        if (this.analyzer != null) {
            this.analyzer.close();
            this.analyzer = null;
        }
    }

    protected Analyzer buildAnalyzer() throws IOException {
        return CustomAnalyzer.builder()
                .withTokenizer(StandardTokenizerFactory.class)
                .addCharFilter(HTMLStripCharFilterFactory.class)
                // 添加模式替换过滤器，将代码中的分隔符替换为空格
                .addCharFilter(PatternReplaceCharFilterFactory.class,
                        "pattern", "[.\\[\\]/(){},:;]",  // 匹配 . [ ] / ( ) { } , : ;
                        "replacement", " ")              // 替换为空格
                .addTokenFilter(LowerCaseFilterFactory.class)
                .build();
    }

    protected Directory getDirectory(String topic) {
        if (StringHelper.isEmpty(topic)) {
            topic = DEFAULT_TOPIC;
        }

        Guard.checkArgument(StringHelper.isValidSimpleVarName(topic), "invalid topic");

        String finalTopic = topic;
        return indexDirs.computeIfAbsent(topic, key -> {
            try {
                rootPath.mkdirs();
                Path topicPath = rootPath.toPath().resolve(key);
                return FSDirectory.open(topicPath);
            } catch (IOException e) {
                throw new NopException(ERR_LUCENE_OPEN_INDEX_FAIL, e)
                        .param(ARG_TOPIC, finalTopic);
            }
        });
    }

    protected SearcherManager getSearcherManager(String topic) {
        return searcherManagers.computeIfAbsent(topic, key -> {
            try {
                IndexWriter writer = getIndexWriter(topic);
                return new SearcherManager(writer, true, true, null);
            } catch (IOException e) {
                throw new NopException(ERR_LUCENE_OPEN_INDEX_FAIL, e)
                        .param(ARG_TOPIC, topic);
            }
        });
    }

    protected IndexWriter getIndexWriter(String topic) {
        return indexWriters.computeIfAbsent(topic, key -> {
            try {
                Directory dir = getDirectory(topic);
                IndexWriterConfig writeConfig = new IndexWriterConfig(analyzer);
                writeConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
                writeConfig.setRAMBufferSizeMB(config.getRamBufferSizeMB());
                return new IndexWriter(dir, writeConfig);
            } catch (IOException e) {
                throw new NopException(ERR_LUCENE_OPEN_INDEX_FAIL, e)
                        .param(ARG_TOPIC, topic);
            }
        });
    }

    @Override
    public void refreshBlocking(String topic) {
        try {
            getSearcherManager(topic).maybeRefreshBlocking();
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    @Override
    public SearchableDoc getDoc(String docId) {
        Guard.notEmpty(docId, "docId");

        // 优先查有缓存的topic
        for (String topic : searcherManagers.keySet()) {
            SearcherManager manager = getSearcherManager(topic);
            try {
                manager.maybeRefresh();
                IndexSearcher searcher = manager.acquire();

                try {
                    Query query = new TermQuery(new Term(FIELD_ID, docId));
                    // 只查第一个
                    TopFieldDocs docs = searcher.search(query, 1, Sort.RELEVANCE);
                    if (docs.scoreDocs != null && docs.scoreDocs.length > 0) {
                        int docNum = docs.scoreDocs[0].doc;
                        Document luceneDoc = searcher.storedFields().document(docNum);
                        return convertDocumentToSearchableDoc(luceneDoc);
                    }
                } finally {
                    manager.release(searcher);
                }
            } catch (IOException e) {
                throw NopException.adapt(e);
            }
        }
        // 若所有topic找完没找到
        return null;
    }

    @Override
    public Map<String, List<String>> analyzeDoc(SearchableDoc doc) {
        Guard.notNull(doc, "doc");
        Map<String, List<String>> result = new LinkedHashMap<>();

        // 仅针对 buildDocument 里以 TextField（分词型）建立的字段
        Map<String, String> textFields = new LinkedHashMap<>();
        textFields.put(FIELD_TITLE, doc.getTitle());
        textFields.put(FIELD_SUMMARY, doc.getSummary());
        textFields.put(FIELD_CONTENT, doc.getContent());

        for (Map.Entry<String, String> entry : textFields.entrySet()) {
            String field = entry.getKey();
            String value = entry.getValue();
            if (StringHelper.isEmpty(value))
                continue;
            List<String> tokens = analyzeText(analyzer, field, value);
            result.put(field, tokens);
        }

        return result;
    }

    @Override
    public List<String> analyzeQuery(String query) {
        return analyzeText(analyzer, FIELD_CONTENT, query);
    }

    private List<String> analyzeText(Analyzer analyzer, String fieldName, String text) {
        List<String> tokens = new ArrayList<>();
        try (TokenStream tokenStream = analyzer.tokenStream(fieldName, new StringReader(text))) {
            CharTermAttribute attr = tokenStream.addAttribute(CharTermAttribute.class);
            tokenStream.reset();
            while (tokenStream.incrementToken()) {
                tokens.add(attr.toString());
            }
            tokenStream.end();
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
        return tokens;
    }

    protected SearchableDoc convertDocumentToSearchableDoc(Document doc) {
        SearchableDoc sd = new SearchableDoc();
        sd.setId(doc.get(FIELD_ID));
        sd.setName(doc.get(FIELD_NAME));
        sd.setTitle(doc.get(FIELD_TITLE));
        sd.setSummary(doc.get(FIELD_SUMMARY));
        sd.setContent(doc.get(FIELD_CONTENT));
        sd.setBizKey(doc.get(FIELD_BIZ_KEY));
        sd.setPath(doc.get(FIELD_PATH));
        sd.setPublishTime(processNumericField(doc, FIELD_PUBLISH_TIME));
        sd.setModifyTime(processNumericField(doc, FIELD_MODIFY_TIME));
        sd.setFileSize(processNumericField(doc, FIELD_FILE_SIZE));

        // Tags
        IndexableField[] tagFields = doc.getFields(FIELD_TAG);
        if (tagFields != null && tagFields.length > 0) {
            Set<String> tags = new LinkedHashSet<>(tagFields.length);
            for (IndexableField field : tagFields) {
                tags.add(field.stringValue());
            }
            sd.setTagSet(tags);
        }
        return sd;
    }

    /**
     * 将Document转换为SearchHit
     */
    protected SearchHit convertDocumentToSearchHit(Document doc) {
        SearchHit hit = new SearchHit();
        hit.setId(doc.get(FIELD_ID));
        hit.setName(doc.get(FIELD_NAME));
        hit.setTitle(doc.get(FIELD_TITLE));
        hit.setSummary(doc.get(FIELD_SUMMARY));
        hit.setContent(doc.get(FIELD_CONTENT));
        hit.setBizKey(doc.get(FIELD_BIZ_KEY));
        hit.setPath(doc.get(FIELD_PATH));
        hit.setPublishTime(processNumericField(doc, FIELD_PUBLISH_TIME));
        hit.setModifyTime(processNumericField(doc, FIELD_MODIFY_TIME));
        hit.setFileSize(processNumericField(doc, FIELD_FILE_SIZE));

        // Tags
        IndexableField[] tagFields = doc.getFields(FIELD_TAG);
        if (tagFields != null && tagFields.length > 0) {
            Set<String> tags = new LinkedHashSet<>(tagFields.length);
            for (IndexableField field : tagFields) {
                tags.add(field.stringValue());
            }
            hit.setTags(tags);
        }
        return hit;
    }

    @Override
    public List<SearchableDoc> getDocsByTerm(String topic, String termText) {
        List<SearchableDoc> docs = new ArrayList<>();
        String field = FIELD_CONTENT;

        // 1. 先对 termText 做分词，取所有 tokens
        List<String> tokens = analyzeTerm(termText);

        if (tokens.isEmpty()) {
            return docs;
        }

        // 2. 反查所有包含任一 token 的文档，docId 去重
        Set<Integer> foundDocIds = new LinkedHashSet<>();
        SearcherManager manager = getSearcherManager(topic);
        try {
            manager.maybeRefresh();
            IndexSearcher searcher = manager.acquire();
            try {
                IndexReader reader = searcher.getIndexReader();
                for (String token : tokens) {
                    Terms terms = MultiTerms.getTerms(reader, field);
                    if (terms == null) continue;
                    TermsEnum termsEnum = terms.iterator();
                    if (termsEnum.seekExact(new BytesRef(token))) {
                        PostingsEnum postings = termsEnum.postings(null, PostingsEnum.NONE);
                        int docId;
                        while ((docId = postings.nextDoc()) != PostingsEnum.NO_MORE_DOCS) {
                            foundDocIds.add(docId);
                        }
                    }
                }
                for (Integer docId : foundDocIds) {
                    Document doc = reader.document(docId);
                    docs.add(convertDocumentToSearchableDoc(doc));
                }
            } finally {
                manager.release(searcher);
            }
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
        return docs;
    }

    /**
     * 对 termText 进行分词，返回所有 token（小写处理，与分词一致）。
     */
    protected List<String> analyzeTerm(String termText) {
        return analyzeText(analyzer, FIELD_CONTENT, termText);
    }

    @Override
    public SearchResponse search(SearchRequest request) {
        SearchType searchType = request.getSearchType() != null
                ? request.getSearchType()
                : SearchType.TEXT;

        switch (searchType) {
            case VECTOR:
                return vectorSearch(request);
            case HYBRID:
                return hybridSearch(request);
            case TEXT:
            default:
                return textSearch(request);
        }
    }

    /**
     * 纯文本搜索（原有逻辑）
     */
    protected SearchResponse textSearch(SearchRequest request) {
        SearcherManager manager = getSearcherManager(request.getTopic());

        try {
            manager.maybeRefresh();
            IndexSearcher searcher = manager.acquire();

            try {
                long beginTime = CoreMetrics.currentTimeMillis();
                Query query = buildQuery(request);

                TopFieldDocs topDocs = searcher.search(query, request.getLimit(), Sort.RELEVANCE);

                Highlighter highlighter = newHighligher(query, request);

                List<SearchHit> hits = processHits(searcher, topDocs, highlighter);

                SearchResponse response = new SearchResponse();
                response.setItems(hits);
                response.setTotal(topDocs.totalHits.value);
                response.setQuery(request.getQuery());
                response.setLimit(request.getLimit());
                response.setProcessTime(CoreMetrics.currentTimeMillis() - beginTime);
                return response;
            } finally {
                manager.release(searcher);
            }
        } catch (IOException | InvalidTokenOffsetsException e) {
            throw NopException.adapt(e);
        }
    }

    /**
     * 纯向量搜索
     */
    /**
     * 纯向量搜索
     */
    protected SearchResponse vectorSearch(SearchRequest request) {
        SearcherManager manager = getSearcherManager(request.getTopic());

        try {
            manager.maybeRefresh();
            IndexSearcher searcher = manager.acquire();

            try {
                long beginTime = CoreMetrics.currentTimeMillis();

                // 对于vector搜索，query应该是预先生成的embedding向量的base64编码
                // 或者是一个简单的文本，我们使用简单的hash模拟
                float[] queryVector = parseQueryVector(request.getQuery());

                if (queryVector == null || queryVector.length == 0) {
                    throw new NopException(ERR_LUCENE_VECTOR_SEARCH_NOT_IMPLEMENTED)
                            .param(ARG_TOPIC, request.getTopic())
                            .param("reason", "Query vector is empty or invalid");
                }

                // 创建kNN查询
                int k = (int) (request.getLimit() * 1.5); // 多取一些用于阈值过滤
                KnnFloatVectorQuery knnQuery = new KnnFloatVectorQuery(
                    FIELD_EMBEDDING,
                    queryVector,
                    k
                );

                // 执行搜索
                TopDocs topDocs = searcher.search(knnQuery, k);

                // 转换结果
                List<SearchHit> hits = new ArrayList<>();
                for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                    // Lucene COSINE similarity的score范围是[0,1]
                    double similarity = (double) scoreDoc.score;

                    // 应用阈值过滤（similarityThreshold范围[0,1]）
                    if (similarity < request.getSimilarityThreshold()) {
                        continue;
                    }

                    Document doc = searcher.storedFields().document(scoreDoc.doc);
                    SearchHit hit = convertDocumentToSearchHit(doc);

                    // 设置分数（方案A：复用score字段）
                    hit.setScore((float) similarity);

                    hits.add(hit);

                    if (hits.size() >= request.getLimit()) {
                        break;
                    }
                }

                // 构建响应
                SearchResponse response = new SearchResponse();
                response.setItems(hits);
                response.setTotal(hits.size());
                response.setQuery(request.getQuery());
                response.setLimit(request.getLimit());
                response.setProcessTime(CoreMetrics.currentTimeMillis() - beginTime);

                return response;

            } finally {
                manager.release(searcher);
            }
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    /**
     * 解析查询向量
     * 支持三种格式：
     * 1. JSON数组格式：[0.1, 0.2, 0.3, ...]
     * 2. 纯文本 + ITextEmbedding：使用嵌入模型生成embedding
     * 3. 纯文本（无ITextEmbedding）：使用简单的hash模拟（仅用于测试）
     */

    private float[] parseQueryVector(String query) {
        if (StringHelper.isEmpty(query)) {
            return null;
        }

        // 尝试解析JSON数组
        if (query.startsWith("[") && query.endsWith("]")) {
            try {
                String content = query.substring(1, query.length() - 1);
                String[] parts = content.split(",");
                float[] vector = new float[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    vector[i] = Float.parseFloat(parts[i].trim());
                }
                return vector;
            } catch (Exception e) {
                LOG.warn("nop.search.parse-query-vector-failed:query={}", query, e);
            }
        }

        // 如果有ITextEmbedding，使用它生成embedding
        if (textEmbedding != null) {
            float[] vector = textEmbedding.embed(query);
            if (vector != null && vector.length > 0) {
                return vector;
            }
        }

        // 使用简单的文本hash模拟（仅用于测试，实际应使用ITextEmbedding）
        return generateSimpleEmbedding(query);
    }


    /**
     * 生成简单的文本embedding（仅用于测试）
     * 实际生产环境应使用ITextEmbedding
     */
    private float[] generateSimpleEmbedding(String text) {
        int dim = config != null ? config.getEmbeddingDimension() : 768;
        float[] embedding = new float[dim];

        // 使用字符串hash生成伪随机向量
        int hash = text.hashCode();
        java.util.Random random = new java.util.Random(hash);

        for (int i = 0; i < dim; i++) {
            embedding[i] = random.nextFloat() * 2 - 1; // 范围[-1, 1]
        }

        // 归一化
        float norm = 0;
        for (float v : embedding) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);

        if (norm > 0) {
            for (int i = 0; i < dim; i++) {
                embedding[i] /= norm;
            }
        }

        return embedding;
    }


    /**
     * 混合搜索（文本 + 向量，使用RRF融合）
     */
    protected SearchResponse hybridSearch(SearchRequest request) {
        SearcherManager manager = getSearcherManager(request.getTopic());

        try {
            manager.maybeRefresh();
            IndexSearcher searcher = manager.acquire();

            try {
                long beginTime = CoreMetrics.currentTimeMillis();

                // 1. 文本搜索
                Query textQuery = buildQuery(request);
                TopDocs textResults = searcher.search(textQuery, request.getLimit() * 2);

                // 2. 向量搜索
                float[] queryVector = parseQueryVector(request.getQuery());
                TopDocs vectorResults = null;
                if (queryVector != null && queryVector.length > 0) {
                    KnnFloatVectorQuery vectorQuery = new KnnFloatVectorQuery(
                        FIELD_EMBEDDING,
                        queryVector,
                        request.getLimit() * 2
                    );
                    vectorResults = searcher.search(vectorQuery, request.getLimit() * 2);
                }

                // 3. RRF融合
                List<SearchHit> mergedHits = mergeWithRRF(
                    textResults,
                    vectorResults,
                    searcher,
                    60  // RRF k parameter
                );

                // 4. 应用阈值并限制数量
                List<SearchHit> filteredHits = mergedHits.stream()
                    .filter(hit -> hit.getScore() >= request.getSimilarityThreshold())
                    .limit(request.getLimit())
                    .collect(Collectors.toList());

                // 5. 构建响应
                SearchResponse response = new SearchResponse();
                response.setItems(filteredHits);
                response.setTotal(filteredHits.size());
                response.setQuery(request.getQuery());
                response.setLimit(request.getLimit());
                response.setProcessTime(CoreMetrics.currentTimeMillis() - beginTime);

                return response;

            } finally {
                manager.release(searcher);
            }
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    /**
     * Reciprocal Rank Fusion (RRF) 融合算法
     */
    private List<SearchHit> mergeWithRRF(
        TopDocs textResults,
        TopDocs vectorResults,
        IndexSearcher searcher,
        int k
    ) throws IOException {
        Map<String, Double> scoreMap = new HashMap<>();
        Map<String, SearchHit> hitMap = new HashMap<>();

        // 处理文本搜索结果
        if (textResults != null) {
            for (int i = 0; i < textResults.scoreDocs.length; i++) {
                ScoreDoc scoreDoc = textResults.scoreDocs[i];
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                String docId = doc.get(FIELD_ID);

                double rrfScore = 1.0 / (k + i + 1);
                scoreMap.merge(docId, rrfScore, Double::sum);

                if (!hitMap.containsKey(docId)) {
                    SearchHit hit = convertDocumentToSearchHit(doc);
                    hitMap.put(docId, hit);
                }
            }
        }

        // 处理向量搜索结果
        if (vectorResults != null) {
            for (int i = 0; i < vectorResults.scoreDocs.length; i++) {
                ScoreDoc scoreDoc = vectorResults.scoreDocs[i];
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                String docId = doc.get(FIELD_ID);

                double rrfScore = 1.0 / (k + i + 1);
                scoreMap.merge(docId, rrfScore, Double::sum);

                if (hitMap.containsKey(docId)) {
                    // 如果已存在（hybrid），说明来自文本搜索，无需额外处理
                } else {
                    SearchHit hit = convertDocumentToSearchHit(doc);
                    hitMap.put(docId, hit);
                }
            }
        }

        // 排序并设置融合分数
        return hitMap.entrySet().stream()
            .sorted((e1, e2) -> Double.compare(
                scoreMap.get(e2.getKey()),
                scoreMap.get(e1.getKey())
            ))
            .peek(e -> e.getValue().setScore(scoreMap.get(e.getKey()).floatValue()))
            .map(Map.Entry::getValue)
            .collect(Collectors.toList());
    }

    protected Highlighter newHighligher(Query query, SearchRequest request) {
        SimpleHTMLFormatter formatter = new SimpleHTMLFormatter(
                config.getHighlightPreTag(),
                config.getHighlightPostTag());
        Highlighter highlighter = new Highlighter(formatter, new DefaultEncoder(), new QueryScorer(query));
        highlighter.setMaxDocCharsToAnalyze(config.getDefaultMaxDocCharsToAnalyze());
        return highlighter;
    }


    @Override
    public void addDocs(String topic, List<SearchableDoc> docs) {
        for (SearchableDoc doc : docs) {
            LOG.info("nop.search.add-doc:docId={},path={}", doc.getId(), doc.getPath());
        }

        IndexWriter writer = getIndexWriter(topic);

        try {
            // 先删除旧文档
            Term[] terms = docs.stream()
                    .map(doc -> new Term(FIELD_ID, doc.getId()))
                    .toArray(Term[]::new);
            writer.deleteDocuments(terms);

            // 批量添加新文档
            List<Document> documents = docs.stream()
                    .map(this::buildDocument)
                    .collect(Collectors.toList());
            writer.addDocuments(documents);

            writer.commit();

            // 通知SearcherManager有更新
            SearcherManager manager = searcherManagers.get(topic);
            if (manager != null) {
                manager.maybeRefresh();
            }

            LOG.info("nop.search.bulk-update:topic={},count={}", topic, docs.size());
        } catch (Exception e) {
            rollback(writer);
            throw NopException.adapt(e);
        }
    }


    protected Query buildQuery(SearchRequest request) throws NopException {
        try {
            StandardQueryParser parser = new StandardQueryParser(analyzer);
            parser.setPointsConfigMap(Map.of(
                    FIELD_PUBLISH_TIME, new PointsConfig(NumberFormat.getNumberInstance(), Long.class),
                    FIELD_MODIFY_TIME, new PointsConfig(NumberFormat.getNumberInstance(), Long.class),
                    FIELD_FILE_SIZE, new PointsConfig(NumberFormat.getNumberInstance(), Long.class)
            ));

            Query mainQuery = StringHelper.isEmpty(request.getQuery())
                    ? new BooleanQuery.Builder().build() // Match all if no query
                    : parser.parse(request.getQuery(), FIELD_CONTENT);

            BooleanQuery.Builder finalQueryBuilder = new BooleanQuery.Builder()
                    .add(mainQuery, BooleanClause.Occur.MUST);

            // Add tag filters if present
            addTagFilters(request, finalQueryBuilder);

            if (request.getFilter() != null) {
                LuceneFilterBeanTransformer transformer = new LuceneFilterBeanTransformer();
                Query filterQuery = transformer.visit(request.getFilter(), DisabledEvalScope.INSTANCE);
                finalQueryBuilder.add(filterQuery, BooleanClause.Occur.FILTER);
            }

            return finalQueryBuilder.build();
        } catch (QueryNodeException e) {
            throw NopException.adapt(e);
        }
    }

    protected void addTagFilters(SearchRequest request, BooleanQuery.Builder queryBuilder) {
        if (request.getTags() == null || request.getTags().isEmpty()) {
            return;
        }

        // 将标签集合转换为BytesRef集合
        List<BytesRef> bytesRefs = new ArrayList<>(request.getTags().size());
        for (String tag : request.getTags()) {
            bytesRefs.add(new BytesRef(tag.toLowerCase()));
        }

        // 创建TermsInSetQuery
        TermInSetQuery termsQuery = new TermInSetQuery(FIELD_TAG, bytesRefs);

        if (request.isMatchAllTags()) {
            // 必须匹配所有标签时使用BooleanQuery+必须条件
            BooleanQuery.Builder allTagsBuilder = new BooleanQuery.Builder();
            for (BytesRef tag : bytesRefs) {
                allTagsBuilder.add(new TermQuery(new Term(FIELD_TAG, tag)),
                        BooleanClause.Occur.MUST);
            }
            queryBuilder.add(allTagsBuilder.build(), BooleanClause.Occur.FILTER);
        } else {
            // 匹配任意标签时使用TermsInSetQuery
            queryBuilder.add(termsQuery, BooleanClause.Occur.FILTER);
        }
    }

    protected List<SearchHit> processHits(IndexSearcher searcher, TopFieldDocs topDocs, Highlighter highlighter)
            throws IOException, InvalidTokenOffsetsException {
        List<SearchHit> hits = new ArrayList<>(topDocs.scoreDocs.length);

        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            Document doc = searcher.storedFields().document(scoreDoc.doc);
            SearchHit hit = new SearchHit();
            if(!Float.isNaN(scoreDoc.score))
                hit.setScore(scoreDoc.score);

            hit.setId(doc.get(FIELD_ID));
            hit.setTitle(processTextField(doc, FIELD_TITLE, highlighter));
            hit.setHighlightedText(processTextField(doc, FIELD_CONTENT, highlighter));
            hit.setSummary(processTextField(doc, FIELD_SUMMARY, highlighter));

            hit.setName(doc.get(FIELD_NAME));
            hit.setBizKey(doc.get(FIELD_BIZ_KEY));
            hit.setPath(doc.get(FIELD_PATH));

            hit.setPublishTime(processNumericField(doc, FIELD_PUBLISH_TIME));
            hit.setModifyTime(processNumericField(doc, FIELD_MODIFY_TIME));
            hit.setFileSize(processNumericField(doc, FIELD_FILE_SIZE));

            // Process tags
            IndexableField[] tagFields = doc.getFields(FIELD_TAG);
            if (tagFields != null && tagFields.length > 0) {
                Set<String> tags = new LinkedHashSet<>(tagFields.length);
                for (IndexableField field : tagFields) {
                    tags.add(field.stringValue());
                }
                hit.setTags(tags);
            }

            hits.add(hit);
        }

        return hits;
    }

    protected String processTextField(Document doc, String fieldName, Highlighter highlighter) {
        String value = doc.get(fieldName);
        if (value == null) return null;

        if (highlighter == null) return value;

        int maxFrags = config.getDefaultMaxNumFragments();

        try {
            String[] frags = highlighter.getBestFragments(analyzer, fieldName, value, maxFrags);
            if (frags == null || frags.length == 0) return value;
            if (frags.length == 1) return frags[0];

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < frags.length; i++) {
                if (i > 0) sb.append("\n\n");
                sb.append(frags[i]);
            }
            return sb.toString();
        } catch (Exception e) {
            LOG.warn("nop.search.highlight-failed:field={}", fieldName, e);
            return value;
        }
    }

    protected long processNumericField(Document doc, String fieldName) {
        IndexableField field = doc.getField(fieldName);
        if (field != null) {
            Number num = field.numericValue(); // 可能为null
            return num != null ? num.longValue() : -1;
        }
        return -1;
    }


    protected Document buildDocument(SearchableDoc doc) {
        Guard.notEmpty(doc.getId(), "id");

        Document ret = new Document();

        // Core fields
        ret.add(new StringField(FIELD_ID, doc.getId(), Field.Store.YES));
        if (!StringHelper.isEmpty(doc.getName()))
            ret.add(new StringField(FIELD_NAME, doc.getName(), Field.Store.YES));
        addTextField(ret, FIELD_TITLE, doc.getTitle(), true);
        addTextField(ret, FIELD_SUMMARY, doc.getSummary(), true);
        addTextField(ret, FIELD_CONTENT, doc.getContent(), doc.isStoreContent());

        // Business key
        if (!StringHelper.isEmpty(doc.getBizKey())) {
            ret.add(new StringField(FIELD_BIZ_KEY, doc.getBizKey(), Field.Store.YES));
        }

        // Numeric fields
        addNumericField(ret, FIELD_PUBLISH_TIME, doc.getPublishTime());
        addNumericField(ret, FIELD_MODIFY_TIME, doc.getModifyTime());
        addNumericField(ret, FIELD_FILE_SIZE, doc.getFileSize());

        // Path
        if (!StringHelper.isEmpty(doc.getPath())) {
            ret.add(new StoredField(FIELD_PATH, doc.getPath()));
        }

        // Tags
        if (doc.getTagSet() != null) {
            for (String tag : doc.getTagSet()) {
                if (!StringHelper.isEmpty(tag)) {
                    ret.add(new StringField(FIELD_TAG, tag.toLowerCase(), Field.Store.YES));
                }
            }
        }

        // Embedding vector field
        float[] embedding = doc.getEmbedding();
        
        // If no embedding but autoGenerate is true and we have an embedding provider, generate it
        if (embedding == null && doc.isAutoGenerateEmbedding() && textEmbedding != null) {
            String textForEmbedding = buildTextForEmbedding(doc);
            if (!StringHelper.isEmpty(textForEmbedding)) {
                try {
                    embedding = textEmbedding.embed(textForEmbedding);
                    if (embedding != null && embedding.length > 0) {
                        doc.setEmbedding(embedding); // Cache for potential reuse
                    }
                } catch (Exception e) {
                    LOG.warn("nop.search.generate-embedding-failed:docId={}", doc.getId(), e);
                }
            }
        }


        // Add embedding field if available
        if (embedding != null && embedding.length > 0) {
            ret.add(new KnnFloatVectorField(FIELD_EMBEDDING, embedding, VectorSimilarityFunction.COSINE));
        }

        return ret;
    }

    /**
     * Build text content for embedding generation.
     * Combines title, summary, and content.
     */
    private String buildTextForEmbedding(SearchableDoc doc) {
        StringBuilder sb = new StringBuilder();
        if (!StringHelper.isEmpty(doc.getTitle())) {
            sb.append(doc.getTitle()).append(" ");
        }
        if (!StringHelper.isEmpty(doc.getSummary())) {
            sb.append(doc.getSummary()).append(" ");
        }
        if (!StringHelper.isEmpty(doc.getContent())) {
            sb.append(doc.getContent());
        }
        return sb.toString().trim();
    }

    protected void addTextField(Document doc, String fieldName, String value, boolean store) {
        if (!StringHelper.isEmpty(value)) {
            TextField field = new TextField(fieldName, value, store ? Field.Store.YES : Field.Store.NO);
            doc.add(field);
        }
    }

    protected void addNumericField(Document doc, String fieldName, long value) {
        if (value > 0) {
            doc.add(new LongPoint(fieldName, value));
            doc.add(new StoredField(fieldName, value));
        }
    }

    @Override
    public void removeDocs(String topic, List<String> ids) {
        IndexWriter writer = getIndexWriter(topic);

        try {
            Term[] terms = ids.stream()
                    .map(id -> new Term(FIELD_ID, id))
                    .toArray(Term[]::new);

            writer.deleteDocuments(terms);
            writer.commit();

            // 通知SearcherManager有更新
            SearcherManager manager = searcherManagers.get(topic);
            if (manager != null) {
                manager.maybeRefresh();
            }

            LOG.info("nop.search.remove-docs:topic={},count={}", topic, ids.size());
        } catch (Exception e) {
            rollback(writer);
            throw NopException.adapt(e);
        }
    }

    @Override
    public void removeTopic(String topic) {
        // 清理SearcherManager
        SearcherManager manager = searcherManagers.remove(topic);
        if (manager != null) {
            try {
                manager.close();
            } catch (IOException e) {
                LOG.error("nop.search.close-searcher-manager-fail", e);
            }
        }

        // 清理Writer
        IndexWriter writer = indexWriters.remove(topic);
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                LOG.error("nop.search.close-writer-fail", e);
            }
        }

        // 清理Directory
        Directory dir = indexDirs.remove(topic);
        if (dir != null) {
            try {
                dir.close();
            } catch (IOException e) {
                LOG.error("nop.search.close-dir-fail", e);
            }
        }

        LOG.info("nop.search.remove-topic:topic={}", topic);
    }

    protected void rollback(IndexWriter writer) {
        try {
            writer.rollback();
        } catch (Exception e) {
            LOG.error("nop.search.rollback-fail", e);
        }
    }
}