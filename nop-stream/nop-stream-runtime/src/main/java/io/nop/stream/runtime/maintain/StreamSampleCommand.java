/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.maintain;

import io.nop.stream.core.connector.StreamDataSampler;
import io.nop.stream.core.connector.registry.ConnectorDirection;
import io.nop.stream.core.connector.registry.ConnectorSampleResult;
import io.nop.stream.core.connector.registry.StreamConnectorCatalog;
import io.nop.stream.core.connector.registry.StreamConnectorConfig;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.flow.validate.StreamConfValidationReport;
import io.nop.stream.flow.validate.StreamConfValidator;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.ioc.IBeanContainer;
import io.nop.core.initialize.CoreInitialization;
import io.nop.core.resource.IResource;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;

/**
 * WI4 (plan 2329): the {@code sample} subcommand of the {@link StreamMaintenanceMain}
 * entry family — the data-sampling sibling of conf-validate/dry-run, operating in the
 * connector validation mode (registry-resolved factory, NOT the XDSL stream mode):
 *
 * <pre>
 *   sample type=&lt;typeName&gt; param=value... [limit=&lt;n&gt;]
 * </pre>
 *
 * <p>Resolves the SOURCE factory through the SPI registry, runs the connector param
 * rule validation (required / unknown / kind / allowedValues / pattern; sensitive
 * values masked; EXPERIMENTAL maturity reported as a non-blocking warning), then
 * dispatches sampling: endpoints implementing {@code SampleableSource} return real
 * records (read-only — no split assignment, no commit, no offset advance); all other
 * endpoints answer an explicit SKIP with the contract reason (closed dispatch — an
 * empty record list is never passed off as success).
 *
 * <p>Exit code contract (aligned with conf-validate D7): 0 = sampled or explicit SKIP,
 * 1 = validation/sampling failure, 2 = usage error.
 */
public final class StreamSampleCommand {

    /**
     * Initialization cap below the global IoC container level — mirrors the
     * {@code nop.core.initialize.max-initialize-level=4500} contract the multi-JVM
     * harness passes to spawned cluster JVMs (runtime pom surefire config documents
     * the same cap). The sample command's connector container is standalone.
     */
    public static final int PRE_IOC_INITIALIZE_LEVEL = 4500;

    private StreamSampleCommand() {
    }

    /** CLI form. Returns the process exit code. */
    public static int run(List<String> args) {
        try {
            ParsedArgs parsed = parseArgs(args);
            return run(parsed.type, parsed.params, parsed.limit);
        } catch (IllegalArgumentException | StreamException e) {
            System.err.println("sample usage error: " + e);
            System.err.println(StreamMaintenanceMain.usage());
            return 2;
        }
    }

    public static int run(String typeName, Map<String, Object> params, int limit) {
        // Cap initialization below the global IoC level (same contract as the
        // MiniStreamCluster spawn JVMs): the sample command needs VFS/xlang only —
        // its connector container is assembled STANDALONE (explicit beans, never the
        // global container), and full-level init would demand datasource/message
        // config this tool must not require.
        CoreInitialization.initializeTo(
                io.nop.stream.runtime.maintain.StreamSampleCommand.PRE_IOC_INITIALIZE_LEVEL);
        // The connector factories are registered through NopIoC beans.xml discovery, so
        // assembling the registry container requires nop-ioc on the classpath. nop-ioc is
        // deliberately NOT a compile dependency of the stream modules (its core
        // initializer is kept out of embedding/test JVMs), so the CLI assembles the
        // container reflectively and fails fast with an actionable message when the
        // platform IoC jars are absent (script launches append them via
        // NOP_STREAM_EXTRA_CLASSPATH; embedding callers pass their live container).
        Object container = null;
        try {
            container = assembleConnectorContainer();
            StreamConnectorCatalog catalog = StreamConnectorCatalog.of((IBeanContainer) container);

            StreamConfValidationReport report = new StreamConfValidator()
                    .validateConnector(ConnectorDirection.SOURCE, typeName, params, catalog, false);
            if (!report.isPassed()) {
                System.out.print(report.render());
                System.out.println("sample target=" + typeName + " exit=1");
                return 1;
            }

            ConnectorSampleResult result = StreamDataSampler.sample(catalog, typeName,
                    new StreamConnectorConfig(typeName, params), limit);
            if (result.isSupported()) {
                System.out.println("sample " + typeName + " -> " + result.getEndpointClass()
                        + " (" + result.getRecords().size() + " record(s), read-only)");
                for (Object record : result.getRecords()) {
                    System.out.println("  " + record);
                }
                if (!report.getIssues().isEmpty()) {
                    System.out.print(report.render());
                }
                System.out.println("sample target=" + typeName + " exit=0");
                return 0;
            }
            report.addProbeLine("sample " + typeName + ": SKIP — " + result.getSkipDetail());
            System.out.print(report.render());
            System.out.println("sample " + typeName + " SKIP (" + result.getSkipErrorCode() + ")");
            System.out.println("sample target=" + typeName + " exit=0");
            return 0;
        } finally {
            if (container != null) {
                try {
                    container.getClass().getMethod("stop").invoke(container);
                } catch (Exception e) {
                    // stop failure after results are printed must not mask the verdict
                    System.err.println("sample: connector container stop failed: " + e);
                }
            }
        }
    }

