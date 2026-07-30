package io.nop.ai.agent.engine;

import io.nop.ai.core.dialect.ILlmDialect;
import io.nop.ai.core.dialect.LlmDialectFactory;
import io.nop.ai.core.model.ApiStyle;

public class TokenEstimators {

    public static ITokenEstimator defaultEstimator() {
        return new CalibratedTokenEstimator(
                LlmDialectFactory.getDialect(ApiStyle.openai), ApiStyle.openai);
    }
}
