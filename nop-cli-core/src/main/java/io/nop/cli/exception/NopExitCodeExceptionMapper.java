package io.nop.cli.exception;

import io.nop.api.core.beans.ErrorBean;
import io.nop.core.exceptions.ErrorMessageManager;
import picocli.CommandLine;

public class NopExitCodeExceptionMapper implements CommandLine.IExitCodeExceptionMapper {
    @Override
    public int getExitCode(Throwable exception) {
        if (exception instanceof CommandLine.ExecutionException)
            exception = exception.getCause();
        ErrorBean errorBean = ErrorMessageManager.instance().buildErrorMessage(null, exception);
        return errorBean.getStatus();
    }
}