    /**
     * Reflectively builds the connector registry container
     * ({@code new BeanContainerBuilder(null)} + discovered connector-*.beans.xml +
     * {@code build(id)} + {@code start()}) so this command carries no compile
     * dependency on nop-ioc. Fails fast with a typed error when nop-ioc is absent.
     */
    private static Object assembleConnectorContainer() {
        try {
            Class<?> builderClass = Class.forName("io.nop.ioc.loader.BeanContainerBuilder");
            Constructor<?> ctor = builderClass.getConstructor(IBeanContainer.class);
            Object builder = ctor.newInstance(new Object[]{null});
            Method addResource = builderClass.getMethod("addResource", IResource.class);
            for (IResource resource : StreamConnectorCatalog.discoverConnectorBeansResources()) {
                addResource.invoke(builder, resource);
            }
            Object container = builderClass.getMethod("build", String.class)
                    .invoke(builder, "stream-connectors-sample");
            container.getClass().getMethod("start").invoke(container);
            return container;
        } catch (ClassNotFoundException e) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_DETAIL, "The sample command assembles the connector registry through "
                            + "the platform IoC container, but nop-ioc is not on the classpath. "
                            + "Append the platform IoC jars (NOP_STREAM_EXTRA_CLASSPATH for script "
                            + "launches) or call back programmatically with a live container.");
        } catch (ReflectiveOperationException e) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_DETAIL, "Failed to assemble the connector registry container: " + e);
        }
    }

    private record ParsedArgs(String type, Map<String, Object> params, int limit) {
    }

    private static ParsedArgs parseArgs(List<String> args) {
        Map<String, Object> params = new LinkedHashMap<>();
        String type = null;
        int limit = 10;
        for (String arg : args) {
            int eq = arg.indexOf('=');
            if (eq <= 0) {
                throw new StreamException(ERR_STREAM_INVALID_ARG)
                        .param(ARG_DETAIL, "Malformed argument (expected key=value): " + arg);
            }
            String key = arg.substring(0, eq);
            String value = arg.substring(eq + 1);
            if ("type".equals(key)) {
                type = value;
            } else if ("limit".equals(key)) {
                try {
                    limit = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    throw new StreamException(ERR_STREAM_INVALID_ARG)
                            .param(ARG_DETAIL, "limit must be an integer: " + value);
                }
            } else {
                params.put(key, value);
            }
        }
        if (type == null || type.isBlank()) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_DETAIL, "Missing required argument 'type' for sample");
        }
        if (limit <= 0) {
            throw new StreamException(ERR_STREAM_INVALID_ARG)
                    .param(ARG_DETAIL, "limit must be positive: " + limit);
        }
        return new ParsedArgs(type, params, limit);
    }
}
