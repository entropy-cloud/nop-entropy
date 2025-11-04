package io.nop.api.core.beans.oauth;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.beans.ExtensibleBean;

import java.util.Map;

@DataBean
public class Oauth2ErrorBean extends ExtensibleBean {
    private String error;
    private String errorDescription;

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    @JsonProperty("error_description")
    public String getErrorDescription() {
        return errorDescription;
    }

    public void setErrorDescription(String errorDescription) {
        this.errorDescription = errorDescription;
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
