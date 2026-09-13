/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://github.com/entropy-cloud/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.maintain;

import io.nop.stream.core.exceptions.StreamException;
import static io.nop.stream.core.exceptions.NopStreamErrors.ARG_DETAIL;
import static io.nop.stream.core.exceptions.NopStreamErrors.ERR_STREAM_INVALID_ARG;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import io.nop.api.core.exceptions.NopException;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.impl.FileResource;
import io.nop.stream.flow.builder.BeanFunctionResolver;
import io.nop.stream.flow.validate.StreamConfValidationReport;
import io.nop.stream.flow.validate.StreamConfValidator;

/**
 * Item 20 (P-REQ-14): the {@code conf-validate} / {@code dry-run} subcommands of the
 * {@link StreamMaintenanceMain} entry family (pre-submit-validation-design.md D1/D2/D7).
 * Validates a stream job definition WITHOUT starting it:
 *
 * <pre>
 *   conf-validate file=&lt;path&gt; [--connect]
 *   dry-run file=&lt;path&gt;          (equivalent to conf-validate --connect)
 * </pre>
 *
 * <p>Bean source (D2): the CLI form falls back to the global container
 * ({@code GlobalBeanFunctionResolver}); the embedding form passes an explicit
 * {@link BeanFunctionResolver} — programmatic ({@code InMemoryBeanFunctionResolver},
 * the S1/S2 scenario shape) or an explicitly assembled container wrapped in
 * {@code BeanContainerFunctionResolver}.
 *
 * <p>Exit code contract (D7): 0 = passed (explicit skip items allowed), 1 = validation
 * failed, 2 = usage error. {@code file} may be a VFS path (leading '/') or a local
 * filesystem path.
 */
public final class StreamConfValidateCommand {

    private StreamConfValidateCommand() {
    }

    /** CLI form: global-container bean source. Returns the process exit code. */
    public static int run(List<String> args) {
        return run(args, null);
    }

    /**
     * Embedding form: beans are resolved through the given resolver (D2 forms 1/2);
     * a null resolver falls back to the global container (D2 form 3). Usage errors
     * (missing/malformed arguments, nonexistent file) return exit code 2; the
     * validation verdict decides 0 vs 1.
     */
    public static int run(List<String> args, BeanFunctionResolver resolver) {
        try {
            String file = parseFileArg(args);
            boolean connect = args.contains("--connect");
            return run(file, resolver, connect);
        } catch (IllegalArgumentException | StreamException e) {
            System.err.println("conf-validate usage error: " + e);
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
                throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "Malformed argument (expected key=value or --connect): " + arg);
            }
            kv.put(arg.substring(0, eq), arg.substring(eq + 1));
        }
        String file = kv.get("file");
        if (file == null || file.isBlank()) {
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "Missing required argument 'file' for conf-validate");
        }
        return file;
    }

    /** Resolves the resource and runs the validator; returns the report exit code. */
    public static int run(String file, BeanFunctionResolver resolver, boolean connect) {
        IResource resource = resolveResource(file);
        StreamConfValidationReport report = new StreamConfValidator()
                .validateStream(resource, resolver, connect);
        System.out.print(report.render());
        System.out.println("conf-validate " + (connect ? "(--connect) " : "") + "target=" + file
                + " exit=" + report.getExitCode());
        return report.getExitCode();
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
            throw new StreamException(ERR_STREAM_INVALID_ARG).param(ARG_DETAIL, "Job definition not found (VFS or local file): " + file);
        }
        return new FileResource(path.toFile());
    }
}
