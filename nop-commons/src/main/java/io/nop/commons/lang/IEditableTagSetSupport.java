package io.nop.commons.lang;

import java.util.HashSet;
import java.util.Set;

public interface IEditableTagSetSupport extends ITagSetSupport {
    @Override
    Set<String> getTagSet();

    void setTagSet(Set<String> tagSet);

    default void removeTag(String tag) {
        Set<String> tags = getTagSet();
        if (tags != null && tags.contains(tag)) {
            tags = new HashSet<>(tags);
            tags.remove(tag);
            setTagSet(tags);
        }
    }

    default void addTag(String tag) {
        Set<String> tags = getTagSet();
        if (tags == null) {
            tags = new HashSet<>();
        }
        if (tags.add(tag))
            setTagSet(tags);
    }
}
