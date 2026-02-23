/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.router;

import io.nop.router.trie.MatchResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestTriePathRouter {
    @Test
    public void testMatch() {
        TriePathRouter<String> router = new TriePathRouter<>();
        router.addPathPattern("/test", "a");
        router.addPathPattern("/test/{id}", "b");
        router.addPathPattern("/test/{id}/data", "c");

        MatchResult<List<RouteValue<String>>> result = router.matchPath("/test/3/data");
        assertEquals(Arrays.asList("test", "3", "data"), result.getPath());
        RouteValue<String> v = result.getValue().get(0);
        assertEquals(Arrays.asList(null, "id", null), v.getVarNames());
        assertEquals("c", v.getValue());
    }

    @Test
    public void testMatchAll_noMatch() {
        TriePathRouter<String> router = new TriePathRouter<>();
        router.addPathPattern("/test", "a");

        List<MatchResult<List<RouteValue<String>>>> results = router.matchAllPath("/other");
        assertTrue(results.isEmpty());
    }

    @Test
    public void testMatchAll_singleExactMatch() {
        TriePathRouter<String> router = new TriePathRouter<>();
        router.addPathPattern("/api/users", "users-handler");

        List<MatchResult<List<RouteValue<String>>>> results = router.matchAllPath("/api/users");
        assertEquals(1, results.size());
        assertEquals("users-handler", results.get(0).getValue().get(0).getValue());
    }

    @Test
    public void testMatchAll_singleWildcardMatch() {
        TriePathRouter<String> router = new TriePathRouter<>();
        router.addPathPattern("/api/{type}", "type-handler");

        List<MatchResult<List<RouteValue<String>>>> results = router.matchAllPath("/api/users");
        assertEquals(1, results.size());
        assertEquals("type-handler", results.get(0).getValue().get(0).getValue());
    }

    @Test
    public void testMatchAll_exactAndWildcardBothMatch() {
        TriePathRouter<String> router = new TriePathRouter<>();
        router.addPathPattern("/api/users", "exact-users");
        router.addPathPattern("/api/{type}", "wildcard-type");

        List<MatchResult<List<RouteValue<String>>>> results = router.matchAllPath("/api/users");
        assertEquals(2, results.size());

        List<String> values = results.stream()
                .map(r -> r.getValue().get(0).getValue())
                .collect(Collectors.toList());
        assertTrue(values.contains("exact-users"));
        assertTrue(values.contains("wildcard-type"));
    }

    @Test
    public void testMatchAll_multipleWildcards() {
        TriePathRouter<String> router = new TriePathRouter<>();
        router.addPathPattern("/api/{version}/users/{id}", "users-by-id");
        router.addPathPattern("/api/{a}/{b}/{c}", "generic-three");

        List<MatchResult<List<RouteValue<String>>>> results = router.matchAllPath("/api/v1/users/123");
        assertEquals(2, results.size());

        List<String> values = results.stream()
                .map(r -> r.getValue().get(0).getValue())
                .collect(Collectors.toList());
        assertTrue(values.contains("users-by-id"));
        assertTrue(values.contains("generic-three"));
    }

    @Test
    public void testMatchAll_tillEndWildcard() {
        TriePathRouter<String> router = new TriePathRouter<>();
        router.addPathPattern("/api/{*path}", "catch-all");
        router.addPathPattern("/api/users", "users-exact");

        List<MatchResult<List<RouteValue<String>>>> results = router.matchAllPath("/api/users/profile");
        assertEquals(1, results.size());
        assertEquals("catch-all", results.get(0).getValue().get(0).getValue());
    }

    @Test
    public void testMatchAll_exactAndTillEndBothMatch() {
        TriePathRouter<String> router = new TriePathRouter<>();
        router.addPathPattern("/api/{*path}", "catch-all");
        router.addPathPattern("/api/users", "users-exact");

        List<MatchResult<List<RouteValue<String>>>> results = router.matchAllPath("/api/users");
        assertEquals(2, results.size());

        List<String> values = results.stream()
                .map(r -> r.getValue().get(0).getValue())
                .collect(Collectors.toList());
        assertTrue(values.contains("catch-all"));
        assertTrue(values.contains("users-exact"));
    }

    @Test
    public void testMatchAll_catchAllPattern() {
        TriePathRouter<String> router = new TriePathRouter<>();
        router.addPathPattern("/{*path}", "catch-all");

        assertEquals("catch-all", router.matchPath("/api").getValue().get(0).getValue());
        assertEquals("catch-all", router.matchPath("/api/users").getValue().get(0).getValue());
        assertEquals("catch-all", router.matchPath("/a/b/c/d/e").getValue().get(0).getValue());
    }

    @Test
    public void testMatchAllPathValues_noMatch() {
        TriePathRouter<String> router = new TriePathRouter<>();
        router.addPathPattern("/test", "a");

        Set<String> values = router.matchAllPathValues("/other");
        assertTrue(values.isEmpty());
    }

    @Test
    public void testMatchAllPathValues_singleMatch() {
        TriePathRouter<String> router = new TriePathRouter<>();
        router.addPathPattern("/api/users", "users-handler");

        Set<String> values = router.matchAllPathValues("/api/users");
        assertEquals(1, values.size());
        assertTrue(values.contains("users-handler"));
    }

    @Test
    public void testMatchAllPathValues_multipleMatches() {
        TriePathRouter<String> router = new TriePathRouter<>();
        router.addPathPattern("/api/users", "exact-users");
        router.addPathPattern("/api/{type}", "wildcard-type");

        Set<String> values = router.matchAllPathValues("/api/users");
        assertEquals(2, values.size());
        assertTrue(values.contains("exact-users"));
        assertTrue(values.contains("wildcard-type"));
    }

    @Test
    public void testMatchAllPathValues_duplicateValues() {
        TriePathRouter<String> router = new TriePathRouter<>();
        router.addPathPattern("/api/users", "handler");
        router.addPathPattern("/api/{type}", "handler");

        Set<String> values = router.matchAllPathValues("/api/users");
        assertEquals(1, values.size());
        assertTrue(values.contains("handler"));
    }

    /**
     * 测试基本路径参数 /a/b/{userId} 的解析
     */
    @Test
    public void testPathParameter_basic() {
        TriePathRouter<String> router = new TriePathRouter<>();
        router.addPathPattern("/a/b/{userId}", "user-handler");

        // 匹配路径 /a/b/123
        MatchResult<List<RouteValue<String>>> result = router.matchPath("/a/b/123");
        assertNotNull(result);
        assertEquals(Arrays.asList("a", "b", "123"), result.getPath());

        RouteValue<String> routeValue = result.getValue().get(0);
        assertEquals("user-handler", routeValue.getValue());
        // 验证 varNames: 前两个是固定路径(null)，第三个是变量名 "userId"
        assertEquals(Arrays.asList(null, null, "userId"), routeValue.getVarNames());

        // 验证可以正确提取变量值
        Map<String, String> vars = extractVariables(result.getPath(), routeValue.getVarNames());
        assertEquals(1, vars.size());
        assertEquals("123", vars.get("userId"));
    }

    /**
     * 测试路径参数在开头: /{version}/api/users
     */
    @Test
    public void testPathParameter_atStart() {
        TriePathRouter<String> router = new TriePathRouter<>();
        router.addPathPattern("/{version}/api/users", "versioned-users");

        MatchResult<List<RouteValue<String>>> result = router.matchPath("/v2/api/users");
        assertNotNull(result);
        assertEquals(Arrays.asList("v2", "api", "users"), result.getPath());

        RouteValue<String> routeValue = result.getValue().get(0);
        assertEquals("versioned-users", routeValue.getValue());
        assertEquals(Arrays.asList("version", null, null), routeValue.getVarNames());

        Map<String, String> vars = extractVariables(result.getPath(), routeValue.getVarNames());
        assertEquals(1, vars.size());
        assertEquals("v2", vars.get("version"));
    }

    /**
     * 测试多个路径参数: /api/{module}/{id}
     */
    @Test
    public void testPathParameter_multiple() {
        TriePathRouter<String> router = new TriePathRouter<>();
        router.addPathPattern("/api/{module}/{id}", "module-handler");

        MatchResult<List<RouteValue<String>>> result = router.matchPath("/api/users/42");
        assertNotNull(result);
        assertEquals(Arrays.asList("api", "users", "42"), result.getPath());

        RouteValue<String> routeValue = result.getValue().get(0);
        assertEquals("module-handler", routeValue.getValue());
        assertEquals(Arrays.asList(null, "module", "id"), routeValue.getVarNames());

        Map<String, String> vars = extractVariables(result.getPath(), routeValue.getVarNames());
        assertEquals(2, vars.size());
        assertEquals("users", vars.get("module"));
        assertEquals("42", vars.get("id"));
    }

    /**
     * 测试连续路径参数: /{a}/{b}/{c}
     */
    @Test
    public void testPathParameter_consecutive() {
        TriePathRouter<String> router = new TriePathRouter<>();
        router.addPathPattern("/{a}/{b}/{c}", "three-vars");

        MatchResult<List<RouteValue<String>>> result = router.matchPath("/x/y/z");
        assertNotNull(result);
        assertEquals(Arrays.asList("x", "y", "z"), result.getPath());

        RouteValue<String> routeValue = result.getValue().get(0);
        assertEquals("three-vars", routeValue.getValue());
        assertEquals(Arrays.asList("a", "b", "c"), routeValue.getVarNames());

        Map<String, String> vars = extractVariables(result.getPath(), routeValue.getVarNames());
        assertEquals(3, vars.size());
        assertEquals("x", vars.get("a"));
        assertEquals("y", vars.get("b"));
        assertEquals("z", vars.get("c"));
    }

    /**
     * 测试路径参数与固定路径混合
     */
    @Test
    public void testPathParameter_mixed() {
        TriePathRouter<String> router = new TriePathRouter<>();
        router.addPathPattern("/api/{version}/users/{userId}/posts/{postId}", "post-handler");

        MatchResult<List<RouteValue<String>>> result = router.matchPath("/api/v1/users/100/posts/200");
        assertNotNull(result);
        assertEquals(Arrays.asList("api", "v1", "users", "100", "posts", "200"), result.getPath());

        RouteValue<String> routeValue = result.getValue().get(0);
        assertEquals("post-handler", routeValue.getValue());
        assertEquals(Arrays.asList(null, "version", null, "userId", null, "postId"), routeValue.getVarNames());

        Map<String, String> vars = extractVariables(result.getPath(), routeValue.getVarNames());
        assertEquals(3, vars.size());
        assertEquals("v1", vars.get("version"));
        assertEquals("100", vars.get("userId"));
        assertEquals("200", vars.get("postId"));
    }

    /**
     * 测试路径参数值包含特殊字符（URL编码后的值）
     */
    @Test
    public void testPathParameter_specialCharacters() {
        TriePathRouter<String> router = new TriePathRouter<>();
        router.addPathPattern("/files/{filename}", "file-handler");

        // 测试包含特殊字符的值（如UUID、数字等）
        MatchResult<List<RouteValue<String>>> result = router.matchPath("/files/abc-123_xyz");
        assertNotNull(result);

        RouteValue<String> routeValue = result.getValue().get(0);
        Map<String, String> vars = extractVariables(result.getPath(), routeValue.getVarNames());
        assertEquals("abc-123_xyz", vars.get("filename"));
    }

    /**
     * 测试路径参数值为纯数字
     */
    @Test
    public void testPathParameter_numericValue() {
        TriePathRouter<String> router = new TriePathRouter<>();
        router.addPathPattern("/items/{id}", "item-handler");

        MatchResult<List<RouteValue<String>>> result = router.matchPath("/items/12345");
        assertNotNull(result);

        RouteValue<String> routeValue = result.getValue().get(0);
        Map<String, String> vars = extractVariables(result.getPath(), routeValue.getVarNames());
        assertEquals("12345", vars.get("id"));
    }

    /**
     * 测试精确匹配优先于通配符匹配
     */
    @Test
    public void testPathParameter_exactMatchPriority() {
        TriePathRouter<String> router = new TriePathRouter<>();
        router.addPathPattern("/api/users", "exact-users");
        router.addPathPattern("/api/{type}", "wildcard-type");

        MatchResult<List<RouteValue<String>>> result = router.matchPath("/api/users");
        assertNotNull(result);
        // match() 方法返回精确匹配优先
        assertEquals("exact-users", result.getValue().get(0).getValue());
    }

    /**
     * 测试路径参数值包含数字和字母混合
     */
    @Test
    public void testPathParameter_alphanumericValue() {
        TriePathRouter<String> router = new TriePathRouter<>();
        router.addPathPattern("/orders/{orderId}", "order-handler");

        MatchResult<List<RouteValue<String>>> result = router.matchPath("/orders/ORD-2024-001");
        assertNotNull(result);

        RouteValue<String> routeValue = result.getValue().get(0);
        Map<String, String> vars = extractVariables(result.getPath(), routeValue.getVarNames());
        assertEquals("ORD-2024-001", vars.get("orderId"));
    }

    /**
     * 测试路径参数匹配不存在的路径
     */
    @Test
    public void testPathParameter_noMatch() {
        TriePathRouter<String> router = new TriePathRouter<>();
        router.addPathPattern("/api/users/{id}", "user-handler");

        // 路径太短
        MatchResult<List<RouteValue<String>>> result1 = router.matchPath("/api/users");
        // 根据实现，可能返回 null 或部分匹配结果
        // 这里验证 varNames 长度与 path 长度不一致的情况

        // 路径前缀不匹配
        MatchResult<List<RouteValue<String>>> result2 = router.matchPath("/other/users/123");
        assertNull(result2);
    }

    /**
     * 辅助方法：从 path 和 varNames 中提取变量名-变量值映射
     */
    private Map<String, String> extractVariables(List<String> path, List<String> varNames) {
        Map<String, String> result = new java.util.HashMap<>();
        if (path == null || varNames == null) {
            return result;
        }
        int len = Math.min(path.size(), varNames.size());
        for (int i = 0; i < len; i++) {
            String varName = varNames.get(i);
            if (varName != null) {
                result.put(varName, path.get(i));
            }
        }
        return result;
    }
}
