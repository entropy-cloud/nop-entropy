/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.autotest.core.execute;

import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.validate.ListValidationErrorCollector;
import io.nop.autotest.core.exceptions.AutoTestException;
import io.nop.commons.util.StringHelper;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.match.IMatchPattern;
import io.nop.match.MatchPatternCompileConfig;
import io.nop.match.MatchState;
import io.nop.match.compile.PatternMatchPatternCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static io.nop.autotest.core.AutoTestErrors.ARG_ERRORS;
import static io.nop.autotest.core.AutoTestErrors.ERR_AUTOTEST_CHECK_MATCH_FAIL;

public class AutoTestMatchChecker {
    public static final Logger LOG = LoggerFactory.getLogger(AutoTestMatchChecker.class);

    public static void checkMatch(MatchPatternCompileConfig config, Object tpl, Object value, IEvalScope scope) {
        IMatchPattern pattern = PatternMatchPatternCompiler.INSTANCE.parseFromValue(null, tpl, config);
        ListValidationErrorCollector collector = new ListValidationErrorCollector();
        MatchState state = new MatchState(value);
        state.setScope(scope);
        state.setErrorCollector(collector);
        boolean matched = pattern.matchValue(state, true);

        if (!collector.getErrors().isEmpty()) {
            formatErrors(collector.getErrors());
            LOG.error("nop.autotest.check-fail:\n{}", getErrorsString(collector.getErrors()));

            throw new AutoTestException(ERR_AUTOTEST_CHECK_MATCH_FAIL).param(ARG_ERRORS, collector.getErrors());
        } else if (!matched) {
            throw new AutoTestException(ERR_AUTOTEST_CHECK_MATCH_FAIL);
        }
    }

    static String getErrorString(ErrorBean error) {
        return StringHelper.rightPad(error.getErrorCode(), 40, ' ') + "::" + error.getDescription();
    }

    static String getErrorsString(List<ErrorBean> errors) {
        return StringHelper.joinArray(errors.stream().map(AutoTestMatchChecker::getErrorString).toArray(), "\n");
    }

    static void formatErrors(List<ErrorBean> errors) {
        for (ErrorBean error : errors) {
            if (error.getDescription() == null)
                error.setDescription(error.getErrorCode());

            String description = ErrorMessageManager.instance().resolveDescription(null, error.getDescription(),
                    error.getParams());
            error.setDescription(description);
        }
    }
}