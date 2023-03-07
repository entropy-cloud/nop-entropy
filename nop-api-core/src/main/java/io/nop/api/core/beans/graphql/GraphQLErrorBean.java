/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.beans.graphql;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@DataBean
public class GraphQLErrorBean implements Serializable {
    private static final long serialVersionUID = -8699938289195810705L;

    private String message;

    private List<GraphQLSourceLocation> locations;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<GraphQLSourceLocation> getLocations() {
        return locations;
    }

    public void setLocations(List<GraphQLSourceLocation> locations) {
        this.locations = locations;
    }

    public void addLocation(GraphQLSourceLocation loc) {
        if (loc != null) {
            if (this.locations == null)
                this.locations = new ArrayList<>(1);
            locations.add(loc);
        }
    }
}
