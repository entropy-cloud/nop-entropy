/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.beans.graphql;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.ApiConstants;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.api.core.ApiConstants.GRAPHQL_EXTENSION_MSG;
import static io.nop.api.core.ApiConstants.GRAPHQL_EXTENSION_STATUS;

@DataBean
public final class GraphQLResponseBean implements Serializable {
    private static final long serialVersionUID = 4256913544246822880L;

    private List<GraphQLErrorBean> errors;

    private Object data;

    private Map<String, Object> extensions;

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public boolean hasError() {
        return errors != null && !errors.isEmpty();
    }


    @JsonIgnore
    public int getStatus() {
        if (extensions == null)
            return 0;
        int defaultStatus = hasError() ? -1 : 0;
        return ConvertHelper.toPrimitiveInt(extensions.get(GRAPHQL_EXTENSION_STATUS), defaultStatus, NopException::new);
    }

    public void setStatus(int status) {
        setExtension(GRAPHQL_EXTENSION_STATUS, status);
    }

    public void setErrorCode(String errorCode) {
        setExtension(ApiConstants.GRAPHQL_EXTENSION_ERROR_CODE, errorCode);
    }

    @JsonIgnore
    public String getErrorCode() {
        return (String) getExtension(ApiConstants.GRAPHQL_EXTENSION_ERROR_CODE);
    }

    public Object getExtension(String name) {
        return extensions == null ? null : extensions.get(name);
    }

    public void setExtension(String name, Object value) {
        if (extensions == null)
            extensions = new LinkedHashMap<>();
        if (value == null) {
            extensions.remove(name);
        } else {
            extensions.put(name, value);
        }
    }

    @JsonIgnore
    public Boolean getBizFatal() {
        return ConvertHelper.toBoolean(getExtension(ApiConstants.GRAPHQL_EXTENSION_BIZ_FATAL));
    }

    public void setBizFatal(Boolean value) {
        setExtension(ApiConstants.GRAPHQL_EXTENSION_BIZ_FATAL, value);
    }

    @JsonIgnore
    public String getMsg() {
        if (errors != null && !errors.isEmpty())
            return errors.get(0).getMessage();
        return (String) getExtension(GRAPHQL_EXTENSION_MSG);
    }

    public void setMsg(String msg) {
        setExtension(GRAPHQL_EXTENSION_MSG, msg);
    }

    public ErrorBean toErrorBean() {
        if (!hasError())
            return null;
        ErrorBean error = new ErrorBean();
        String errorCode = getErrorCode();
        if (errorCode != null) {
            error.setErrorCode(errorCode);
        } else {
            error.setErrorCode(errors.get(0).getMessage());
        }
        String msg = getMsg();
        if (msg == null) {
            msg = errors.get(0).getMessage();
        }
        error.setDescription(msg);
        error.setBizFatal(getBizFatal());
        return error;
    }

    public void addError(ErrorBean error) {
        if (errors == null)
            errors = new ArrayList<>();

        setErrorCode(error.getErrorCode());
        setStatus(error.getStatus());
        if (error.isBizFatal())
            setBizFatal(error.isBizFatal());

        GraphQLErrorBean errorBean = new GraphQLErrorBean();
        GraphQLSourceLocation loc = buildLoc(error.getSourceLocation());
        if (loc != null)
            errorBean.setLocations(Arrays.asList(loc));

        errorBean.setMessage(error.getDescription());
        if (errorBean.getMessage() == null) {
            errorBean.setMessage(error.getErrorCode());
        }
        errors.add(errorBean);
    }

    private GraphQLSourceLocation buildLoc(String loc) {
        if (loc == null)
            return null;
        SourceLocation sourceLoc = SourceLocation.parse(loc);
        GraphQLSourceLocation ret = new GraphQLSourceLocation();
        ret.setLine(sourceLoc.getLine());
        ret.setColumn(sourceLoc.getCol());
        return ret;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<GraphQLErrorBean> getErrors() {
        return errors;
    }

    public void setErrors(List<GraphQLErrorBean> errors) {
        this.errors = errors;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, Object> getExtensions() {
        return extensions;
    }

    public void setExtensions(Map<String, Object> extensions) {
        this.extensions = extensions;
    }

    public ApiResponse<Object> toApiResponse() {
        ApiResponse<Object> res = new ApiResponse<>();
        res.setData(getData());
        res.setStatus(getStatus());
        res.setMsg(getMsg());
        res.setCode(getErrorCode());
        res.setBizFatal(getBizFatal());
        res.setWrapper(true);
        return res;
    }

    public Object get(){
        return toApiResponse().get();
    }
}