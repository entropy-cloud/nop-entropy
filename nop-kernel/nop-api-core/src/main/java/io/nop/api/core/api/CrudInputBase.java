/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.api;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

@DataBean
public abstract class CrudInputBase implements Serializable {

    private String _chgType;

    private Map<String, Object> _extAttrs;

    public String get_chgType() {
        return _chgType;
    }

    public void set_chgType(String _chgType) {
        this._chgType = _chgType;
    }

    @JsonAnyGetter
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, Object> get_extAttrs() {
        return _extAttrs;
    }

    public void set_extAttrs(Map<String, Object> _extAttrs) {
        this._extAttrs = _extAttrs;
    }

    @JsonAnySetter
    public void set_extAttr(String name, Object value) {
        if (_extAttrs == null)
            _extAttrs = new LinkedHashMap<>();
        _extAttrs.put(name, value);
    }
}
