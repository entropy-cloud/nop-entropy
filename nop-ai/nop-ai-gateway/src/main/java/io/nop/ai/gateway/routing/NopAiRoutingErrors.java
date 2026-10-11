package io.nop.ai.gateway.routing;

import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

/**
 * Error codes owned by the gateway model-class routing capability.
 *
 * <p>Relocated verbatim from {@code io.nop.ai.core.NopAiCoreErrors} (WI9
 * error-code relocation, plan 2438): WI5/plan 2424 moved the routing engine
 * into nop-ai-gateway, leaving this code with zero core-side consumers, so
 * the holder moves next to its consumers. The error code ID and English
 * description are deliberately preserved verbatim — persisted/logged
 * contracts; renaming would change behavior. The matching i18n yaml key in
 * nop-runner/nop-cli-core is keyed by ID and is unaffected.
 *
 * <p>{@code ARG_MODEL_CLASS} is a local copy: the original constant stays in
 * {@code NopAiCoreErrors} because core's {@code LlmConfigHelper} attaches it
 * to {@code nop.err.ai.service.no-default-llms} throw sites. String argument
 * -name constants carry no registry identity, so the duplication is safe.
 */
public interface NopAiRoutingErrors {
    String ARG_MODEL_CLASS = "modelClass";

    /**
     * Model-class routing pool saturation (plan 2026-08-15-0849-2, design §3.3
     * Q8/Q10 adjudication, Phase 1): thrown by {@code ModelClassRouter} when no
     * candidate in the current model class is available. The saturation
     * semantic covers BOTH concurrency saturation (every candidate has reached
     * its concurrency limit) and health saturation (every candidate's circuit
     * is OPEN / otherwise unavailable) — one code, semantic = "no available
     * candidate", with the triggering model class carried as a parameter.
     * English description by plan adjudication (cross-module consumed by
     * W6/W7 orchestration; not subject to i18n rewriting).
     */
    ErrorCode ERR_AI_MODEL_CLASS_SATURATED =
            define("nop.err.ai.model-class.saturated",
                    "No candidate is currently available for model class: {modelClass}", ARG_MODEL_CLASS);
}
