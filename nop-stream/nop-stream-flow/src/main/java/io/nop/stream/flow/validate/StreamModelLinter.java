/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.validate;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.resource.IResource;
import io.nop.stream.flow.model.CheckpointConfigModel;
import io.nop.stream.flow.model.StreamAggregateModel;
import io.nop.stream.flow.model.StreamJoinModel;
import io.nop.stream.core.windowing.AccumulationMode;
import io.nop.stream.core.common.functions.sink.SinkConsistencyCapability;
import io.nop.stream.core.common.functions.source.SourceConsistencyCapability;
import io.nop.stream.flow.builder.BeanFunctionResolver;
import io.nop.stream.flow.model.StreamCepModel;
import io.nop.stream.flow.model.StreamCustomModel;
import io.nop.stream.flow.model.StreamEdgeModel;
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.flow.model.StreamProcessModel;
import io.nop.stream.flow.model.StreamSinkModel;
import io.nop.stream.flow.model.StreamSourceModel;
import io.nop.stream.flow.model.StreamTimestampsAndWatermarksModel;
import io.nop.stream.flow.model.StreamTransformModel;
import io.nop.stream.flow.model.StreamWindowModel;
import io.nop.stream.flow.model.WindowingStrategyModel;
import io.nop.xlang.xdsl.DslModelParser;

import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_NOT_IMPLEMENTED;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_WINDOW_ATTR_UNSUPPORTED;

/**
 * xdsl-full-coverage roadmap WI3 (plan 2349): the model-aware lint layer behind the
 * {@code StreamMaintenanceMain lint} subcommand. Where the builder fail-fasts on the
 * FIRST unconsumed declaration and nop-lint (WI2) scans raw XML per file, the linter
 * parses the model through the production {@link DslModelParser} chain and reports
 * ALL best-practice findings at once, then folds the authoritative conf-validator
 * verdict (R11 chaining) into the same report.
 *
 * <p>Severity contract (roadmap Rule 4 two-stage rollout): every declarative-face
 * finding is WARNING — the builder remains the final authority; only the folded
 * conf-validator FAILs decide the exit code. R6/R13/R14 land as explicit SKIP notes
 * (the D7 contract allows explicit skips; silent skips are forbidden) — R6 activates
 * when WI15 releases the endpoint capability face, R13 when WI21 lands the scenario
 * registry, R14 when the connector params face exists.
 *
 * <p>Error codes are reused from the {@code nop.err.stream.*} family — zero new codes.
 */
public final class StreamModelLinter {
    public static final int LAYER_LINT = 0;

    private static final String CODE_NOT_IMPLEMENTED = ERR_STREAM_NOT_IMPLEMENTED.getErrorCode();
    private static final String CODE_WINDOW_ATTR = ERR_STREAM_WINDOW_ATTR_UNSUPPORTED.getErrorCode();
    private static final String CODE_INVALID_ARG = ERR_STREAM_INVALID_ARG.getErrorCode();

    private final StreamConfValidator validator = new StreamConfValidator();

    /** Full lint of one job definition: face scan + R11 conf-validator folding. */
    public StreamConfValidationReport lint(IResource resource, BeanFunctionResolver resolver,
                                           boolean connect) {
        StreamConfValidationReport report = new StreamConfValidationReport();
        StreamModel model;
        try {
            model = parse(resource);
        } catch (Exception e) {
            // short-circuit: the folded conf-validator run would report the same
            // parse failure — emit exactly one authoritative FAIL, no face scan.
            // e.toString() keeps the exception class name in the single-issue text
            // (the report is the user-facing surface; a stack adds nothing for a
            // DSL syntax error whose location the xdef layer already carries).
            String code = (e instanceof NopException)
                    ? ((NopException) e).getErrorCode()
                    : CODE_INVALID_ARG;
            report.add(ValidationIssue.of(StreamConfValidator.LAYER_MODEL, resource.getPath(),
                    code, null, "model parse failed: " + e));
            return report;
        }
        scanFaces(model, report);
        StreamConfValidationReport confReport = validator.validateStream(resource, resolver, connect);
        for (ValidationIssue issue : confReport.getIssues()) {
            report.add(issue);
        }
        for (String line : confReport.getProbeLog()) {
            report.addProbeLine(line);
        }
        return report;
    }

