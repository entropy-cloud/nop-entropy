/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.router;

import io.nop.commons.util.StringHelper;
import io.nop.router.trie.MatchResult;
import io.nop.router.trie.Trie;
import io.nop.router.trie.TrieNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TriePathRouter<E> extends Trie<List<RouteValue<E>>> {

    public void addMultiPathPattern(String pattern, E value) {
        if (pattern.indexOf('|') < 0) {
            addPathPattern(pattern, value);
        } else {
            List<String> list = StringHelper.split(pattern, '|');
            list.forEach(item -> addPathPattern(item, value));
        }
    }

    public void addMatchAll(E value) {
        List<String> list = List.of("*path");
        makeNode(list, node -> {
            List<String> varNames = List.of("path");
            node.setTillEnd(true);
            addValue(node, varNames, value);
        });
    }

    public void addPathPattern(String pattern, E value) {
        List<String> list = parseRoute(pattern);

        if (list.isEmpty()) {
            makeNode(Collections.emptyList(), node -> {
                addValue(node, list, value);
            });
        } else {
            makeNode(list, node -> {
                List<String> varNames = getVarNames(list);
                String last = varNames.get(varNames.size() - 1);
                boolean tillEnd = last != null && last.startsWith("*");
                node.setTillEnd(tillEnd);
                addValue(node, varNames, value);
            });
        }
    }

    public MatchResult<List<RouteValue<E>>> matchPath(String path) {
        return match(this.parseRoute(path));
    }

    /**
     * 匹配所有可能的路径模式，返回所有匹配结果
     */
    public List<MatchResult<List<RouteValue<E>>>> matchAllPath(String path) {
        return matchAll(this.parseRoute(path));
    }

    /**
     * 匹配所有可能的路径模式，只返回匹配的值集合
     */
    public Set<E> matchAllPathValues(String path) {
        Set<List<RouteValue<E>>> matched = matchAllValues(this.parseRoute(path));
        Set<E> result = new HashSet<>();
        for (List<RouteValue<E>> routeValues : matched) {
            for (RouteValue<E> rv : routeValues) {
                result.add(rv.getValue());
            }
        }
        return result;
    }

    public List<String> parseRoute(String path) {
        if (path.startsWith("/"))
            path = path.substring(1);
        if (path.endsWith("/"))
            path = path.substring(0, path.length() - 1);
        return StringHelper.split(path, '/');
    }

    private List<String> getVarNames(List<String> list) {
        List<String> varNames = new ArrayList<>(list.size());
        for (String name : list) {
            if (name.startsWith("{") && name.endsWith("}")) {
                varNames.add(name.substring(1, name.length() - 1));
            } else {
                varNames.add(null);
            }
        }
        return varNames;
    }

    private void addValue(TrieNode<List<RouteValue<E>>> node, List<String> varNames, E value) {
        List<RouteValue<E>> list = node.getValue();
        if (list == null) {
            list = new ArrayList<>();
            node.setValue(list);
        }
        list.add(new RouteValue<>(varNames, value));
    }
}
