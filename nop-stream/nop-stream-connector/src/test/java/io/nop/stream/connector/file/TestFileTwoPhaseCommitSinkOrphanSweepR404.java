/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.connector.file;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CRA 8.44 R4-04 (plan 2537) red⑧ acceptance pin — W-08 (finding 1.2-C6-007;
 * adjudication D.1 / seam S8, dual face binding): epoch temp files whose
 * checkpoint never went durable must be swept at the recovery chain's last step
 * ({@code beginTransaction}), under the manifest ∪ pending double whitelist and
 * the self-subtask-suffix filter.
 *
 * <p>Injection set (three disjoint classes + manifest face):
 * <ul>
 *   <li><b>orphan</b> {@code .epoch-7.tmp} — self suffix, in NO whitelist → must be
 *       deleted (the pre-fix defect face: the in-memory registration died with the
 *       process and no startup scan existed);</li>
 *   <li><b>other-subtask in-flight</b> {@code .epoch-9.s1.tmp} — structurally
 *       invisible to this subtask's scan (parallel 2PC isolation, AR-14) → must
 *       survive;</li>
 *   <li><b>manifest-recorded</b> {@code .epoch-3.tmp} — the manifest whitelist
 *       (main whitelist at this hook) → must survive;</li>
 *   <li><b>commit-failure-retained</b> {@code .epoch-5.tmp} — created through the
 *       REAL chain (saveState → commit throws → pending retained, the MJ-3 leg-(c)
 *       construction) and protected by the pending whitelist → must survive;</li>
 *   <li><b>manifest temp face②</b> {@code manifest.properties.tmp} (+ the
 *       {@code .s2} shape on a subtask-2 sink copy) — ATOMIC_MOVE-failure residue,
 *       never enters manifest content → must be deleted.</li>
 * </ul>
 *
 * <p>Zero-touch guards (implicitly exercised): the sweep never touches finals
 * ({@code epoch-*.txt}), the manifest itself, or the lock file; it does not enter
 * the {@code withManifestLock} protocol (the manifest is only READ).
 */
class TestFileTwoPhaseCommitSinkOrphanSweepR404 {

    @TempDir
    Path tempDir;

    private static FileTwoPhaseCommitSink<String> newSink(Path dir, int subtaskIndex) {
        // the retention window is the SSOT default (the sweep is a zero-knob face)
        return new FileTwoPhaseCommitSink<>(dir.toString(), StandardCharsets.UTF_8,
                subtaskIndex, FileTwoPhaseCommitSink.DEFAULT_RETENTION_EPOCH_WINDOW);
    }

    private static void touch(Path dir, String name, String content) throws Exception {
        Files.writeString(dir.resolve(name), content, StandardCharsets.UTF_8);
    }

