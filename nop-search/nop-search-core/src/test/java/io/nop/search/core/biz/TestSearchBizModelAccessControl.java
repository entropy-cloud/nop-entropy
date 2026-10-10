package io.nop.search.core.biz;

import io.nop.api.core.auth.IUserContext;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.config.AppConfig;
import io.nop.search.api.ISearchEngine;
import io.nop.search.api.SearchRequest;
import io.nop.search.api.SearchResponse;
import io.nop.search.api.SearchableDoc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * P1-4.4-S-06（plan 2394）：SearchEngineBizModel 访问控制判别——
 * checkAllowAccess fail-closed（无用户上下文/非 admin 拒绝，admin 放行）；
 * indexDir 路径白名单（配置 nop.search.index-dir-roots 非空时，根外绝对路径拒绝）。
 */
public class TestSearchBizModelAccessControl {

    @TempDir
    Path dir;

    private static IUserContext userInRole(String... roles) {
        return (IUserContext) Proxy.newProxyInstance(
                TestBizModelAccessUser.class.getClassLoader(),
                new Class[]{IUserContext.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "isUserInRole":
                            return java.util.Arrays.asList(roles).contains(args[0]);
                        case "getUserId":
                            return "test-user";
                        default:
                            return null;
                    }
                });
    }

    /** 占位类型仅用于代理命名空间。 */
    static class TestBizModelAccessUser {
    }

    @BeforeEach
    void clearContext() {
        IUserContext.set(null);
        AppConfig.getConfigProvider().updateConfigValue(SearchEngineBizModel.CFG_SEARCH_INDEX_DIR_ROOTS, null);
    }

    @AfterEach
    void resetContext() {
        IUserContext.set(null);
        AppConfig.getConfigProvider().updateConfigValue(SearchEngineBizModel.CFG_SEARCH_INDEX_DIR_ROOTS, null);
    }

    private SearchEngineBizModel newBizModel() {
        SearchEngineBizModel bizModel = new SearchEngineBizModel();
        bizModel.setSearchEngine(new StubEngine());
        return bizModel;
    }

    static class StubEngine implements ISearchEngine {
        @Override
        public SearchResponse search(SearchRequest request) {
            return new SearchResponse();
        }

        @Override
        public SearchableDoc getDoc(String topic, String docId) {
            return null;
        }

        @Override
        public java.util.List<SearchableDoc> getDocsByTerm(String topic, String term) {
            return Collections.emptyList();
        }

        @Override
        public Map<String, java.util.List<String>> analyzeDoc(SearchableDoc doc) {
            return Collections.emptyMap();
        }

        @Override
        public java.util.List<String> analyzeQuery(String query) {
            return Collections.emptyList();
        }

        @Override
        public void addDocs(String topic, java.util.List<SearchableDoc> docs) {
        }

        @Override
        public void removeDocs(String topic, java.util.List<String> docIds) {
        }

        @Override
        public void refreshBlocking(String topic) {
        }

        @Override
        public void removeTopic(String topic) {
        }
    }

    @Test
    public void testCheckAllowAccessFailsClosedWithoutUserContext() throws Exception {
        SearchEngineBizModel bizModel = newBizModel();
        var method = SearchEngineBizModel.class.getDeclaredMethod("checkAllowAccess",
                String.class, String.class, java.util.List.class);
        method.setAccessible(true);

        IUserContext.set(null);
        var ex = assertThrows(Exception.class,
                () -> method.invoke(bizModel, "indexDir", "topic-x", null));
        var cause = ex.getCause();
        assertNotNull(cause);
        assertEquals("nop.err.search.no-permission", ((NopException) cause).getErrorCode(),
                "no user context must be denied (P1-4.4-S-06)");
    }

    @Test
    public void testCheckAllowAccessDeniesNonAdmin() throws Exception {
        SearchEngineBizModel bizModel = newBizModel();
        var method = SearchEngineBizModel.class.getDeclaredMethod("checkAllowAccess",
                String.class, String.class, java.util.List.class);
        method.setAccessible(true);

        IUserContext.set(userInRole("user"));
        var ex = assertThrows(Exception.class,
                () -> method.invoke(bizModel, "indexDir", "topic-x", null));
        assertEquals("nop.err.search.no-permission",
                ((NopException) ex.getCause()).getErrorCode(), "non-admin must be denied");
    }

    @Test
    public void testCheckAllowAccessAllowsAdmin() throws Exception {
        SearchEngineBizModel bizModel = newBizModel();
        var method = SearchEngineBizModel.class.getDeclaredMethod("checkAllowAccess",
                String.class, String.class, java.util.List.class);
        method.setAccessible(true);

        IUserContext.set(userInRole("admin"));
        method.invoke(bizModel, "indexDir", "topic-x", null); // no exception
    }

    @Test
    public void testIndexDirRejectsPathOutsideAllowedRoots() throws Exception {
        AppConfig.getConfigProvider().updateConfigValue(SearchEngineBizModel.CFG_SEARCH_INDEX_DIR_ROOTS,
                dir.resolve("allowed").toString());

        SearchEngineBizModel bizModel = newBizModel();
        IUserContext.set(userInRole("admin"));

        File outside = Files.write(dir.resolve("outside"), "x".getBytes()).toFile();
        var ex = assertThrows(NopException.class,
                () -> bizModel.indexDir("topic-x", outside.getAbsolutePath(), null),
                "absolute path outside the allowed roots must be rejected (P1-4.4-S-06)");
        assertEquals("nop.err.search.no-permission", ex.getErrorCode());
    }

    @Test
    public void testIndexDirAllowsPathInsideAllowedRoots() throws Exception {
        File allowed = Files.createDirectories(dir.resolve("allowed")).toFile();
        AppConfig.getConfigProvider().updateConfigValue(SearchEngineBizModel.CFG_SEARCH_INDEX_DIR_ROOTS,
                allowed.getAbsolutePath());

        SearchEngineBizModel bizModel = newBizModel();
        IUserContext.set(userInRole("admin"));
        bizModel.setSearchableDocEnhancer(null);

        // 空目录 indexAll 不产文档，但路径校验必须放行（不抛 no-permission）
        bizModel.indexDir("topic-x", allowed.getAbsolutePath(), null);
    }

    @Test
    public void testAbsolutePathDeniedWhenNoRootsConfigured() throws Exception {
        SearchEngineBizModel bizModel = newBizModel();
        IUserContext.set(userInRole("admin"));

        // 默认（未配置 roots）直解析分支仅允许工作目录相对路径：绝对路径拒绝
        File outside = Files.write(dir.resolve("abs-target"), "x".getBytes()).toFile();
        var ex = assertThrows(NopException.class,
                () -> bizModel.indexDir("topic-x", outside.getAbsolutePath(), null),
                "absolute paths must be denied when no roots are configured");
        assertEquals("nop.err.search.no-permission", ex.getErrorCode());
    }

    @Test
    public void testLocatorResolvedPathExemptFromRootWhitelist() throws Exception {
        // resourceLocator 是应用显式配置的信任映射——豁免白名单
        File target = Files.write(dir.resolve("located.md"), "# doc".getBytes()).toFile();
        SearchEngineBizModel bizModel = newBizModel();
        bizModel.setResourceLocator(path -> new io.nop.core.resource.impl.FileResource(target));
        IUserContext.set(userInRole("admin"));

        assertSame(target, bizModel.getLocalFile("located.md"),
                "locator-resolved paths are exempt from the whitelist (trusted mapping)");
    }
}
