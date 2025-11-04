/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.diff;

import java.util.List;
import java.util.Map;

public class DiffValue implements IDiffValue {
    private DiffType diffType;
    private Object oldValue;
    private Object newValue;

    private Map<String, IDiffValue> propDiffs;

    private List<IDiffValue> elementDiffs;

    private String keyProp;

    private Map<String, IDiffValue> keyedElementDiffs;

    public DiffValue(DiffType diffType, Object oldValue, Object newValue) {
        this.diffType = diffType;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public static DiffValue same(Object oldValue, Object newValue) {
        return new DiffValue(DiffType.same, oldValue, newValue);
    }

    public static DiffValue replace(Object oldValue, Object newValue) {
        return new DiffValue(DiffType.replace, oldValue, newValue);
    }

    public static DiffValue remove(Object oldValue) {
        return new DiffValue(DiffType.remove, oldValue, null);
    }

    public static DiffValue update(Object oldValue, Object newValue) {
        return new DiffValue(DiffType.update, oldValue, newValue);
    }

    public static DiffValue add(Object newValue) {
        return new DiffValue(DiffType.add, null, newValue);
    }



    @Override
    public DiffType getDiffType() {
        return diffType;
    }

    public void setDiffType(DiffType diffType) {
        this.diffType = diffType;
    }

    @Override
    public Object getOldValue() {
        return oldValue;
    }

    public void setOldValue(Object oldValue) {
        this.oldValue = oldValue;
    }

    @Override
    public Object getNewValue() {
        return newValue;
    }

    public void setNewValue(Object newValue) {
        this.newValue = newValue;
    }

    @Override
    public Map<String, IDiffValue> getPropDiffs() {
        return propDiffs;
    }

    public void setPropDiffs(Map<String, IDiffValue> propDiffs) {
        this.propDiffs = propDiffs;
    }

    @Override
    public List<IDiffValue> getElementDiffs() {
        return elementDiffs;
    }

    public void setElementDiffs(List<IDiffValue> elementDiffs) {
        this.elementDiffs = elementDiffs;
    }

    public String getKeyProp() {
        return keyProp;
    }

    public void setKeyProp(String keyProp) {
        this.keyProp = keyProp;
    }

    public Map<String, IDiffValue> getKeyedElementDiffs() {
        return keyedElementDiffs;
    }

    public void setKeyedElementDiffs(Map<String, IDiffValue> keyedElementDiffs) {
        this.keyedElementDiffs = keyedElementDiffs;
    }
}