/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.util;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.json.IJsonString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * 由a,b|c|e,f这种形式的字符串解析得到。多个逗号分隔的字符串通过符号|连接在一起。可以用于表达最简单的与或关系
 */
public class MultiCsvSet implements IJsonString, Iterable<Set<String>> {
    public static MultiCsvSet EMPTY = new MultiCsvSet(Collections.emptyList());
    private final List<Set<String>> sets;

    public MultiCsvSet(List<Set<String>> sets) {
        this.sets = Guard.notEmpty(sets, "sets");
    }

    @StaticFactoryMethod
    public static MultiCsvSet fromText(String text) {
        if (ApiStringHelper.isEmpty(text))
            return MultiCsvSet.EMPTY;

        List<String> list = ApiStringHelper.stripedSplit(text, '|');
        if (list.isEmpty())
            return MultiCsvSet.EMPTY;

        List<Set<String>> sets = new ArrayList<>(list.size());
        for (String item : list) {
            Set<String> csvSet = ConvertHelper.toCsvSet(item);
            if (csvSet.isEmpty())
                continue;
            sets.add(csvSet);
        }
        return new MultiCsvSet(sets);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Set<String> set : sets) {
            if (sb.length() > 0)
                sb.append('|');
            sb.append(ApiStringHelper.join(set, ","));
        }
        return sb.toString();
    }

    @Override
    public Iterator<Set<String>> iterator() {
        return sets.iterator();
    }

    public List<Set<String>> getSets() {
        return sets;
    }

    public boolean allContainsTag(String tag) {
        for (Set<String> set : sets) {
            if (!set.contains(tag))
                return false;
        }
        return true;
    }

    public boolean anyContainsTag(String tag) {
        for (Set<String> set : sets) {
            if (set.contains(tag))
                return true;
        }
        return false;
    }

    public boolean isEmpty() {
        return sets.isEmpty();
    }

    public int size() {
        return sets.size();
    }

    public Set<String> getFirst() {
        return sets.isEmpty() ? null : sets.get(0);
    }
}
