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

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
