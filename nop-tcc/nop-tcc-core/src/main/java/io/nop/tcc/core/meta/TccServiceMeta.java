package io.nop.tcc.core.meta;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.CollectionHelper;

import java.util.Map;

import static io.nop.tcc.core.TccCoreErrors.ARG_SERVICE_METHOD;
import static io.nop.tcc.core.TccCoreErrors.ARG_SERVICE_NAME;
import static io.nop.tcc.core.TccCoreErrors.ERR_TCC_UNKNOWN_SERVICE_METHOD;

@DataBean
public class TccServiceMeta {
    private final String serviceName;

    private final Map<String, TccMethodMeta> methods;

    public TccServiceMeta(@JsonProperty("serviceName") String serviceName,
                          @JsonProperty("methods") Map<String, TccMethodMeta> methods) {
        this.serviceName = serviceName;
        this.methods = methods;
    }

    public String getServiceName() {
        return serviceName;
    }

    public Map<String, TccMethodMeta> getMethods() {
        return methods;
    }

    public TccServiceMeta merge(Map<String, TccMethodMeta> methods) {
        Map<String, TccMethodMeta> ret = CollectionHelper.newHashMap(methods.size() + this.methods.size());
        ret.putAll(this.methods);
        ret.putAll(methods);
        return new TccServiceMeta(serviceName, ret);
    }

    public TccMethodMeta getMethod(String methodName) {
        return methods.get(methodName);
    }

    public TccMethodMeta requireMethod(String methodName) {
        TccMethodMeta method = getMethod(methodName);
        if (method == null) {
            throw new NopException(ERR_TCC_UNKNOWN_SERVICE_METHOD)
                    .param(ARG_SERVICE_NAME, serviceName)
                    .param(ARG_SERVICE_METHOD, methodName);
        }
        return method;
    }
}
