/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.api.core.beans;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;

import java.util.List;
import java.util.Map;

@DataBean
public class GraphBean extends ExtensibleBean {
    private List<VertexBean> vertices;
    private List<EdgeBean> edges;

    @DataBean
    public static class VertexBean extends ExtensibleBean {
        private String vertexId;

        public VertexBean() {
        }

        public VertexBean(String vertexId) {
            this.setVertexId(vertexId);
        }

        public String getVertexId() {
            return vertexId;
        }

        public void setVertexId(String vertexId) {
            this.vertexId = vertexId;
        }

        @JsonAnyGetter
        public Map<String, Object> getAttrs() {
            return super.getAttrs();
        }

        @JsonAnySetter
        public void setAttr(String name, Object value) {
            super.setAttr(name, value);
        }
    }

    @DataBean
    public static class EdgeBean extends ExtensibleBean {
        private String edgeId;

        private String source;
        private String target;

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String getEdgeId() {
            return edgeId;
        }

        public void setEdgeId(String edgeId) {
            this.edgeId = edgeId;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }

        @JsonAnyGetter
        public Map<String, Object> getAttrs() {
            return super.getAttrs();
        }

        @JsonAnySetter
        public void setAttr(String name, Object value) {
            super.setAttr(name, value);
        }
    }

    public List<VertexBean> getVertices() {
        return vertices;
    }

    public void setVertices(List<VertexBean> vertices) {
        this.vertices = vertices;
    }

    public List<EdgeBean> getEdges() {
        return edges;
    }

    public void setEdges(List<EdgeBean> edges) {
        this.edges = edges;
    }
}