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
import io.nop.commons.util.IoHelper;
import io.nop.commons.util.StringHelper;
import io.nop.search.api.ISearchEngine;
import io.nop.search.api.SearchConstants;
import io.nop.search.api.SearchHit;
import io.nop.search.api.SearchRequest;
import io.nop.search.api.SearchResponse;
import io.nop.search.api.SearchableDoc;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.charfilter.HTMLStripCharFilterFactory;
import org.apache.lucene.analysis.core.LowerCaseFilterFactory;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.standard.StandardTokenizerFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.queryparser.flexible.standard.config.PointsConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.search.highlight.DefaultEncoder;
import org.apache.lucene.search.highlight.Formatter;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static io.nop.search.api.SearchConstants.FIELD_CONTENT;
import static io.nop.search.api.SearchConstants.FIELD_ENTITY_ID;
import static io.nop.search.api.SearchConstants.FIELD_LINK;
import static io.nop.search.api.SearchConstants.FIELD_MODIFY_TIME;
import static io.nop.search.api.SearchConstants.FIELD_NAME;
import static io.nop.search.api.SearchConstants.FIELD_PUBLISH_TIME;
import static io.nop.search.api.SearchConstants.FIELD_SUMMARY;
import static io.nop.search.api.SearchConstants.FIELD_TITLE;
import static org.apache.lucene.document.Field.Store.NO;
import static org.apache.lucene.document.Field.Store.YES;
import static org.apache.lucene.index.IndexWriterConfig.OpenMode.CREATE_OR_APPEND;

public class LuceneSearchEngine implements ISearchEngine {
    static final Logger LOG = LoggerFactory.getLogger(LuceneSearchEngine.class);

    private Analyzer analyzer;

    private final Map<String, Directory> indexDirs = new ConcurrentHashMap<>();

    private LuceneConfig config;

    private File rootFile;

    public LuceneConfig getConfig() {
        return config;
    }

