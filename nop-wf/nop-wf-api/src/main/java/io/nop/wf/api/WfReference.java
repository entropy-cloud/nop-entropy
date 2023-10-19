/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.wf.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;

@DataBean
public class WfReference implements Serializable {

    private static final long serialVersionUID = -5836185931439028454L;

    private final String wfName;
    private final Long wfVersion;
    private final String wfId;

    public WfReference(@JsonProperty("wfName") String wfName,
                       @JsonProperty("wfVersion") Long wfVersion,
                       @JsonProperty("wfId") String wfId) {
        this.wfName = wfName;
        this.wfVersion = wfVersion;
        this.wfId = wfId;
    }

    public String toString() {
        return wfName + '-' + wfVersion + ":" + wfId;
    }

    public String getWfName() {
        return wfName;
    }

    public Long getWfVersion() {
        return wfVersion;
    }

    public String getWfId() {
        return wfId;
    }
}
