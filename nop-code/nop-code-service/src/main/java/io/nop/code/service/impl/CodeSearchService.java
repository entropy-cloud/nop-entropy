package io.nop.code.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.TreeBean;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.beans.query.QueryFieldBean;
import io.nop.code.core.model.CodeLanguage;
import io.nop.code.core.model.CodeSymbolKind;
import io.nop.code.dao.entity.NopCodeFile;
import io.nop.code.dao.entity.NopCodeSymbol;
import io.nop.code.api.dto.CodeSearchResultDTO;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.search.api.ISearchEngine;
import io.nop.search.api.SearchHit;
import io.nop.search.api.SearchRequest;
import io.nop.search.api.SearchResponse;
import io.nop.search.api.SearchType;
class CodeSearchService {

    private static final Logger LOG = LoggerFactory.getLogger(CodeSearchService.class);

    private final IDaoProvider daoProvider;
    private final ISearchEngine searchEngine;
    private final CodeCacheManager cacheManager;

    CodeSearchService(IDaoProvider daoProvider, ISearchEngine searchEngine, CodeCacheManager cacheManager) {
        this.daoProvider = daoProvider;
        this.searchEngine = searchEngine;
        this.cacheManager = cacheManager;
    }

    List<CodeSearchResultDTO> searchCode(String indexId, String query, String searchType,
                                          String language, String filePattern, int limit) {
        if (query == null || query.isEmpty()) return Collections.emptyList();
        if (searchEngine != null) {
            return searchViaEngine(indexId, query, language, filePattern, limit);
        }
        if (daoProvider == null) return Collections.emptyList();
        String type = searchType != null ? searchType : "COMBINED";
        int lim = limit > 0 ? limit : 50;
        switch (type) {
            case "SYMBOL_NAME":
                return searchBySymbolName(indexId, query, language, filePattern, lim);
            case "FULL_TEXT":
                return searchFullText(indexId, query, language, filePattern, lim);
            case "COMBINED":
            default:
                return searchCombined(indexId, query, language, filePattern, lim);
        }
    }

    private List<CodeSearchResultDTO> searchViaEngine(String indexId, String query,
                                                       String language, String filePattern, int limit) {
        int lim = limit > 0 ? limit : 50;
        String topic = "nop-code-" + indexId;
        SearchRequest req = new SearchRequest();
        req.setTopic(topic);
        req.setQuery(query);
        req.setSearchType(SearchType.TEXT);
        req.setLimit(lim);
        Set<String> tags = new HashSet<>();
        if (language != null && !language.isEmpty()) {
            tags.add(language);
        }
        if (!tags.isEmpty()) {
            req.setTags(tags);
            req.setMatchAllTags(false);
        }
        try {
            SearchResponse resp = searchEngine.search(req);
            if (resp == null || resp.getItems() == null) {
                return Collections.emptyList();
            }
            Map<String, String> filePathCache = buildFilePathCache(indexId);
            List<CodeSearchResultDTO> results = new ArrayList<>();
            for (SearchHit hit : resp.getItems()) {
                CodeSearchResultDTO dto = new CodeSearchResultDTO();
                dto.setMatchedSymbolName(hit.getName());
                dto.setMatchedQualifiedName(hit.getTitle());
                dto.setMatchType("SEARCH_ENGINE");
                dto.setScore((double) hit.getScore());
                dto.setContext(hit.getContent());
                dto.setFilePath(hit.getPath() != null ? hit.getPath() : "");
                if (hit.getHighlightedText() != null) {
                    dto.setContext(hit.getHighlightedText());
                }
                if (hit.getTags() != null) {
                    for (String tag : hit.getTags()) {
                        try {
                            CodeSymbolKind.valueOf(tag);
                        } catch (IllegalArgumentException e) {
                            LOG.debug("Ignoring unknown symbol kind tag: {}", tag);
                        }
                    }
                }
                results.add(dto);
            }
            return filterByFilePattern(results, filePattern);
        } catch (Exception e) {
            LOG.warn("Search engine failed, falling back to DB query for index {}", indexId, e);
            if (daoProvider == null) return Collections.emptyList();
            return searchCombined(indexId, query, language, filePattern, lim);
        }
    }

