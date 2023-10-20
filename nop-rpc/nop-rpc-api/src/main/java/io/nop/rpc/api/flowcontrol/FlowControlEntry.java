/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.rpc.api.flowcontrol;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class FlowControlEntry {

    public static final String RESOURCE_TYPE_WEB = "web";
    public static final String RESOURCE_TYPE_RPC = "rpc";
    public static final String RESOURCE_TYPE_GATEWAY = "gateway";
    public static final String RESOURCE_TYPE_SQL = "sql";

    private boolean inBound;
    private String contextName = "default";
    private String origin;
    private String bizKey;
    private String resourceType;
    private String resource;

    public boolean isInBound() {
        return inBound;
    }

    public void setInBound(boolean inBound) {
        this.inBound = inBound;
    }

    public String getContextName() {
        return contextName;
    }

    public void setContextName(String contextName) {
        this.contextName = contextName;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getBizKey() {
        return bizKey;
    }

    public void setBizKey(String bizKey) {
        this.bizKey = bizKey;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }
}
