package io.nop.xlang.expr;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.nop.api.core.annotations.core.StaticFactoryMethod;
import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.annotations.data.ImmutableBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.IJsonString;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;

import static io.nop.xlang.XLangErrors.ARG_METHOD_REF;
import static io.nop.xlang.XLangErrors.ERR_XLANG_INVALID_METHOD_REF;

@DataBean
@ImmutableBean
public class MethodRef implements IJsonString {
    private final String ownerName;

    private final String methodName;

    public MethodRef(@JsonProperty("ownerName") String ownerName,
                     @JsonProperty("methodName") String methodName) {
        this.ownerName = Guard.notEmpty(ownerName, "ownerName");
        this.methodName = Guard.notEmpty(methodName, "methodName");
    }

    @StaticFactoryMethod
    public static MethodRef parse(String text) {
        int pos = text.indexOf("::");
        if (pos < 0)
            throw new NopException(ERR_XLANG_INVALID_METHOD_REF).param(ARG_METHOD_REF, text);

        String ownerName = text.substring(0, pos);
        String methodName = text.substring(pos + 2);

        if (!StringHelper.isValidClassName(ownerName) || !StringHelper.isValidJavaVarName(methodName))
            throw new NopException(ERR_XLANG_INVALID_METHOD_REF).param(ARG_METHOD_REF, text);
        return new MethodRef(ownerName, methodName);
    }

    public String toString() {
        return ownerName + "::" + methodName;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getMethodName() {
        return methodName;
    }
}
