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
                + "      MaxParallelismReshardMigration).";
    }
}
