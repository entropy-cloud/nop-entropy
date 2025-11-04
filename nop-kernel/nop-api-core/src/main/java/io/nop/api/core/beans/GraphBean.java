/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.beans;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.meta.PropMeta;

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

        @PropMeta(propId = 1)
        public String getVertexId() {
            return vertexId;
        }

        public void setVertexId(String vertexId) {
            this.vertexId = vertexId;
        }

        @PropMeta(propId = 2)
        public Map<String, Object> getAttrs() {
            return super.getAttrs();
        }

        public void setAttr(String name, Object value) {
            super.setAttr(name, value);
        }
    }

    @DataBean
    public static class EdgeBean extends ExtensibleBean {
        private String edgeId;

        private String source;
        private String target;

        @PropMeta(propId = 1)
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public String getEdgeId() {
            return edgeId;
        }

        public void setEdgeId(String edgeId) {
            this.edgeId = edgeId;
        }

        @PropMeta(propId = 2)
        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        @PropMeta(propId = 3)
        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }

        @PropMeta(propId = 4)
        public Map<String, Object> getAttrs() {
            return super.getAttrs();
        }

        public void setAttr(String name, Object value) {
            super.setAttr(name, value);
        }
    }

    @PropMeta(propId = 1)
    public List<VertexBean> getVertices() {
        return vertices;
    }

    public void setVertices(List<VertexBean> vertices) {
        this.vertices = vertices;
    }

    @PropMeta(propId = 2)
    public List<EdgeBean> getEdges() {
        return edges;
    }

    public void setEdges(List<EdgeBean> edges) {
        this.edges = edges;
    }

    @PropMeta(propId = 3)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, Object> getAttrs() {
        return super.getAttrs();
    }

}