    private StreamModel parse(IResource resource) {
        // mirror of StreamConfValidator.parseModel — parse failures become the
        // single authoritative LAYER_MODEL FAIL (short-circuit, no face scan).
        return (StreamModel) new DslModelParser().parseFromResource(resource);
    }

    // ------------------------------------------------------------------
    // declarative-face scan (R1/R12 + hints)
    // ------------------------------------------------------------------

    private void scanFaces(StreamModel model, StreamConfValidationReport report) {
        // R1+R12: unconsumed declaration faces, mirror of the WI1 ledger table B
        // (live 2026-10-09: allowedLateness/ACCUMULATING/maxDelay/emit are released
        // and NOT reported). The builder fail-fasts the same faces — these are
        // WARNING advisories, the build stays authoritative.
        if (model.getWindowingStrategies() != null) {
            for (WindowingStrategyModel strategy : model.getWindowingStrategies()) {
                // WI5 (plan 2351): triggerId released — no longer an unconsumed face.
                if (strategy.getAccumulationMode() == AccumulationMode.ACCUMULATING_AND_RETRACTING) {
                    report.add(ValidationIssue.warning(LAYER_LINT, "strategy " + strategy.getStrategyId(),
                            CODE_WINDOW_ATTR, "ACCUMULATING_AND_RETRACTING is spec-only "
                                    + "(roadmap WI5 release face; the builder rejects it)"));
                }
            }
        }
        if (model.getTransforms() != null) {
            for (StreamTransformModel t : model.getTransforms()) {
                scanTransform(t, report);
            }
        }
        if (model.getEdges() != null) {
            for (StreamEdgeModel e : model.getEdges()) {
                scanEdge(e, report);
            }
        }
        scanRegistries(model, report);

        boolean hasWindow = hasTransform(model, StreamWindowModel.class);
        boolean hasCep = hasTransform(model, StreamCepModel.class);
        boolean hasAggregate = hasTransform(model, StreamAggregateModel.class);
        boolean hasJoin = hasTransform(model, StreamJoinModel.class);

        // R4: window/aggregate/join without a <schemas> type declaration
        if ((hasWindow || hasAggregate || hasJoin) && !model.hasSchemas()) {
            report.add(ValidationIssue.warning(LAYER_LINT, model.getName(), CODE_INVALID_ARG,
                    "model uses window/aggregate/join without a <schemas> declaration — "
                            + "types resolve to Unknown (roadmap R4)"));
        }
        // R5: window/cep without a watermark supplier
        if ((hasWindow || hasCep) && !hasTransform(model, StreamTimestampsAndWatermarksModel.class)) {
            report.add(ValidationIssue.warning(LAYER_LINT, model.getName(), CODE_INVALID_ARG,
                    "model uses window/cep without <timestampsAndWatermarks> — no watermark "
                            + "supply on the declarative face (roadmap R5)"));
        }
        // R2: declarative-first degradation hint (heuristic — bean-carried logic with
        // zero declarative advanced ops)
        boolean hasProcess = hasTransform(model, StreamProcessModel.class);
        if (hasProcess && !(hasWindow || hasAggregate || hasJoin || hasCep)) {
            report.add(ValidationIssue.warning(LAYER_LINT, model.getName(), CODE_INVALID_ARG,
                    "model carries process-bean logic with no declarative window/aggregate/"
                            + "join/cep — verify the logic cannot be expressed declaratively "
                            + "(roadmap R2, heuristic hint)"));
        }
        // R3: state-ownership hint for the <custom> escape hatch
        if (hasTransform(model, StreamCustomModel.class)) {
            report.add(ValidationIssue.warning(LAYER_LINT, model.getName(), CODE_NOT_IMPLEMENTED,
                    "model declares <custom> operators — verify any operator state registers "
                            + "with checkpoint participation (roadmap R3 hint)"));
        }

        // R6: idempotent-combination pre-check — dormant until WI15 releases the
        // endpoint capability face; explicit skip, never silent.
        CheckpointConfigModel cfg = model.getCheckpoint();
        if (cfg != null && cfg.getProcessingGuarantee() == io.nop.stream.core.checkpoint.ProcessingGuarantee.STRICT_EXACTLY_ONCE) {
            report.add(ValidationIssue.skip(LAYER_LINT, model.getName(), CODE_INVALID_ARG,
                    "R6: STRICT_EXACTLY_ONCE declared — endpoint REPLAYABLE-source/2PC-sink "
                            + "static pre-check activates when WI15 releases the endpoint "
                            + "capability face; the engine-side validator remains the final gate"));
        }
        // R13 model-aware half: scenario-registry comparison activates with WI21.
        report.add(ValidationIssue.skip(LAYER_LINT, model.getName(), CODE_INVALID_ARG,
                "R13: scenario-registry comparison (E1-E12 fixtures) activates when WI21 "
                        + "lands the scenario registry"));
        // R14: dimension-table wiring shape check has no declarative signal until
        // WI15 releases the connector params face.
        report.add(ValidationIssue.skip(LAYER_LINT, model.getName(), CODE_INVALID_ARG,
                "R14: dimension-table platform-reuse shape check activates when WI15 "
                        + "releases the endpoint params face (IJdbcTemplate/IBatchLoader rule)"));
    }