    @Test
    void beginTransactionSweepsOrphanTempsAndSparesTheWhitelists() throws Exception {
        FileTwoPhaseCommitSink<String> sink = newSink(tempDir, 0);
        // ---- injection set ----
        touch(tempDir, ".epoch-7.tmp", "orphan batch"); // orphan (defect face)
        touch(tempDir, ".epoch-9.s1.tmp", "peer subtask in flight"); // other suffix
        touch(tempDir, ".epoch-3.tmp", "manifest-recorded epoch batch"); // manifest whitelist
        // the manifest records epoch 3 (main whitelist at the begin hook)
        Properties manifest = new Properties();
        manifest.setProperty("3", tempDir.resolve("epoch-3.txt").toString());
        java.io.OutputStream out = Files.newOutputStream(tempDir.resolve("manifest.properties"));
        manifest.store(out, null);
        out.close();
            // a legal FINAL file must never be touched
            touch(tempDir, "epoch-3.txt", "final durable batch");

        // commit-failure-retained pending (MJ-3 leg (c), real chain):
        // saveState(5) writes .epoch-5.tmp + registers pending; then commit(5) fails
        // (final path occupied by a DIRECTORY → Files.move cannot atomic-rename),
        // so the entry STAYS in pendingCommits with its temp on disk.
        sink.invoke("row-5a");
        sink.saveState(5L);
        assertTrue(Files.exists(tempDir.resolve(".epoch-5.tmp")),
                "precondition: the epoch-5 temp exists");
        Files.createDirectories(tempDir.resolve("epoch-5.txt"));
        assertThrows(Exception.class, () -> sink.commit(5L),
                "the injected move failure must fail the commit");
        assertFalse(sink.getPendingCommits().isEmpty(),
                "precondition: the failed commit RETAINS its pending entry (MJ-3)");

        // ---- trigger: beginTransaction = the recovery chain's last step ----
        sink.beginTransaction();

        // ---- verdicts ----
        assertFalse(Files.exists(tempDir.resolve(".epoch-7.tmp")),
                "the orphan self-suffix temp must be swept at beginTransaction "
                        + "(pre-fix: no scan existed, the temp survived forever — "
                        + "CRA 8.44 R4-04 W-08 red⑧)");
        assertTrue(Files.exists(tempDir.resolve(".epoch-9.s1.tmp")),
                "another subtask's in-flight temp must survive (suffix filter, AR-14)");
        assertTrue(Files.exists(tempDir.resolve(".epoch-3.tmp")),
                "a manifest-recorded epoch temp must survive (manifest whitelist)");
        assertTrue(Files.exists(tempDir.resolve(".epoch-5.tmp")),
                "a commit-failure-retained temp must survive (pending whitelist, MJ-3)");
        assertTrue(Files.exists(tempDir.resolve("epoch-3.txt")),
                "finals are never touched by the sweep");
    }

    @Test
    void manifestTempFaceIsSweptPerSubtaskSuffix() throws Exception {
        FileTwoPhaseCommitSink<String> sink0 = newSink(tempDir, 0);
        FileTwoPhaseCommitSink<String> sink2 = newSink(tempDir, 2);
        // ATOMIC_MOVE-failure residue shapes (K10 naming: manifest.properties{suffix}.tmp)
        touch(tempDir, "manifest.properties.tmp", "subtask-0 residue");
        touch(tempDir, "manifest.properties.s2.tmp", "subtask-2 residue");
        // a PEER subtask's manifest temp is invisible to subtask 0's scan
        touch(tempDir, "manifest.properties.s1.tmp", "subtask-1 residue");

        sink0.beginTransaction();
        assertFalse(Files.exists(tempDir.resolve("manifest.properties.tmp")),
                "the self-suffix manifest temp must be swept (W-08 face②)");
        assertTrue(Files.exists(tempDir.resolve("manifest.properties.s1.tmp")),
                "a peer subtask's manifest temp must survive subtask 0's sweep");

        sink2.beginTransaction();
        assertFalse(Files.exists(tempDir.resolve("manifest.properties.s2.tmp")),
                "subtask 2's sweep must remove its own suffix manifest temp");
        assertTrue(Files.exists(tempDir.resolve("manifest.properties.s1.tmp")),
                "subtask 2 must not touch subtask 1's manifest temp either");
    }

    @Test
    void sweepSurvivesAMissingOrCorruptManifestConservatively() throws Exception {
        FileTwoPhaseCommitSink<String> sink = newSink(tempDir, 0);
        // a corrupt manifest (malformed unicode escape → IllegalArgumentException from
        // Properties.load) must not fail the recovery chain — the sweep degrades to
        // "skip everything" (fail-safe: conservative keep)
        touch(tempDir, "manifest.properties", "epoch=\\uZZZZ broken escape");
        touch(tempDir, ".epoch-11.tmp", "orphan under corrupt manifest");

        sink.beginTransaction(); // must NOT throw
        assertTrue(Files.exists(tempDir.resolve(".epoch-11.tmp")),
                "with an unreadable manifest whitelist the sweep must keep the file "
                        + "(conservative fail-safe, adjudication D.2)");
    }
}
