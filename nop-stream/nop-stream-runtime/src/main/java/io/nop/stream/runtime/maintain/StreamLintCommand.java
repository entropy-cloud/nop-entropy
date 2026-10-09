/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.maintain;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.impl.FileResource;
import io.nop.stream.core.exceptions.StreamException;
import io.nop.stream.flow.builder.BeanFunctionResolver;
import io.nop.stream.flow.validate.StreamConfValidationReport;
import io.nop.stream.flow.validate.StreamModelLinter;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;

/**
 * xdsl-full-coverage roadmap WI3 (plan 2349): the {@code lint} subcommand of the
 * {@link StreamMaintenanceMain} entry family — the model-aware best-practice layer:
 *
 * <pre>
 *   lint file=&lt;path&gt; [--connect]
 * </pre>
 *
 * <p>Mirrors {@link StreamConfValidateCommand} (argument shape, VFS/local dual
 * resolution, D2 bean-source forms). The exit-code contract is the D7 shape:
 * 0 = no FAIL finding (WARNING advisories and explicit SKIP notes allowed),
 * 1 = at least one FAIL (including xdef parse failures), 2 = usage error. The
 * command renders its own {@code lint} header — the shared report body lines are
 * reused, the validator's {@code conf-validate:} verdict line is not.
 */
public final class StreamLintCommand {

    private StreamLintCommand() {
    }

    /** CLI form: global-container bean source. Returns the process exit code. */
    public static int run(List<String> args) {
        return run(args, null);
    }

    /**
     * Embedding form: beans resolved through the given resolver; a null resolver
     * falls back to the global container. Usage errors return exit code 2; the
     * lint verdict (FAIL count) decides 0 vs 1.
     */
    public static int run(List<String> args, BeanFunctionResolver resolver) {
        try {
            String file = parseFileArg(args);
            boolean connect = args.contains("--connect");
            return run(file, resolver, connect);
        } catch (IllegalArgumentException | StreamException e) {
            System.err.println("lint usage error: " + e);
            System.err.println(StreamMaintenanceMain.usage());
            return 2;
        }
    }

    private static String parseFileArg(List<String> args) {
        Map<String, String> kv = new java.util.LinkedHashMap<>();
        for (String arg : args) {
            if ("--connect".equals(arg)) {
                continue;
            }
            int eq = arg.indexOf('=');
            if (eq <= 0) {
                throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL,
                        "Malformed argument (expected key=value or --connect): " + arg);
            }
            kv.put(arg.substring(0, eq), arg.substring(eq + 1));
        }
        String file = kv.get("file");
        if (file == null || file.isBlank()) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL,
                    "Missing required argument 'file' for lint");
        }
        return file;
    }

    /** Resolves the resource, runs the linter, renders, and returns the exit code. */
    public static int run(String file, BeanFunctionResolver resolver, boolean connect) {
        IResource resource = resolveResource(file);
        StreamConfValidationReport report = new StreamModelLinter()
                .lint(resource, resolver, connect);
        System.out.print(renderLint(report));
        System.out.println("lint " + (connect ? "(--connect) " : "") + "target=" + file
                + " exit=" + report.getExitCode());
        return report.getExitCode();
    }

    /**
     * The lint-rendered report: per-issue lines from the shared validation report
     * body (ValidationIssue.describe()) under a {@code lint:} verdict header — the
     * validator's {@code conf-validate:} header would mislabel a lint run.
     */
    static String renderLint(StreamConfValidationReport report) {
        long failCount = report.getIssues().stream()
                .filter(i -> i.getSeverity() == io.nop.stream.flow.validate.ValidationIssue.Severity.FAIL)
                .count();
        long warningCount = report.getIssues().stream()
                .filter(i -> i.getSeverity() == io.nop.stream.flow.validate.ValidationIssue.Severity.WARNING)
                .count();
        long skipCount = report.getIssues().size() - failCount - warningCount;
        StringBuilder sb = new StringBuilder();
        if (failCount == 0) {
            sb.append("lint: OK (0 failed");
            if (skipCount > 0) {
                sb.append(", ").append(skipCount).append(" explicit skip item(s)");
            }
            if (warningCount > 0) {
                sb.append(", ").append(warningCount).append(" warning(s)");
            }
            sb.append(")\n");
        } else {
            sb.append("lint: FAILED (").append(failCount).append(" failed, ")
                    .append(warningCount).append(" warning(s), ")
                    .append(skipCount).append(" skipped)\n");
        }
        for (io.nop.stream.flow.validate.ValidationIssue issue : report.getIssues()) {
            sb.append("  ").append(issue.describe()).append('\n');
        }
        return sb.toString();
    }

    private static IResource resolveResource(String file) {
        if (file.startsWith("/")) {
            try {
                IResource resource = VirtualFileSystem.instance().getResource(file);
                if (resource.exists()) {
                    return resource;
                }
            } catch (NopException e) {
                // VFS not initialized (bare CLI JVM): fall through to the local-file
                // interpretation instead of crashing — an absolute local path also
                // starts with '/'.
            }
        }
        java.nio.file.Path path = Paths.get(file);
        if (!Files.isRegularFile(path)) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL,
                    "Job definition not found (VFS or local file): " + file);
        }
        return new FileResource(path.toFile());
    }
}
