/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.fsm.model;

import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.api.core.json.IJsonString;
import io.nop.api.core.util.Guard;
import io.nop.commons.collections.ListFunctions;
import io.nop.commons.util.StringHelper;

import java.util.Collections;
import java.util.List;

public class StateId implements IJsonString, Comparable<StateId> {
    /**
     * 包含所有父id和本级id
     */
    private final List<String> ids;

    private final String text;

    private StateId(List<String> ids) {
        this.ids = Guard.notEmpty(ids, "state ids");
        if (ids.size() == 1) {
            text = ids.get(0);
        } else {
            text = StringHelper.join(ids, "/");
        }
    }

    public static StateId fromIds(List<String> ids) {
        return new StateId(ids);
    }

    @StaticFactoryMethod
    public static StateId fromText(String text) {
        if (StringHelper.isEmpty(text))
            return null;

        int pos = text.indexOf('/');
        if (pos < 0) {
            return new StateId(text);
        } else {
            return new StateId(StringHelper.split(text, '/'));
        }
    }

    public StateId(String stateId) {
        this(Collections.singletonList(stateId));
    }

    public String getParentId() {
        if (ids.size() == 1)
            return null;

        if (ids.size() == 2)
            return ids.get(0);

        StringBuilder sb = new StringBuilder();
        for (String id : ids) {
            if (sb.length() > 0)
                sb.append('/');
            sb.append(id);
        }
        return sb.toString();
    }

    public String getLeafId() {
        return ids.get(ids.size() - 1);
    }

    public List<String> getIds() {
        return ids;
    }

    public int size() {
        return ids.size();
    }

    public StateId subState(String stateId) {
        Guard.notEmpty(stateId, "stateId");

        List<String> subIds = ListFunctions.concat(ids, stateId);
        return new StateId(subIds);
    }

    @Override
    public int compareTo(StateId o) {
        return text.compareTo(o.text);
    }

    @Override
    public String toString() {
        return text;
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;

        if (!(o instanceof StateId))
            return false;

        return ((StateId) o).text.equals(text);
    }
}