    private List<CodeSearchResultDTO> searchBySymbolName(String indexId, String query,
                                                          String language, String filePattern, int limit) {
        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
        QueryBean qb = new QueryBean();
        qb.addFilter(FilterBeans.eq("indexId", indexId));
        TreeBean nameFilter = FilterBeans.contains("name", query);
        TreeBean qnFilter = FilterBeans.contains("qualifiedName", query);
        qb.addFilter(FilterBeans.or(nameFilter, qnFilter));
        qb.setLimit(limit * 2);
        List<NopCodeSymbol> symbols = symbolDao.findAllByQuery(qb);
        Map<String, String> filePathCache = buildFilePathCache(indexId);
        List<CodeSearchResultDTO> results = new ArrayList<>();
        for (NopCodeSymbol sym : symbols) {
            CodeSearchResultDTO dto = toSearchResult(sym, filePathCache, "SYMBOL_NAME");
            dto.setScore(scoreSymbolNameMatch(query, sym.getName(), sym.getQualifiedName()));
            results.add(dto);
        }
        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        if (results.size() > limit) results = results.subList(0, limit);
        results = filterByLanguage(results, indexId, language, filePathCache);
        return filterByFilePattern(results, filePattern);
    }

    private List<CodeSearchResultDTO> searchFullText(String indexId, String query,
                                                      String language, String filePattern, int limit) {
        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
        QueryBean qb = new QueryBean();
        qb.addFilter(FilterBeans.eq("indexId", indexId));
        TreeBean sigFilter = FilterBeans.contains("signature", query);
        TreeBean docFilter = FilterBeans.contains("documentation", query);
        qb.addFilter(FilterBeans.or(sigFilter, docFilter));
        qb.setLimit(limit * 2);
        List<NopCodeSymbol> symbols = symbolDao.findAllByQuery(qb);
        Map<String, String> filePathCache = buildFilePathCache(indexId);
        List<CodeSearchResultDTO> results = new ArrayList<>();
        for (NopCodeSymbol sym : symbols) {
            CodeSearchResultDTO dto = toSearchResult(sym, filePathCache, "FULL_TEXT");
            dto.setScore(scoreFullTextMatch(query, sym.getSignature(), sym.getDocumentation()));
            results.add(dto);
        }
        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        if (results.size() > limit) results = results.subList(0, limit);
        results = filterByLanguage(results, indexId, language, filePathCache);
        return filterByFilePattern(results, filePattern);
    }

    private List<CodeSearchResultDTO> searchCombined(String indexId, String query,
                                                      String language, String filePattern, int limit) {
        IEntityDao<NopCodeSymbol> symbolDao = daoProvider.daoFor(NopCodeSymbol.class);
        QueryBean qb = new QueryBean();
        qb.addFilter(FilterBeans.eq("indexId", indexId));
        TreeBean nameFilter = FilterBeans.contains("name", query);
        TreeBean qnFilter = FilterBeans.contains("qualifiedName", query);
        TreeBean sigFilter = FilterBeans.contains("signature", query);
        TreeBean docFilter = FilterBeans.contains("documentation", query);
        qb.addFilter(FilterBeans.or(nameFilter, qnFilter, sigFilter, docFilter));
        qb.setLimit(limit * 3);
        List<NopCodeSymbol> symbols = symbolDao.findAllByQuery(qb);
        Map<String, String> filePathCache = buildFilePathCache(indexId);
        Set<String> seen = new HashSet<>();
        List<CodeSearchResultDTO> results = new ArrayList<>();
        for (NopCodeSymbol sym : symbols) {
            String dedupeKey = sym.getId();
            if (seen.contains(dedupeKey)) continue;
            seen.add(dedupeKey);
            CodeSearchResultDTO dto = toSearchResult(sym, filePathCache, "COMBINED");
            dto.setScore(scoreCombined(query, sym.getName(), sym.getQualifiedName(),
                    sym.getSignature(), sym.getDocumentation()));
            results.add(dto);
        }
        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        if (results.size() > limit) results = results.subList(0, limit);
        results = filterByLanguage(results, indexId, language, filePathCache);
        return filterByFilePattern(results, filePattern);
    }

    private Map<String, String> buildFilePathCache(String indexId) {
        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
        QueryBean fq = new QueryBean();
        fq.addFilter(FilterBeans.eq("indexId", indexId));
        fq.addField(QueryFieldBean.forField("id"));
        fq.addField(QueryFieldBean.forField("filePath"));
        List<Map<String, Object>> rows = fileDao.selectFieldsByQuery(fq);
        Map<String, String> cache = new HashMap<>(rows.size());
        for (Map<String, Object> row : rows) {
            Object id = row.get("id");
            Object path = row.get("filePath");
            if (id != null && path != null) {
                cache.put(id.toString(), path.toString());
            }
        }
        return cache;
    }

    private CodeSearchResultDTO toSearchResult(NopCodeSymbol sym, Map<String, String> filePathCache,
                                                String matchType) {
        CodeSearchResultDTO dto = new CodeSearchResultDTO();
        dto.setMatchedSymbolName(sym.getName());
        dto.setMatchedQualifiedName(sym.getQualifiedName());
        dto.setMatchType(matchType);
        dto.setLine(sym.getLine() != null ? sym.getLine() : 0);
        dto.setFilePath(filePathCache.getOrDefault(sym.getFileId(), ""));
        dto.setContext(sym.getSignature());
        return dto;
    }

