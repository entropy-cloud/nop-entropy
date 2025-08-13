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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static io.nop.search.api.SearchConstants.FIELD_BIZ_KEY;
import static io.nop.search.api.SearchConstants.FIELD_CONTENT;
import static io.nop.search.api.SearchConstants.FIELD_FILE_SIZE;
import static io.nop.search.api.SearchConstants.FIELD_ID;
import static io.nop.search.api.SearchConstants.FIELD_MODIFY_TIME;
import static io.nop.search.api.SearchConstants.FIELD_NAME;
import static io.nop.search.api.SearchConstants.FIELD_PATH;
import static io.nop.search.api.SearchConstants.FIELD_PUBLISH_TIME;
import static io.nop.search.api.SearchConstants.FIELD_SUMMARY;
import static io.nop.search.api.SearchConstants.FIELD_TAG;
import static io.nop.search.api.SearchConstants.FIELD_TITLE;
import static io.nop.search.lucene.LuceneErrors.ARG_INDEX_DIR;
import static io.nop.search.lucene.LuceneErrors.ARG_TOPIC;
import static io.nop.search.lucene.LuceneErrors.ERR_LUCENE_OPEN_INDEX_FAIL;

public class LuceneSearchEngine implements ISearchEngine {
    static final Logger LOG = LoggerFactory.getLogger(LuceneSearchEngine.class);

    private Analyzer analyzer;
    private final Map<String, Directory> indexDirs = new ConcurrentHashMap<>();
    private final Map<String, IndexWriter> indexWriters = new ConcurrentHashMap<>();
    private final Map<String, SearcherManager> searcherManagers = new ConcurrentHashMap<>();
    private LuceneConfig config;
    private Path rootPath;

    @Inject
    public void setConfig(LuceneConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {
        if (config == null) {
            config = new LuceneConfig();
        }

        this.rootPath = Path.of(config.getIndexDir());
        try {
            if (!java.nio.file.Files.exists(rootPath)) {
                java.nio.file.Files.createDirectories(rootPath);
            }
            this.analyzer = buildAnalyzer();
        } catch (Exception e) {
            throw new NopException(ERR_LUCENE_OPEN_INDEX_FAIL, e)
                    .param(ARG_INDEX_DIR, config.getIndexDir());
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
                Path topicPath = rootPath.resolve(key);
                return FSDirectory.open(topicPath);
            } catch (IOException e) {
                throw new NopException(ERR_LUCENE_OPEN_INDEX_FAIL, e)
                        .param(ARG_TOPIC, finalTopic)
                        .param(ARG_INDEX_DIR, config.getIndexDir());
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
        SearcherManager manager = getSearcherManager(request.getTopic());

        try {
            manager.maybeRefresh(); // 尝试刷新获取最新索引
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

        return ret;
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