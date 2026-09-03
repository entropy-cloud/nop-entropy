/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.stream.runtime.maintain;

/**
 * Item 16 (P-REQ-10): the nop-stream maintenance tool entry family. Both
 * state-maintenance operations share ONE entry (Phase 1 decision —
 * convergence of the reset tool and the offline reshard tool, D-GAP §2.5
 * observation) with the same validate-first / act / report semantics:
 *
 * <pre>
 *   reset-state jobId=&lt;id&gt; checkpointBaseDir=&lt;dir&gt; sourceReplayable=true|false
 *   reshard oldSavepointPath=&lt;path&gt; oldMaxParallelism=&lt;n&gt; newMaxParallelism=&lt;m&gt; outputBaseDir=&lt;dir&gt;
 * </pre>
 *
 * <p>Item 20 (P-REQ-13/14) joins the same family with the pre-submit validation
 * subcommands (pre-submit-validation-design.md D1):
 *
 * <pre>
 *   conf-validate file=&lt;path&gt; [--connect]
 *   dry-run file=&lt;path&gt;
 * </pre>
 *
 * <p>Fail-fast with a non-zero exit code and a stderr message on invalid
 * arguments or failed preconditions (no silent success).
 */
public final class StreamMaintenanceMain {

    private StreamMaintenanceMain() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println(usage());
            System.exit(2);
            return;
        }
        try {
            switch (args[0]) {
                case "reset-state":
                    runReset(java.util.Arrays.asList(args).subList(1, args.length));
                    break;
                case "reshard":
                    runReshard(java.util.Arrays.asList(args).subList(1, args.length));
                    break;
                case "conf-validate":
                    runConfValidate(java.util.Arrays.asList(args).subList(1, args.length), false);
                    break;
                case "dry-run":
                    runConfValidate(java.util.Arrays.asList(args).subList(1, args.length), true);
                    break;
                default:
                    System.err.println("Unknown subcommand: " + args[0]);
                    System.err.println(usage());
                    System.exit(2);
            }
        } catch (Exception e) {
            System.err.println("stream-maintenance failed:");
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    /**
     * Item 20 (P-REQ-14): conf-validate / dry-run subcommands. Usage errors exit 2;
     * the validation verdict decides 0 (passed, explicit skips allowed) vs 1 (failed)
     * per the D7 exit-code contract (mapping lives in {@link StreamConfValidateCommand}).
     */
    private static void runConfValidate(java.util.List<String> argList, boolean connect) {
        java.util.List<String> effective = new java.util.ArrayList<>(argList);
        if (connect && !effective.contains("--connect")) {
            effective.add("--connect");
        }
        System.exit(StreamConfValidateCommand.run(effective, null));
    }

    private static void runReset(java.util.List<String> argList) {
        java.util.Map<String, String> kv = parseKeyValueArgs(argList);
        String jobId = require(kv, "jobId", "reset-state");
        String baseDir = require(kv, "checkpointBaseDir", "reset-state");
        String replayable = require(kv, "sourceReplayable", "reset-state");

        StreamStateResetTool.ResetResult result = StreamStateResetTool.reset(
                jobId, baseDir, Boolean.parseBoolean(replayable), null);
        System.out.println("reset-state OK: jobId=" + result.getJobId()
                + " deletedPath=" + result.getDeletedPath().toAbsolutePath()
                + " deletedCheckpoints=" + result.getDeletedCheckpoints());
    }

    private static void runReshard(java.util.List<String> argList) {
        java.util.Map<String, String> kv = parseKeyValueArgs(argList);
        String oldPath = require(kv, "oldSavepointPath", "reshard");
        int oldMaxP = Integer.parseInt(require(kv, "oldMaxParallelism", "reshard"));
        int newMaxP = Integer.parseInt(require(kv, "newMaxParallelism", "reshard"));
        String outDir = require(kv, "outputBaseDir", "reshard");

        io.nop.stream.runtime.checkpoint.reshard.ReshardMigrationResult result =
                io.nop.stream.runtime.checkpoint.reshard.MaxParallelismReshardMigration.migrate(
                        oldPath, oldMaxP, newMaxP, outDir);
        System.out.println("reshard OK: oldSavepointPath=" + result.getOldSavepointPath()
                + " newSavepointPath=" + result.getNewSavepointPath()
                + " oldMaxParallelism=" + result.getOldMaxParallelism()
                + " newMaxParallelism=" + result.getNewMaxParallelism()
                + " keyCountByState=" + result.getKeyCountByState());
    }

    private static java.util.Map<String, String> parseKeyValueArgs(java.util.List<String> args) {
        java.util.Map<String, String> kv = new java.util.LinkedHashMap<>();
        for (String arg : args) {
            int eq = arg.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("Malformed argument (expected key=value): " + arg);
            }
            kv.put(arg.substring(0, eq), arg.substring(eq + 1));
        }
        return kv;
    }

    private static String require(java.util.Map<String, String> kv, String key, String subcommand) {
        String value = kv.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required argument '" + key + "' for subcommand '"
                    + subcommand + "'");
        }
        return value;
    }

    public static String usage() {
        return "Usage: StreamMaintenanceMain <subcommand> key=value...\n"
                + "Subcommands:\n"
                + "  reset-state jobId=<id> checkpointBaseDir=<dir> sourceReplayable=true|false\n"
                + "      Clears the job's local checkpoint state (durable checkpoints + epoch\n"
                + "      manifests + source cursors) for a fresh re-run. Refuses to run when\n"
                + "      sourceReplayable=false or the state directory does not exist.\n"
                + "  reshard oldSavepointPath=<path> oldMaxParallelism=<n> newMaxParallelism=<m> outputBaseDir=<dir>\n"
                + "      Offline max-parallelism reshard of a savepoint (delegates to\n"
                + "      MaxParallelismReshardMigration).\n"
                + "  conf-validate file=<path> [--connect]\n"
                + "      Validates a stream job definition (XDSL) WITHOUT starting it: layer 1\n"
                + "      model parse (stream.xdef field-level) + layer 2 graph construction.\n"
                + "      --connect adds layer 3 connectivity probing (equivalent to dry-run).\n"
                + "      Exit code: 0 = passed, 1 = validation failed, 2 = usage error.\n"
                + "  dry-run file=<path>\n"
                + "      Pre-submit connectivity dry-run: conf-validate --connect (P-REQ-13).\n"
                + "      Probes each source/sink endpoint per connector family and reports\n"
                + "      per-family results; families without a probe contract are reported\n"
                + "      as explicit skip items (never silently passed).";
    }
}
