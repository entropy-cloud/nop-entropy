/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.orm.pdm;

import java.util.Map;
import java.util.Set;

public class PdmElement {
    private String id;
    private String name;
    private String code;
    private String comment;
    private Set<String> tagSet;
    private Map<String, Object> extProps;
    private boolean notGenCode;

    public boolean containsTag(String tag) {
        return tagSet != null && tagSet.contains(tag);
    }

    public boolean isNotGenCode() {
        return notGenCode;
    }

    public void setNotGenCode(boolean notGenCode) {
        this.notGenCode = notGenCode;
    }

    public Set<String> getTagSet() {
        return tagSet;
    }

    public void setTagSet(Set<String> tagSet) {
        this.tagSet = tagSet;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getTag() {
        if (tagSet == null || tagSet.isEmpty())
            return null;
        return tagSet.iterator().next();
    }

    public Map<String, Object> getExtProps() {
        return extProps;
    }

    public void setExtProps(Map<String, Object> extProps) {
        this.extProps = extProps;
    }
}
