package io.nop.search.core.biz;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.config.IConfigReference;
import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResourceLocator;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.search.api.ISearchEngine;
import io.nop.search.api.SearchRequest;
import io.nop.search.api.SearchResponse;
import io.nop.search.api.SearchableDoc;
import io.nop.search.core.index.BuildIndexTool;
import io.nop.search.core.index.ISearchableDocEnhancer;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;

import static io.nop.search.core.SearchCoreConstants.ACTION_ADD_DOC;
import static io.nop.search.core.SearchCoreConstants.ACTION_GET_DOC;
import static io.nop.search.core.SearchCoreConstants.ACTION_INDEX_DIR;
import static io.nop.search.core.SearchCoreConstants.ACTION_REMOVE_DOCS;
import static io.nop.search.core.SearchCoreConstants.ACTION_SEARCH;

@BizModel("SearchEngine")
public class SearchEngineBizModel {

    private ISearchEngine searchEngine;

    private IResourceLocator resourceLocator;

    private ISearchableDocEnhancer searchableDocEnhancer;

    @Inject
    public void setSearchEngine(ISearchEngine searchEngine) {
        this.searchEngine = searchEngine;
    }

    @Inject
    public void setSearchableDocEnhancer(@Nullable ISearchableDocEnhancer docEnhancer) {
        this.searchableDocEnhancer = docEnhancer;
    }

    public void setResourceLocator(IResourceLocator resourceLocator) {
        this.resourceLocator = resourceLocator;
    }

    public IResourceLocator getResourceLocator() {
        return resourceLocator;
    }

    public ISearchableDocEnhancer getSearchableDocEnhancer() {
        return searchableDocEnhancer;
    }

    @BizMutation
    public void indexDir(
            @Name("topic") String topic,
            @Name("path") String path,
            @Name("pattern") @Optional String pattern) {
        checkAllowAccess(ACTION_INDEX_DIR, topic, null);

        BuildIndexTool tool = new BuildIndexTool(searchEngine);
        tool.setSearchableDocEnhancer(getSearchableDocEnhancer());

        File dir = getLocalFile(path);
        tool.indexAll(topic, dir, getFilter(pattern));
    }

    private static final SourceLocation s_loc = SourceLocation.fromClass(SearchEngineBizModel.class);

    static final String ROLE_ADMIN = "admin";
    private static final String ARG_ACTION = "action";
    private static final String ARG_TOPIC = "topic";

    /**
     * P1-4.4-S-06（plan 2394）：indexDir 允许根目录白名单（csv）。默认空=直解析分支仅允许
     * 工作目录相对路径；绝对路径必须规范化后落在某根内。
     */
    public static final IConfigReference<String> CFG_SEARCH_INDEX_DIR_ROOTS = AppConfig
            .varRef(s_loc, "nop.search.index-dir-roots", String.class, null);

    private static final ErrorCode ERR_SEARCH_NO_PERMISSION = ErrorCode.define(
            "nop.err.search.no-permission",
            "当前用户无权执行搜索管理操作（需要 admin 角色）:action={action},topic={topic}",
            ARG_ACTION, ARG_TOPIC);

    /**
     * P1-4.4-S-06（plan 2394）：fail-closed 授权钩子——无用户上下文与 admin 之外一律
     * 拒绝（平台层鉴权不覆盖业务级 topic/路径授权，空实现即模块授权缺失）。
     */
    protected void checkAllowAccess(String action, String topic, List<String> docIds) {
        IUserContext user = IUserContext.get();
        if (user == null || !user.isUserInRole(ROLE_ADMIN)) {
            throw new NopException(ERR_SEARCH_NO_PERMISSION)
                    .param(ARG_ACTION, action).param(ARG_TOPIC, topic);
        }
    }

    /**
     * P1-4.4-S-06（plan 2394）：indexDir 路径白名单——resourceLocator 解析走应用显式
     * 配置的信任映射（豁免）；直解析分支受白名单约束（见 CFG_SEARCH_INDEX_DIR_ROOTS）。
     */
    protected File getLocalFile(String path) {
        if (resourceLocator != null) {
            File located = resourceLocator.getResource(path).toFile();
            if (located == null)
                throw new IllegalArgumentException("path not resolve to file:path=" + path);
            return located;
        }

        File file;
        if (path.startsWith("file:"))
            file = VirtualFileSystem.instance().getResource(path).toFile();
        else
            file = FileHelper.resolveFile(path);

        String roots = CFG_SEARCH_INDEX_DIR_ROOTS.get();
        if (StringHelper.isEmpty(roots)) {
            if (file.isAbsolute())
                throw new NopException(ERR_SEARCH_NO_PERMISSION)
                        .param(ARG_ACTION, ACTION_INDEX_DIR).param(ARG_TOPIC, path);
        } else {
            String canonical;
            try {
                canonical = file.getCanonicalPath();
            } catch (IOException e) {
                throw new NopException(ERR_SEARCH_NO_PERMISSION)
                        .param(ARG_ACTION, ACTION_INDEX_DIR).param(ARG_TOPIC, path);
            }
            boolean allowed = false;
            for (String root : roots.split(",")) {
                File rootDir = FileHelper.resolveFile(root.trim());
                String rootCanonical;
                try {
                    rootCanonical = rootDir.getCanonicalPath();
                } catch (IOException e) {
                    continue;
                }
                if (canonical.equals(rootCanonical) || canonical.startsWith(rootCanonical + File.separator)) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                throw new NopException(ERR_SEARCH_NO_PERMISSION)
                        .param(ARG_ACTION, ACTION_INDEX_DIR).param(ARG_TOPIC, path);
            }
        }
        return file;
    }

    protected BiPredicate<String, File> getFilter(String pattern) {
        if (StringHelper.isEmpty(pattern)) {
            return BuildIndexTool::isDefaultIndexable;
        } else {
            Pattern patternObj = Pattern.compile(pattern);
            return (path, file) -> patternObj.matcher(path).matches();
        }
    }

    @BizQuery
    public CompletionStage<SearchResponse> searchAsync(@Name("request") SearchRequest request) {
        checkAllowAccess(ACTION_SEARCH, request.getTopic(), null);

        return searchEngine.searchAsync(request);
    }

    @BizQuery
    public SearchableDoc getDoc(@Name("topic") String topic, @Name("docId") String docId) {
        checkAllowAccess(ACTION_GET_DOC, topic, Collections.singletonList(docId));
        return searchEngine.getDoc(topic, docId);
    }

    @BizMutation
    public void addDoc(@Name("topic") String topic, @Name("doc") SearchableDoc doc) {
        checkAllowAccess(ACTION_ADD_DOC, topic, null);

        searchEngine.addDoc(topic, doc);
    }

    @BizMutation
    public void addDocs(@Name("topic") String topic, @Name("docs") List<SearchableDoc> docs) {
        checkAllowAccess(ACTION_ADD_DOC, topic, null);

        searchEngine.addDocs(topic, docs);
    }

    @BizMutation
    public void removeDocs(@Name("topic") String topic, @Name("docIds") List<String> docIds) {
        checkAllowAccess(ACTION_REMOVE_DOCS, topic, docIds);

        searchEngine.removeDocs(topic, docIds);
    }
}