    @Inject
    public void setConfig(LuceneConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {
        if (config == null)
            config = new LuceneConfig();

        rootFile = new File(config.getIndexDir());
        rootFile.mkdirs();
        try {
            this.analyzer = buildAnalyzer();
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (this.analyzer != null) {
            this.analyzer.close();
            this.analyzer = null;
        }

        for (Directory directory : indexDirs.values()) {
            IoHelper.safeClose(directory);
        }
        indexDirs.clear();
    }

    protected Analyzer buildAnalyzer() throws IOException {
        CustomAnalyzer.Builder builder = CustomAnalyzer.builder()
                .withTokenizer(StandardTokenizerFactory.class)
                .addCharFilter(HTMLStripCharFilterFactory.NAME)
                .addTokenFilter(LowerCaseFilterFactory.NAME);
        return builder.build();
    }

    protected Directory getDirectory(String topic) {
        if (StringHelper.isEmpty(topic))
            topic = DEFAULT_TOPIC;

        Guard.checkArgument(StringHelper.isValidSimpleVarName(topic), "invalid topic");

        return indexDirs.computeIfAbsent(topic, key -> {
            try {
                return FSDirectory.open(new File(rootFile, key).toPath());
            } catch (IOException e) {
                throw NopException.adapt(e);
            }
        });
    }

    @Override
    public SearchResponse search(SearchRequest request) {
        Directory dir = getDirectory(request.getTopic());

        DirectoryReader dirReader = null;
        try {
            dirReader = DirectoryReader.open(dir);
            long beginTime = CoreMetrics.currentTimeMillis();
            IndexSearcher searcher = new IndexSearcher(dirReader);
            Query query = parseQuery(request.getQuery());

            TopFieldDocs topDocs = searcher.search(query, request.getLimit(), Sort.RELEVANCE);

            Formatter formatter =
                    new SimpleHTMLFormatter(config.getHighlightPreTag(), config.getHighlightPostTag());
            QueryScorer scorer = new QueryScorer(query);
            Highlighter highlighter = new Highlighter(formatter, new DefaultEncoder(), scorer);

            List<SearchHit> hits = new ArrayList<>(topDocs.scoreDocs.length);
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.storedFields().document(scoreDoc.doc);

                String title = doc.get(FIELD_TITLE);
                String titleFragment = highlighter.getBestFragment(analyzer, FIELD_TITLE, title);
                if (titleFragment != null) {
                    title = titleFragment;
                }

                String content = doc.get(FIELD_CONTENT);
                String contentFragment = highlighter.getBestFragment(analyzer, FIELD_CONTENT, content);
                if (contentFragment != null) {
                    content = contentFragment;
                }

                SearchHit hit = new SearchHit();
                hit.setScore(scoreDoc.score);

                hit.setName(doc.get(FIELD_NAME));
                hit.setTitle(title);
                hit.setContent(content);
                IndexableField publishTimeField = doc.getField(FIELD_PUBLISH_TIME);
                if (publishTimeField != null) {
                    long publishTime = publishTimeField.numericValue().longValue();
                    hit.setPublishTime(publishTime);
                }

                IndexableField modifyTimeField = doc.getField(FIELD_MODIFY_TIME);
                if (modifyTimeField != null) {
                    long modifyTime = modifyTimeField.numericValue().longValue();
                    hit.setModifyTime(modifyTime);
                }

                hit.setLink(doc.get(FIELD_LINK));

                hits.add(hit);
            }

            SearchResponse response = new SearchResponse();
            response.setItems(hits);
            response.setTotal(topDocs.totalHits.value);
            response.setQuery(request.getQuery());
            response.setLimit(request.getLimit());
            response.setProcessTime(CoreMetrics.currentTimeMillis() - beginTime);
            return response;
        } catch (IOException | InvalidTokenOffsetsException | QueryNodeException e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeCloseObject(dirReader);
        }
    }

    protected Query parseQuery(String query) throws QueryNodeException {
        StandardQueryParser parser = new StandardQueryParser(analyzer);
        parser.setPointsConfigMap(Map.of(
                SearchConstants.FIELD_PUBLISH_TIME, new PointsConfig(NumberFormat.getNumberInstance(), Long.class)
        ));
        return parser.parse(query, SearchConstants.FIELD_CONTENT);
    }

    @Override
    public void addDocs(String topic, List<SearchableDoc> docs) {
        runWithWriter(topic, writer -> {
            try {
                for (SearchableDoc doc : docs) {
                    Document docValue = buildDocument(doc);
                    long seqNum = writer.updateDocument(new Term(FIELD_NAME, doc.getName()), docValue);
                    LOG.info("nop.search.update-doc:name={},seqNum={}", doc.getName(), seqNum);
                }
            } catch (Exception e) {
                throw NopException.adapt(e);
            }
        });
    }

    protected Document buildDocument(SearchableDoc doc) {
        Document ret = new Document();
        ret.add(new StringField(FIELD_NAME, doc.getName(), YES));
        if (!StringHelper.isEmpty(doc.getTitle()))
            ret.add(new TextField(FIELD_TITLE, doc.getTitle(), YES));
        if (!StringHelper.isEmpty(doc.getSummary()))
            ret.add(new TextField(FIELD_SUMMARY, doc.getSummary(), YES));

        if (!StringHelper.isEmpty(doc.getContent())) {
            ret.add(new TextField(FIELD_CONTENT, doc.getContent(), doc.isStoreContent() ? YES : NO));
        }

        if (!StringHelper.isEmpty(doc.getEntityId()))
            ret.add(new StoredField(FIELD_ENTITY_ID, doc.getEntityId()));

        long publishTime = doc.getPublishTime();
        if (publishTime > 0) {
            ret.add(new LongPoint(FIELD_PUBLISH_TIME, publishTime));
            ret.add(new StoredField(FIELD_PUBLISH_TIME, publishTime));
        }

        long modifyTime = doc.getModifyTime();
        if (modifyTime > 0) {
            ret.add(new LongPoint(FIELD_MODIFY_TIME, modifyTime));
            ret.add(new StoredField(FIELD_MODIFY_TIME, modifyTime));
        }

        if (!StringHelper.isEmpty(doc.getLink()))
            ret.add(new StoredField(FIELD_LINK, doc.getLink()));
        return ret;
    }

    @Override
    public void removeDocs(String topic, List<String> names) {
        runWithWriter(topic, writer -> {
            try {
                Term[] terms = names.stream()
                        .map(postName -> new Term(FIELD_NAME, postName))
                        .toArray(Term[]::new);
                long seqNum = writer.deleteDocuments(terms);
                LOG.info("nop.search.remove-doc:count={}, seqNum={}", names.size(), seqNum);
            } catch (Exception e) {
                throw NopException.adapt(e);
            }
        });
    }

    @Override
    public void removeTopic(String topic) {
        runWithWriter(topic, writer -> {
            try {
                writer.deleteAll();
            } catch (IOException e) {
                throw NopException.adapt(e);
            }
        });
    }

    protected void runWithWriter(String topic, Consumer<IndexWriter> fn) {
        IndexWriterConfig writeConfig = new IndexWriterConfig(analyzer);
        writeConfig.setOpenMode(CREATE_OR_APPEND);
        Directory dir = getDirectory(topic);

        IndexWriter writer = null;
        try {
            writer = new IndexWriter(dir, writeConfig);
            fn.accept(writer);
        } catch (Exception e) {
            throw NopException.adapt(e);
        } finally {
            IoHelper.safeCloseObject(writer);
        }
    }
}