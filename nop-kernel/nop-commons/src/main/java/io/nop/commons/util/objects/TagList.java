/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.util.objects;

import io.nop.api.core.json.IJsonString;
import io.nop.api.core.util.IDeepCloneable;
import io.nop.commons.util.StringHelper;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 对于a,b这种标签列表的封装，也可以用于CSS class的处理
 */
public class TagList implements Serializable, Iterable<String>, IDeepCloneable, IJsonString {
    private static final long serialVersionUID = -2144290977778763730L;

    private final char separator;
    private final Set<String> tags;

    public TagList() {
        this(',');
    }

    public TagList(char separator) {
        this(separator, new LinkedHashSet<>());
    }

    public TagList(char separator, Set<String> tags) {
        this.tags = tags == null ? new LinkedHashSet<>() : tags;
        this.separator = separator;
    }

    public static TagList parse(String str, char separator) {
        List<String> list = StringHelper.stripedSplit(str, separator);
        if (list == null || list.isEmpty())
            return new TagList(separator);
        return new TagList(separator, new LinkedHashSet<>(list));
    }

    public String toString() {
        if (tags.isEmpty())
            return "";

        StringBuilder sb = new StringBuilder();
        sb.append(separator);
        for (String tag : tags) {
            sb.append(tag).append(separator);
        }
        return sb.toString();
    }

    @Override
    public TagList deepClone() {
        TagList ret = new TagList(separator, new LinkedHashSet<>(tags));
        return ret;
    }

    public boolean isEmpty() {
        return tags.isEmpty();
    }

    public int length() {
        return tags.size();
    }

    @Override
    public Iterator<String> iterator() {
        return tags.iterator();
    }

    protected void checkNotReadOnly() {

    }

    public void add(String tag) {
        checkNotReadOnly();
        tags.add(tag);
    }

    public void add(String tag, String... otherTags) {
        checkNotReadOnly();

        tags.add(tag);
        for (String otherTag : otherTags) {
            tags.add(otherTag);
        }
    }

    public void addAll(Collection<String> otherTags) {
        checkNotReadOnly();
        if (otherTags != null) {
            tags.addAll(otherTags);
        }
    }

    public void remove(String tag) {
        checkNotReadOnly();
        tags.remove(tag);
    }

    public void remove(String tag, String... otherTags) {
        checkNotReadOnly();
        tags.remove(tag);
        for (String otherTag : otherTags) {
            tags.remove(otherTag);
        }
    }

    public void removeAll(Collection<String> otherTags) {
        checkNotReadOnly();
        if (otherTags != null) {
            tags.removeAll(otherTags);
        }
    }

    public boolean contains(String tag) {
        return tags.contains(tag);
    }

    public boolean containsAll(Collection<String> checkTags) {
        if (checkTags == null)
            return false;
        return this.tags.containsAll(checkTags);
    }

    public boolean containsAny(Collection<String> checkTags) {
        if (checkTags == null)
            return false;
        for (String checkTag : checkTags) {
            if (tags.contains(checkTag))
                return true;
        }
        return false;
    }

    public boolean retainAll(Collection<String> checkTags) {
        checkNotReadOnly();
        if (checkTags == null) {
            if (isEmpty())
                return false;

            tags.clear();
            return true;
        }
        return tags.retainAll(checkTags);
    }

    /**
     * 如果tag存在，则删除它并返回false，如果不存在，则添加它并返回true。
     *
     * @param tag
     * @return
     */
    public boolean toggle(String tag) {
        checkNotReadOnly();
        if (tags.remove(tag)) {
            return false;
        }
        tags.add(tag);
        return true;
    }

    public void replace(String oldTag, String newTag) {
        remove(oldTag);
        add(newTag);
    }
}