    private void scanTransform(StreamTransformModel t, StreamConfValidationReport report) {
        if (t instanceof StreamWindowModel) {
            StreamWindowModel w = (StreamWindowModel) t;
            // WI5 (plan 2351): node-level triggerId released — no longer a face.
            if (t.getParallelism() != null && t.getParallelism() > 0) {
                report.add(ValidationIssue.warning(LAYER_LINT, "window " + t.getId(),
                        CODE_NOT_IMPLEMENTED, "<window> is a virtual element — parallelism must be "
                                + "declared on the owning aggregate/reduce/process element"));
            }
        }
        if (t instanceof StreamSourceModel) {
            scanEndpoint("source " + t.getId(), (StreamSourceModel) t, report);
        }
        if (t instanceof StreamSinkModel) {
            StreamSinkModel s = (StreamSinkModel) t;
            if (s.hasParams()) {
                report.add(ValidationIssue.warning(LAYER_LINT, "sink " + t.getId(),
                        CODE_NOT_IMPLEMENTED, "<sink> <params> has no execution consumer "
                                + "(roadmap WI15 release face)"));
            }
            if (s.getMaxParallelism() > 0) {
                report.add(ValidationIssue.warning(LAYER_LINT, "sink " + t.getId(),
                        CODE_NOT_IMPLEMENTED, "sink maxParallelism has no execution consumer "
                                + "(roadmap WI15 release face)"));
            }
            if (s.getInputType() != null) {
                report.add(ValidationIssue.warning(LAYER_LINT, "sink " + t.getId(),
                        CODE_NOT_IMPLEMENTED, "<sink> <inputType> has no execution consumer "
                                + "(roadmap WI15 release face)"));
            }
            if (s.getConsistencyCapability() != null
                    && s.getConsistencyCapability() != SinkConsistencyCapability.AT_LEAST_ONCE) {
                report.add(ValidationIssue.warning(LAYER_LINT, "sink " + t.getId(),
                        CODE_NOT_IMPLEMENTED, "sink consistencyCapability has no execution consumer "
                                + "(roadmap WI15 release face)"));
            }
        }
        if (t instanceof StreamCustomModel) {
            StreamCustomModel c = (StreamCustomModel) t;
            if (c.hasParams() || c.getSource() != null) {
                report.add(ValidationIssue.warning(LAYER_LINT, "custom " + t.getId(),
                        CODE_NOT_IMPLEMENTED, "<custom> <params>/<source> body has no consumer — "
                                + "only customType beans are consumed (roadmap WI15 release face)"));
            }
        }
    }

