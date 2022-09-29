package io.nop.match.adapter;

import io.nop.api.core.validate.IValidationErrorCollector;
import io.nop.api.core.validate.IValidator;
import io.nop.core.context.IExecutionContext;
import io.nop.core.context.IServiceContext;
import io.nop.core.lang.eval.EvalExprProvider;
import io.nop.match.IMatchPattern;
import io.nop.match.MatchState;

/**
 * 将IMatchPattern接口适配到IValidator接口
 */
public class MatchPatternValidator implements IValidator<Object> {
    private final IMatchPattern pattern;

    public MatchPatternValidator(IMatchPattern pattern) {
        this.pattern = pattern;
    }

    @Override
    public void validate(Object message, IValidationErrorCollector collector) {
        MatchState state = newMatchState(message);
        state.setErrorCollector(collector);
        pattern.matchValue(state, true);
    }

    MatchState newMatchState(Object message) {
        if (message instanceof MatchState)
            return (MatchState) message;
        if (message instanceof IExecutionContext) {
            IServiceContext context = (IServiceContext) message;
            MatchState state = new MatchState(context.getRequest());
            state.setScope(context);
            return state;
        } else {
            MatchState state = new MatchState(message);
            state.setScope(EvalExprProvider.newEvalScope());
            return state;
        }
    }
}
