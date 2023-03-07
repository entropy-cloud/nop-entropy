/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.dao.shard;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;
import java.util.Objects;

@DataBean
public class ShardSelection implements Serializable {

    private static final long serialVersionUID = -8254474213500016912L;

    private final String querySpace;
    private final String shardName;

    @JsonCreator
    public ShardSelection(@JsonProperty("querySpace") String querySpace, @JsonProperty("shardName") String shardName) {
        this.querySpace = querySpace;
        this.shardName = shardName;
    }

    public int hashCode() {
        int h = (querySpace == null ? 0 : querySpace.hashCode());
        h = h * 31 + (shardName == null ? 0 : shardName.hashCode());

        return h;
    }

    public boolean equals(Object o) {
        if (o == null)
            return false;

        if (this == o)
            return true;
        ShardSelection other = (ShardSelection) o;
        return Objects.equals(shardName, other.shardName) && Objects.equals(querySpace, other.querySpace);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ShardSelection[querySpace=");
        sb.append(querySpace).append(",name=").append(shardName);
        sb.append("]");
        return sb.toString();
    }

    public String getQuerySpace() {
        return querySpace;
    }

    public String getShardName() {
        return shardName;
    }
}