    private void scanEndpoint(String target, StreamSourceModel t, StreamConfValidationReport report) {
        if (t.hasParams()) {
            report.add(ValidationIssue.warning(LAYER_LINT, target, CODE_NOT_IMPLEMENTED,
                    "<source> <params> has no execution consumer (roadmap WI15 release face)"));
        }
        if (t.getMaxParallelism() > 0) {
            report.add(ValidationIssue.warning(LAYER_LINT, target, CODE_NOT_IMPLEMENTED,
                    "source maxParallelism has no execution consumer (roadmap WI15 release face)"));
        }
        if (t.getOutputType() != null) {
            report.add(ValidationIssue.warning(LAYER_LINT, target, CODE_NOT_IMPLEMENTED,
                    "<source> <outputType> has no execution consumer (roadmap WI15 release face)"));
        }
        if (t.getConsistencyCapability() != null
                && t.getConsistencyCapability() != SourceConsistencyCapability.AT_LEAST_ONCE) {
            report.add(ValidationIssue.warning(LAYER_LINT, target, CODE_NOT_IMPLEMENTED,
                    "source consistencyCapability has no execution consumer (roadmap WI15 release face)"));
        }
    }

    private void scanEdge(StreamEdgeModel e, StreamConfValidationReport report) {
        String target = "edge " + e.getId();
        if (e.getFlowControlPolicy() != null) {
            report.add(ValidationIssue.warning(LAYER_LINT, target, CODE_NOT_IMPLEMENTED,
                    "edge flowControlPolicy has no consumer (roadmap WI14 release face)"));
        }
        if (e.getQueueCapacity() != null) {
            report.add(ValidationIssue.warning(LAYER_LINT, target, CODE_NOT_IMPLEMENTED,
                    "edge queueCapacity has no consumer (roadmap WI14 release face)"));
        }
        if (e.getReceiveWindow() != null) {
            report.add(ValidationIssue.warning(LAYER_LINT, target, CODE_NOT_IMPLEMENTED,
                    "edge receiveWindow has no consumer (roadmap WI14 release face)"));
        }
        if (e.getPacketSize() != null) {
            report.add(ValidationIssue.warning(LAYER_LINT, target, CODE_NOT_IMPLEMENTED,
                    "edge packetSize has no consumer (roadmap WI14 release face)"));
        }
    }

    private void scanRegistries(StreamModel model, StreamConfValidationReport report) {
        // WI12 (plan 2358): <streams> REMOVED from the schema (no consumer ever);
        // <environments>/<requirements>/<checkpointParticipants> RELEASED — validated
        // by StreamModelDslBuilder (validateEnvironments / validateDeclaredRequirements
        // / validateCheckpointParticipants); their NOT_IMPLEMENTED lint warnings are
        // gone.
        // WI11 (plan 2357): <sideInputs> released — validated/materialized by
        // StreamModelDslBuilder (validateSideInputs/materializeSideInputs); the
        // NOT_IMPLEMENTED lint warning is gone.
        if (model.getOnStart() != null || model.getOnEnd() != null || model.getOnError() != null) {
            // WI13 (plan 2359): the roadmap WI13 release-face note is retired — the
            // face is adjudicated Java-only (runtime job event bus is the designated
            // wiring seam; four gaps block the DSL wiring, see the builder message).
            report.add(ValidationIssue.warning(LAYER_LINT, "<onStart>/<onEnd>/<onError>",
                    CODE_NOT_IMPLEMENTED, "lifecycle callbacks are a Java-only surface: the"
                            + " runtime job event bus is the designated wiring seam, but the"
                            + " streamCtx type, onStart timing contract, local-execute bus"
                            + " coverage and owner authorization are not in place yet"));
        }
        if (model.hasCoders()) {
            report.add(ValidationIssue.warning(LAYER_LINT, "<coders>", CODE_NOT_IMPLEMENTED,
                    "<coders> is a Java-only surface: wire formats attach at the connector"
                            + " layer and the declarative registry has no consumer"));
        }
    }

    private boolean hasTransform(StreamModel model, Class<? extends StreamTransformModel> type) {
        if (model.getTransforms() == null) {
            return false;
        }
        for (StreamTransformModel t : model.getTransforms()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }

}
