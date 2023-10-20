/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.router;

import io.nop.commons.util.StringHelper;
import io.nop.router.trie.MatchResult;
import io.nop.router.trie.Trie;
import io.nop.router.trie.TrieNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TriePathRouter<E> extends Trie<List<RouteValue<E>>> {
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