    private double scoreSymbolNameMatch(String query, String name, String qualifiedName) {
        String lowerQuery = query.toLowerCase();
        if (name == null && qualifiedName == null) return 0.0;
        if (name != null && name.equals(query)) return 1.0;
        if (name != null) {
            double score = scoreSubstring(lowerQuery, name.toLowerCase(), name);
            if (score > 0) return score;
        }
        if (qualifiedName != null) {
            return scoreSubstring(lowerQuery, qualifiedName.toLowerCase(), qualifiedName);
        }
        return 0.0;
    }

    private double scoreSubstring(String lowerQuery, String lowerTarget, String originalTarget) {
        if (lowerTarget.equals(lowerQuery)) return 0.95;
        if (lowerTarget.startsWith(lowerQuery)) {
            return 0.8 - (lowerQuery.length() - lowerTarget.length()) * 0.001;
        }
        int idx = lowerTarget.indexOf(lowerQuery);
        if (idx >= 0) {
            double base = 0.6;
            double posPenalty = idx * 0.02;
            double lenBonus = (double) lowerQuery.length() / lowerTarget.length() * 0.1;
            return Math.max(0.1, base - posPenalty + lenBonus);
        }
        return 0.0;
    }

    private double scoreFullTextMatch(String query, String signature, String documentation) {
        String lowerQuery = query.toLowerCase();
        boolean sigMatch = signature != null && signature.toLowerCase().contains(lowerQuery);
        boolean docMatch = documentation != null && documentation.toLowerCase().contains(lowerQuery);
        if (sigMatch && docMatch) {
            double sigLen = signature.length();
            return 0.5 + Math.min(0.2, (double) query.length() / sigLen);
        }
        if (sigMatch) {
            double sigLen = signature.length();
            return 0.4 + Math.min(0.1, (double) query.length() / sigLen);
        }
        if (docMatch) {
            double docLen = documentation.length();
            return 0.3 + Math.min(0.1, (double) query.length() / docLen);
        }
        return 0.0;
    }

    private double scoreCombined(String query, String name, String qualifiedName,
                                  String signature, String documentation) {
        String lowerQuery = query.toLowerCase();
        if (name != null && name.equals(query)) return 1.0;
        if (name != null) {
            double nameScore = scoreSubstring(lowerQuery, name.toLowerCase(), name);
            if (nameScore > 0.5) return nameScore;
            if (nameScore > 0) return nameScore;
        }
        if (qualifiedName != null) {
            double qnScore = scoreSubstring(lowerQuery, qualifiedName.toLowerCase(), qualifiedName);
            if (qnScore > 0) return qnScore;
        }
        boolean sigMatch = signature != null && signature.toLowerCase().contains(lowerQuery);
        boolean docMatch = documentation != null && documentation.toLowerCase().contains(lowerQuery);
        if (sigMatch && docMatch) return 0.35;
        if (sigMatch) return 0.25;
        if (docMatch) return 0.2;
        return 0.0;
    }

    private List<CodeSearchResultDTO> filterByFilePattern(List<CodeSearchResultDTO> results, String filePattern) {
        if (filePattern == null || filePattern.isEmpty()) return results;
        String pattern = globToRegex(filePattern);
        return results.stream()
                .filter(r -> r.getFilePath() != null && r.getFilePath().matches(pattern))
                .collect(Collectors.toList());
    }

    private String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                sb.append(".*");
            } else if (c == '?') {
                sb.append(".");
            } else if ("\\[]{}()+^$.|".indexOf(c) >= 0) {
                sb.append('\\').append(c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    List<CodeSearchResultDTO> filterByLanguage(List<CodeSearchResultDTO> results,
                                                String indexId, String language,
                                                Map<String, String> filePathCache) {
        if (language == null || language.isEmpty()) return results;
        IEntityDao<NopCodeFile> fileDao = daoProvider.daoFor(NopCodeFile.class);
        QueryBean fq = new QueryBean();
        fq.addFilter(FilterBeans.eq("indexId", indexId));
        fq.addFilter(FilterBeans.eq("language", language));
        Set<String> matchingPaths = new HashSet<>();
        for (NopCodeFile f : fileDao.findAllByQuery(fq)) {
            matchingPaths.add(f.getFilePath());
        }
        if (matchingPaths.isEmpty()) return Collections.emptyList();
        results.removeIf(dto -> !matchingPaths.contains(dto.getFilePath()));
        return results;
    }
}
