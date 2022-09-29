package io.nop.match.compile;

import io.nop.api.core.util.SourceLocation;
import io.nop.core.lang.eval.IEvalAction;
import io.nop.match.IMatchPattern;
import io.nop.match.IMatchPatternCompiler;
import io.nop.match.MatchPatternCompileConfig;
import io.nop.match.pattern.SwitchMatchPattern;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.nop.core.lang.json.utils.SourceLocationHelper.getPropLocation;
import static io.nop.match.compile.PatternCompileHelper.optionsToMap;
import static io.nop.match.compile.PatternCompileHelper.requireOption;

public class SwitchMatchPatternCompiler implements IMatchPatternCompiler {
    public static final SwitchMatchPatternCompiler INSTANCE = new SwitchMatchPatternCompiler();

    @Override
    public IMatchPattern parseFromValue(SourceLocation loc, Object value, MatchPatternCompileConfig config) {
        Map<String, Object> options = optionsToMap(value);
        String exprString = (String) requireOption("switch", options, "chooseExpr");
        Map<String, Object> casesConfig = (Map<String, Object>) requireOption("switch", options, "cases");

        IEvalAction chooseExpr = config.getExprParser().parseExpr(null, exprString);

        Object defaultOption = options.get("default");

        IMatchPattern defaultPattern = defaultOption == null ? null :
                config.getCompileHelper().parseFromValue(getPropLocation(options, "default"),
                        defaultOption, config);

        Map<String, IMatchPattern> cases = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : casesConfig.entrySet()) {
            String caseName = entry.getKey();
            IMatchPattern casePattern = config.getCompileHelper().parseFromValue(
                    getPropLocation(casesConfig, caseName), entry.getValue(), config);
            cases.put(caseName, casePattern);
        }

        return new SwitchMatchPattern(exprString, new ExprStringFunction(chooseExpr), cases, defaultPattern);
    }
}
