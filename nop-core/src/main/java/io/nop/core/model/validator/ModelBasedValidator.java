/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.model.validator;

import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.beans.ITreeBean;
import io.nop.api.core.util.IVariableScope;
import io.nop.api.core.validate.IValidationErrorCollector;
import io.nop.api.core.validate.IValidator;
import io.nop.core.CoreErrors;
import io.nop.core.model.query.FilterBeanEvaluator;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModelBasedValidator implements IValidator<IVariableScope> {
    private final ValidatorModel validatorModel;
    private final FilterBeanEvaluator evaluator;

    public ModelBasedValidator(ValidatorModel validatorModel, FilterBeanEvaluator evaluator) {
        this.validatorModel = validatorModel;
        this.evaluator = evaluator;
    }

    public ModelBasedValidator(ValidatorModel validatorModel) {
        this(validatorModel, FilterBeanEvaluator.INSTANCE);
    }

    public void validateWithDefaultCollector(IVariableScope scope, int fatalSeverity) {
        DefaultValidationErrorCollector collector = new DefaultValidationErrorCollector(fatalSeverity);
        validate(scope, collector);
        collector.end();
    }

    @Override
    public void validate(IVariableScope scope, IValidationErrorCollector collector) {

        List<ValidatorCheckModel> checks = validatorModel.getChecks();
        if (checks != null) {
            for (ValidatorCheckModel check : checks) {
                if (!passCondition(check.getCondition(), scope)) {
                    Map<String, Object> params = buildParams(check.getErrorParams(), scope);
                    ErrorBean errorBean = collector.buildError(check.getErrorCode());
                    errorBean.setSeverity(check.getSeverity());
                    errorBean.setParams(params);
                    collector.addError(errorBean);
                }
            }
        }

        if (!passCondition(validatorModel.getCondition(), scope)) {
            Map<String, Object> params = buildParams(validatorModel.getErrorParams(), scope);
            String errorCode = validatorModel.getErrorCode();
            if (errorCode == null)
                errorCode = CoreErrors.ERR_VALIDATE_CHECK_FAIL.getErrorCode();
            ErrorBean errorBean = collector.buildError(errorCode);
            errorBean.setParams(params);
            errorBean.setSeverity(validatorModel.getSeverity());
            collector.addError(errorBean);
        }
    }

    private boolean passCondition(ITreeBean condition, IVariableScope scope) {
        if (condition == null)
            return true;

        return Boolean.TRUE.equals(evaluator.visitRoot(condition, scope));
    }

    private Map<String, Object> buildParams(Map<String, String> errorParams, IVariableScope scope) {
        if (errorParams == null)
            return Collections.emptyMap();

        Map<String, Object> ret = new HashMap<>();
        for (Map.Entry<String, String> entry : errorParams.entrySet()) {
            String targetName = entry.getKey();
            String srcName = entry.getValue();
            Object value = scope.getValueByPropPath(srcName);
            ret.put(targetName, value);
        }
        return ret;
    }
}