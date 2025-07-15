package io.nop.api.core.validate;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.exceptions.NopRebuildException;

import java.util.LinkedHashMap;
import java.util.Map;

public class ThrowValidationErrorCollector implements IValidationErrorCollector {
    private Map<String, Object> extParams;

    public ThrowValidationErrorCollector extParam(String name, Object value) {
        if (extParams == null)
            extParams = new LinkedHashMap<>();
        extParams.put(name, value);
        return this;
    }

    public ThrowValidationErrorCollector bizObjName(String bizObjName) {
        return extParam(ApiConstants.PARAM_BIZ_OBJ_NAME, bizObjName);
    }

    @Override
    public void addError(ErrorBean error) {
        if (extParams != null) {
            extParams.forEach((name, value) -> {
                if (!error.hasParam(name)) {
                    error.param(name, value);
                }
            });
        }
        throw NopRebuildException.rebuild(error);
    }
}
