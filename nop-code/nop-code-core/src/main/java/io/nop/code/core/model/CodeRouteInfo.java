package io.nop.code.core.model;

import io.nop.api.core.annotations.data.DataBean;

@DataBean
public class CodeRouteInfo {
    private String httpMethod;
    private String routePath;
    private String handlerSymbolId;
    private String handlerQualifiedName;

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getRoutePath() {
        return routePath;
    }

    public void setRoutePath(String routePath) {
        this.routePath = routePath;
    }

    public String getHandlerSymbolId() {
        return handlerSymbolId;
    }

    public void setHandlerSymbolId(String handlerSymbolId) {
        this.handlerSymbolId = handlerSymbolId;
    }

    public String getHandlerQualifiedName() {
        return handlerQualifiedName;
    }

    public void setHandlerQualifiedName(String handlerQualifiedName) {
        this.handlerQualifiedName = handlerQualifiedName;
    }
}
