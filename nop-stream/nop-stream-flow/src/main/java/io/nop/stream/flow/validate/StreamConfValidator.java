/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.flow.validate;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.ApiStringHelper;
import io.nop.core.resource.IResource;
import io.nop.stream.core.common.functions.SinkFunction;
import io.nop.stream.core.common.functions.source.SourceFunction;
import io.nop.stream.core.connector.ConnectivityProbeOutcome;
import io.nop.stream.core.connector.StreamConnectivityProber;
import io.nop.stream.core.connector.registry.ConnectorCapabilityDescriptor;
import io.nop.stream.core.connector.registry.ConnectorDirection;
import io.nop.stream.core.connector.registry.ConnectorParamDescriptor;
import io.nop.stream.core.connector.registry.IStreamConnectorFactory;
import io.nop.stream.core.connector.registry.StreamConnectorCatalog;
import io.nop.stream.core.exceptions.NopStreamErrors;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.flow.builder.BeanFunctionResolver;
import io.nop.stream.flow.builder.GlobalBeanFunctionResolver;
import io.nop.stream.flow.builder.StreamModelDslBuilder;
import io.nop.stream.flow.model.StreamModel;
import io.nop.stream.flow.model.StreamSinkModel;
import io.nop.stream.flow.model.StreamSourceModel;
import io.nop.stream.flow.model.StreamTransformModel;
import io.nop.xlang.xdsl.DslModelParser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Core of the conf-validate / dry-run command (item 20 / P-REQ-13/14,
 * pre-submit-validation-design.md D2/D6/D7). Validates a stream job definition
 * WITHOUT starting it:
 *
 * <ul>
 *   <li>Layer 1 — model parse: the XDSL is validated field-level against
 *       {@code stream.xdef} by the platform DSL parser.</li>
 *   <li>Layer 2 — construction: the full graph is built through
 *       {@link StreamModelDslBuilder} without {@code execute()} (bean resolution,
 *       type matching, FL-1 rejection faces and xpl compilation are all exercised);
 *       connector mode validates params field-level against the SPI registry
 *       descriptor (missing required / unknown params are reported item by item
 *       with the option name) and constructs the endpoint via the catalog.</li>
 *   <li>Layer 3 — connectivity probe ({@code --connect} / dry-run): every
 *       source/sink endpoint instance is probed via {@link StreamConnectivityProber}
 *       (capability interface → FLIP-27 → 2PC contract → explicit skip); families
 *       without a probe contract are reported as explicit skip items.</li>
 * </ul>
 *
 * <p>Error output contract: every issue carries layer + element/endpoint + error
 * code + option name (when the contract has one). Layer 3 failures are collected
 * per endpoint and never abort the whole run; the report aggregates them.
 */
public final class StreamConfValidator {

    /** Layer 1: model parse (stream.xdef field-level validation). */
    public static final int LAYER_MODEL = 1;
    /** Layer 2: construction (graph build / registry param validation + factory construction). */
    public static final int LAYER_CONSTRUCTION = 2;
    /** Layer 3: connectivity probe (dry-run). */
    public static final int LAYER_CONNECTIVITY = 3;

    private static final String UNKNOWN_ERROR_CODE = "unexpected-error";

    /** Param keys probed (in order) to populate the issue's option name. */
    private static final String[] OPTION_NAME_KEYS =
            {"paramName", "attrName", "beanName", "refName", "typeName", "id"};

    // ------------------------------------------------------------------
    // stream mode (XDSL path + bean source, D2)
    // ------------------------------------------------------------------

    /**
     * Validates the stream job definition at the given resource: layer 1 (xdef parse)
     * + layer 2 (graph build without execute); {@code connect} additionally runs
     * layer 3 (per-endpoint connectivity probe).
     */
    public StreamConfValidationReport validateStream(IResource resource, BeanFunctionResolver resolver,
                                                      boolean connect) {
        StreamConfValidationReport report = new StreamConfValidationReport();
        String target = String.valueOf(resource.getPath());

        StreamModel model = parseModel(resource, target, report);
        if (model == null) {
            // Parse failed: layers 2/3 cannot run on a broken model — the layer-1 issue
            // already explains why. This is an explicit stop, not a silent skip.
            return report;
        }
        boolean graphBuilt = buildGraph(model, resolver, report);
        if (connect && graphBuilt) {
            probeEndpoints(model, resolver, report);
        }
        return report;
    }

    private StreamModel parseModel(IResource resource, String target, StreamConfValidationReport report) {
        try {
            return (StreamModel) new DslModelParser().parseFromResource(resource);
        } catch (NopException e) {
            report.add(toIssue(LAYER_MODEL, target, e));
            return null;
        } catch (Exception e) {
            report.add(ValidationIssue.of(LAYER_MODEL, target, UNKNOWN_ERROR_CODE, null, String.valueOf(e)));
            return null;
        }
    }

