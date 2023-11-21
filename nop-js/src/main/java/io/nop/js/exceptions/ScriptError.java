package io.nop.js.exceptions;

import io.nop.api.core.exceptions.ErrorCode;
import io.nop.api.core.exceptions.NopException;

/**
 * rollup新版本在装载失败的时候访问了很多属性
 */
public class ScriptError extends NopException {
    public String code;

    public String pluginCode;

    public Object plugin;

    public Object hook;

    public String message;

    public Object watchFiles;

    public ScriptError(NopException ex) {
        super(ex.getErrorCode(), ex.getCause(), false, false);
        params(ex.getParams());
        this.message = ex.getMessage();
    }

    public ScriptError(ErrorCode errorCode) {
        super(errorCode);
    }

    public ScriptError(ErrorCode errorCode, Throwable error) {
        super(errorCode, error);
    }

}
