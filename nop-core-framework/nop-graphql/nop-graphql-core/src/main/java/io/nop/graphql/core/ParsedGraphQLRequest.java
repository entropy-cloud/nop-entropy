/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.graphql.core;

import io.nop.graphql.core.ast.GraphQLDocument;
import io.nop.graphql.core.ast.GraphQLOperationType;

import java.util.Map;

public class ParsedGraphQLRequest {
    private String operationId;
    private GraphQLDocument document;
    private Map<String, Object> variables;
    private Map<String, Object> extensions;

    public ParsedGraphQLRequest() {
    }

    public ParsedGraphQLRequest(GraphQLDocument doc, Map<String, Object> vars) {
        this.document = doc;
        this.variables = vars;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public void addOperation(GraphQLOperationType opType, String operation, Object request) {
        if (document == null)
            document = new GraphQLDocument();

    }

    public GraphQLDocument getDocument() {
        return document;
    }

    public void setDocument(GraphQLDocument document) {
        this.document = document;
    }

    public Map<String, Object> getVariables() {
        return variables;
    }

    public void setVariables(Map<String, Object> variables) {
        this.variables = variables;
    }

    public Map<String, Object> getExtensions() {
        return extensions;
    }

    public void setExtensions(Map<String, Object> extensions) {
        this.extensions = extensions;
    }

    public Object getVariable(String name) {
        return variables == null ? null : variables.get(name);
    }

    public Object getExtension(String name) {
        return extensions == null ? null : extensions.get(name);
    }
}