    private boolean buildGraph(StreamModel model, BeanFunctionResolver resolver,
                               StreamConfValidationReport report) {
        try {
            StreamModelDslBuilder.of(model, resolver).build();
            return true;
        } catch (NopException e) {
            String element = stringParam(e, "element");
            report.add(toIssue(LAYER_CONSTRUCTION, element != null ? element : "graph-build", e));
        } catch (Exception e) {
            report.add(ValidationIssue.of(LAYER_CONSTRUCTION, "graph-build", UNKNOWN_ERROR_CODE, null,
                    String.valueOf(e)));
        }
        return false;
    }

    /**
     * Layer 3 (D3 driver): iterate the model's source/sink elements, resolve each
     * endpoint instance through the bean source, and probe it. PASS outcomes land in
     * the probe log; FAIL and explicit-skip outcomes land in the issue list (skip
     * items do not fail the run). Inline-xpl endpoints have no probe contract and
     * are reported as explicit skips naming the element.
     */
    private void probeEndpoints(StreamModel model, BeanFunctionResolver resolver,
                                StreamConfValidationReport report) {
        BeanFunctionResolver effective = resolver != null ? resolver : GlobalBeanFunctionResolver.INSTANCE;
        List<StreamTransformModel> transforms = model.getTransforms();
        if (transforms == null) {
            return;
        }
        for (StreamTransformModel t : transforms) {
            if (t instanceof StreamSourceModel source) {
                probeEndpoint("source '" + source.getId() + "'", source.getBean(),
                        source.getSource() != null ? "(inline xpl)" : null,
                        effective, SourceFunction.class, report);
            } else if (t instanceof StreamSinkModel sink) {
                probeEndpoint("sink '" + sink.getId() + "'", sink.getBean(),
                        sink.getSource() != null ? "(inline xpl)" : null,
                        effective, SinkFunction.class, report);
            }
        }
    }

    private <T> void probeEndpoint(String elementDesc, String beanName, String inlineNote,
                                   BeanFunctionResolver resolver, Class<T> endpointType,
                                   StreamConfValidationReport report) {
        String target = beanName != null
                ? elementDesc + " (bean " + beanName + ")"
                : elementDesc + " " + inlineNote;
        Object endpoint;
        if (beanName == null) {
            endpoint = null;
        } else {
            try {
                endpoint = resolver.resolve(beanName, endpointType);
            } catch (NopException e) {
                report.add(toIssue(LAYER_CONNECTIVITY, target, e));
                return;
            }
        }
        if (endpoint == null) {
            report.add(ValidationIssue.skip(LAYER_CONNECTIVITY, target,
                    NopStreamErrors.ERR_STREAM_CONNECTIVITY_NOT_SUPPORTED.getErrorCode(),
                    "inline xpl endpoint has no probe contract; connectivity undetermined by dry-run"));
            report.addProbeLine(target + ": SKIP (no probe contract)");
            return;
        }
        ConnectivityProbeOutcome outcome = StreamConnectivityProber.probe(target, endpoint);
        switch (outcome.getStatus()) {
            case PASS -> report.addProbeLine(target + ": PASS — " + outcome.getDetail());
            case FAIL -> report.add(ValidationIssue.of(LAYER_CONNECTIVITY, target,
                    outcome.getErrorCode() != null ? outcome.getErrorCode() : UNKNOWN_ERROR_CODE,
                    beanName, outcome.getDetail()));
            case SKIP -> {
                report.add(ValidationIssue.skip(LAYER_CONNECTIVITY, target,
                        outcome.getErrorCode() != null ? outcome.getErrorCode() : UNKNOWN_ERROR_CODE,
                        outcome.getDetail()));
                report.addProbeLine(target + ": SKIP — " + outcome.getDetail());
            }
        }
    }

    // ------------------------------------------------------------------
    // connector mode (SPI registry world, D6)
    // ------------------------------------------------------------------

    /**
     * Validates one connector declared by type name + params against the SPI registry
     * (item 19): field-level param validation against the capability descriptor
     * (unknown params and missing required params are reported with the option name),
     * then construction through the catalog (typed ctor validation + capability
     * alignment + endpoint close); {@code connect} additionally probes the constructed
     * endpoint's connectivity before it is closed.
     */
    public StreamConfValidationReport validateConnector(ConnectorDirection direction, String typeName,
                                                          Map<String, Object> params,
                                                          StreamConnectorCatalog catalog, boolean connect) {
        StreamConfValidationReport report = new StreamConfValidationReport();
        String target = typeName + " (" + direction.name() + ")";

        IStreamConnectorFactory factory = resolveFactory(direction, typeName, catalog, target, report);
        if (factory != null) {
            validateParamsFieldLevel(factory.describeCapabilities(), params, target, report);
            constructViaCatalog(catalog, direction, typeName, params, report);
        }
        if (connect && report.isPassed()) {
            probeConnectorViaCatalog(catalog, direction, typeName, params, target, report);
        }
        return report;
    }

