/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 标签的存储格式为",a,b,", 在首尾都增加了逗号，因此作为数据库字段存储时可以通过 like '%,a,%'这种方式来匹配。
 *
 * @author canonical_entropy@163.com
 */
public class TagsHelper {

    public static Set<String> parse(String s, char separator) {
        List<String> list = StringHelper.stripedSplit(s, separator);
        if (list == null)
            return null;
        if (list.isEmpty())
            return Collections.emptySet();
        return new LinkedHashSet<>(list);
    }

    public static String toString(Collection<String> tagSet) {
        return toString(tagSet, ',');
    }

    public static String toString(Collection<String> tagSet, char separator) {
        if (tagSet == null)
            return null;
        if (tagSet.isEmpty())
            return "";

        StringBuilder sb = new StringBuilder();
        sb.append(separator);
        for (String tag : tagSet) {
            sb.append(tag).append(separator);
        }
        return sb.toString();
    }

    public static boolean contains(Collection<String> tags, String tag) {
        if (tags == null || tags.isEmpty())
            return false;
        return tags.contains(tag);
    }

    public static boolean containsAny(Collection<String> tags, Collection<String> filters) {
        if (filters == null || filters.isEmpty())
            return true;

        if (tags == null || tags.isEmpty())
            return false;

        for (String filter : filters) {
            if (tags.contains(filter))
                return true;
        }
        return false;
    }

    public static boolean containsAll(Collection<String> tags, Collection<String> filters) {
        if (filters == null || filters.isEmpty())
            return true;

        if (tags == null || tags.isEmpty())
            return false;

        return tags.containsAll(filters);
    }

    public static Set<String> remove(Set<String> tags, String tag) {
        if (tags == null || tags.isEmpty())
            return tags;
        if (!tags.contains(tag))
            return tags;
        Set<String> ret = new LinkedHashSet<>(tags);
        ret.remove(tag);
        return ret;
    }

    public static Set<String> add(Set<String> tags, String tag) {
        if (tags == null) {
            tags = new LinkedHashSet<>();
            tags.add(tag);
            return tags;
        } else {
            Set<String> ret = new LinkedHashSet<>(tags);
            ret.add(tag);
            return ret;
        }
    }

    public static Set<String> merge(Collection<String> tagsA, Collection<String> tagsB) {
        if (tagsA == null)
            tagsA = Collections.emptySet();
        if (tagsB == null)
            tagsB = Collections.emptySet();

        Set<String> ret = new LinkedHashSet<>(tagsA.size() + tagsB.size());
        ret.addAll(tagsA);
        ret.addAll(tagsB);
        return ret;
    }

    public static Set<String> toSet(String[] tags) {
        return new LinkedHashSet<>(Arrays.asList(tags));
    }
}