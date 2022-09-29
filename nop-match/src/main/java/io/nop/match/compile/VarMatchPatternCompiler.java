package io.nop.match.compile;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.util.StringHelper;
import io.nop.match.IMatchPattern;
import io.nop.match.IMatchPatternCompiler;
import io.nop.match.MatchPatternCompileConfig;
import io.nop.match.pattern.VarMatchPattern;

import static io.nop.match.MatchErrors.ARG_VAR_NAME;
import static io.nop.match.MatchErrors.ERR_MATCH_INVALID_VAR_NAME;

public class VarMatchPatternCompiler implements IMatchPatternCompiler {
    public static final VarMatchPatternCompiler INSTANCE = new VarMatchPatternCompiler();

    @Override
    public IMatchPattern parseFromValue(SourceLocation loc, Object value, MatchPatternCompileConfig config) {
        String varName = ConvertHelper.toString(value);
        if (StringHelper.isEmpty(varName))
            throw new NopException(ERR_MATCH_INVALID_VAR_NAME)
                    .loc(loc)
                    .param(ARG_VAR_NAME, varName);

        return new VarMatchPattern(config.getEqualsChecker(), varName);
    }
}