    private void probeConnectorViaCatalog(StreamConnectorCatalog catalog, ConnectorDirection direction,
                                          String typeName, Map<String, Object> params, String target,
                                          StreamConfValidationReport report) {
        ConnectivityProbeOutcome outcome = catalog.probeConnectivity(direction, typeName,
                new io.nop.stream.core.connector.registry.StreamConnectorConfig(typeName, params));
        switch (outcome.getStatus()) {
            case PASS -> report.addProbeLine(target + ": PASS — " + outcome.getDetail());
            case FAIL -> report.add(ValidationIssue.of(LAYER_CONNECTIVITY, target,
                    outcome.getErrorCode() != null ? outcome.getErrorCode() : UNKNOWN_ERROR_CODE,
                    typeName, outcome.getDetail()));
            case SKIP -> {
                report.add(ValidationIssue.skip(LAYER_CONNECTIVITY, target,
                        outcome.getErrorCode() != null ? outcome.getErrorCode() : UNKNOWN_ERROR_CODE,
                        outcome.getDetail()));
                report.addProbeLine(target + ": SKIP — " + outcome.getDetail());
            }
        }
    }

    private IStreamConnectorFactory resolveFactory(ConnectorDirection direction, String typeName,
                                                    StreamConnectorCatalog catalog, String target,
                                                    StreamConfValidationReport report) {
        try {
            return direction == ConnectorDirection.SOURCE
                    ? catalog.getRegistry().resolveSourceFactory(typeName)
                    : catalog.getRegistry().resolveSinkFactory(typeName);
        } catch (NopException e) {
            report.add(toIssue(LAYER_CONSTRUCTION, target, e));
            return null;
        }
    }

    /**
     * Field-level validation of the provided params against the descriptor: unknown
     * params are rejected naming the param and the declared set (item 19 deferred
     * this boundary to item 20); missing required params are reported naming the
     * param and its kind. All issues are collected — never first-error-only (D7).
     */
    private void validateParamsFieldLevel(ConnectorCapabilityDescriptor descriptor,
                                           Map<String, Object> params, String target,
                                           StreamConfValidationReport report) {
        Set<String> declared = new LinkedHashSet<>();
        for (ConnectorParamDescriptor p : descriptor.getParams()) {
            declared.add(p.getName());
        }
        for (String provided : params.keySet()) {
            if (!declared.contains(provided)) {
                StreamException ex = new StreamException(NopStreamErrors.ERR_STREAM_CONNECTOR_PARAM_UNKNOWN);
                ex.param(NopStreamErrors.ARG_TYPE_NAME, descriptor.getTypeName());
                ex.param(NopStreamErrors.ARG_PARAM_NAME, provided);
                ex.param(NopStreamErrors.ARG_DECLARED_PARAMS, new ArrayList<>(declared));
                report.add(toIssue(LAYER_CONSTRUCTION, target, ex));
            }
        }
        for (ConnectorParamDescriptor p : descriptor.getParams()) {
            if (p.isRequired() && !params.containsKey(p.getName())) {
                StreamException ex = new StreamException(NopStreamErrors.ERR_STREAM_CONNECTOR_PARAM_REQUIRED);
                ex.param(NopStreamErrors.ARG_TYPE_NAME, descriptor.getTypeName());
                ex.param(NopStreamErrors.ARG_PARAM_NAME, p.getName());
                ex.param(NopStreamErrors.ARG_PARAM_KIND, p.getKind().name());
                report.add(toIssue(LAYER_CONSTRUCTION, target, ex));
            }
        }
    }

    private void constructViaCatalog(StreamConnectorCatalog catalog, ConnectorDirection direction,
                                      String typeName, Map<String, Object> params,
                                      StreamConfValidationReport report) {
        try {
            catalog.probe(direction, typeName,
                    new io.nop.stream.core.connector.registry.StreamConnectorConfig(typeName, params));
        } catch (NopException e) {
            report.add(toIssue(LAYER_CONSTRUCTION, typeName + " (" + direction.name() + ")", e));
        } catch (Exception e) {
            report.add(ValidationIssue.of(LAYER_CONSTRUCTION, typeName + " (" + direction.name() + ")",
                    UNKNOWN_ERROR_CODE, null, String.valueOf(e)));
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** Converts a typed exception into a structured issue, extracting the option name. */
    static ValidationIssue toIssue(int layer, String target, NopException e) {
        String errorCode = e.getErrorCode();
        return ValidationIssue.of(layer, target, errorCode != null ? errorCode : UNKNOWN_ERROR_CODE,
                optionName(e), renderMessage(e));
    }

    static String renderMessage(NopException e) {
        String desc = e.getDescription();
        if (desc == null) {
            String message = e.getMessage();
            return message != null ? message : String.valueOf(e);
        }
        return ApiStringHelper.renderTemplate(desc, e::getParam);
    }

    private static String optionName(NopException e) {
        for (String key : OPTION_NAME_KEYS) {
            Object value = e.getParam(key);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private static String stringParam(NopException e, String key) {
        Object value = e.getParam(key);
        return value != null ? String.valueOf(value) : null;
    }
}
