package io.nop.task.builder;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalFunction;
import io.nop.core.lang.eval.IEvalScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogEvalFunction implements IEvalFunction {
    private static final Logger LOG = LoggerFactory.getLogger(LogEvalFunction.class);

    private final String stepName;
    private final SourceLocation loc;

    public LogEvalFunction(String stepName, SourceLocation loc) {
        this.stepName = stepName;
        this.loc = loc;
    }

    @Override
    public Object invoke(Object thisObj, Object[] args, IEvalScope scope) {
        return doLog((Throwable) args[0]);
    }

    Object doLog(Throwable error) {
        LOG.info("task.run-fail:stepName={},loc={}", stepName, loc, error);
        return null;
    }
}
