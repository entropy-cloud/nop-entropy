package io.nop.api.core.beans.std;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.meta.PropMeta;

@DataBean
public class StdIdRequestBean {

    private String id;

    private Boolean allowEmpty;

    @PropMeta(mandatory = true, propId = 1)
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @PropMeta(propId = 2)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Boolean getAllowEmpty() {
        return allowEmpty;
    }

    public void setAllowEmpty(Boolean allowEmpty) {
        this.allowEmpty = allowEmpty;
    }
}
