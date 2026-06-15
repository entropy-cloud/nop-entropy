package io.nop.ai.agent.session;

import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.agent.model.AgentExecStatus;
import io.nop.ai.api.chat.messages.ChatUserMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 195 focused tests for the crash-safe (atomic) write path of
 * {@link SessionFileWriter}. Each test verifies one specific behavior of the
 * write-to-tmp + {@code Files.move(ATOMIC_MOVE, REPLACE_EXISTING)} pattern:
 *
 * <ol>
 *   <li><b>atomic-write-target-intact</b>: after a successful write the target
 *       file contains the complete, deserializable JSON and the {@code .tmp}
 *       sibling is gone.</li>
 *   <li><b>tmp-cleanup</b>: the {@code .tmp} file does not survive a
 *       successful write.</li>
 *   <li><b>stale-tmp-recovery</b>: a pre-existing stale {@code .tmp} file is
 *       overwritten by the next write and removed afterwards.</li>
 *   <li><b>pre-move-failure-isolation</b> (crash-safety core guarantee): when
 *       the tmp-write step fails, the existing target file is left untouched —
 *       never truncated or partially overwritten.</li>
 *   <li><b>overwrite-write</b>: two consecutive writes leave the target with
 *       the second content and no residual tmp.</li>
 *   <li><b>end-to-end persistence wiring</b>: {@link FileBackedSessionStore#save}
 *       routes through {@link SessionFileWriter#write} so the atomic write
 *       path is exercised by the real persistence pipeline.</li>
 * </ol>
 */
public class TestSessionFileWriterAtomicWrite {

    @TempDir
    Path tempDir;

    private static AgentSession sampleSession(String id, String content) {
        AgentSession s = AgentSession.create(id, "agent");
        s.appendMessages(java.util.List.of(new ChatUserMessage(content)));
        s.setStatus(AgentExecStatus.running);
        return s;
    }

    private Path targetFile(String id) {
        return tempDir.resolve(id).resolve("session.json");
    }

    private Path tmpFile(String id) {
        return targetFile(id).resolveSibling(targetFile(id).getFileName() + ".tmp");
    }

    // ========================================================================
    // (1) atomic-write-target-intact + (2) tmp-cleanup
    // ========================================================================

    @Test
    void successfulWriteLeavesCompleteTargetAndNoTmp() {
        SessionFileWriter writer = new SessionFileWriter();
        Path target = targetFile("intact");
        AgentSession session = sampleSession("intact", "hello");

        writer.write(target, session);

        assertTrue(Files.exists(target), "target file must exist after write");
        assertFalse(Files.exists(tmpFile("intact")),
                "tmp file must not survive a successful write");

        // The target must contain complete, deserializable JSON — prove it by
        // reading it back through SessionFileReader.
        SessionFileReader reader = new SessionFileReader();
        AgentSession restored = reader.readIfExists(target);
        assertTrue(restored != null, "written file must be readable as a session");
        assertEquals("intact", restored.getSessionId());
        assertEquals(1, restored.getMessageCount());
        assertEquals("hello", restored.getMessages().get(0).getContent());
    }

    // ========================================================================
    // (3) stale-tmp-recovery
    // ========================================================================

    @Test
    void staleTmpIsOverwrittenAndRemovedAfterWrite() throws Exception {
        Path target = targetFile("stale");
        Path tmp = tmpFile("stale");

        // Pre-create a stale .tmp file (simulating a crash that left a tmp
        // behind from a previous, interrupted write).
        Files.createDirectories(target.getParent());
        Files.writeString(tmp, "stale-tmp-content-from-previous-crashed-write");

        SessionFileWriter writer = new SessionFileWriter();
        AgentSession session = sampleSession("stale", "fresh-content");
        writer.write(target, session);

        // The stale tmp must be gone (overwritten then moved away).
        assertFalse(Files.exists(tmp),
                "stale tmp must not survive the next successful write");
        // The target must reflect the new content, not the stale tmp.
        SessionFileReader reader = new SessionFileReader();
        AgentSession restored = reader.readIfExists(target);
        assertTrue(restored != null);
        assertEquals("fresh-content", restored.getMessages().get(0).getContent());
    }

    // ========================================================================
    // (4) pre-move-failure-isolation (crash-safety core guarantee)
    // ========================================================================

    @Test
    void preMoveFailureLeavesExistingTargetUntouched() throws Exception {
        Path target = targetFile("iso");
        Path tmp = tmpFile("iso");

        // Pre-populate the target with valid, complete content (JSON-A).
        Files.createDirectories(target.getParent());
        Files.writeString(target, SessionFileWriter.serialize(sampleSession("iso", "json-A-original")));

        // Block the tmp-write step by pre-creating a DIRECTORY at the tmp
        // path. Files.write(tmp, ...) on an existing directory throws
        // IOException ("Is a directory") — a portable failure injection that
        // does not depend on OS file-permission semantics.
        Files.createDirectory(tmp);
        assertTrue(Files.isDirectory(tmp), "precondition: tmp path must be a blocking directory");

        SessionFileWriter writer = new SessionFileWriter();
        AgentSession newSession = sampleSession("iso", "json-B-should-never-land");

        // The write must fail fast (Minimum Rules #24 — no silent swallow).
        assertThrows(NopAiAgentException.class, () -> writer.write(target, newSession),
                "write must surface the tmp-write failure as NopAiAgentException");

        // CRASH-SAFETY GUARANTEE: the target still contains the complete
        // original JSON-A — never truncated, never partially overwritten.
        assertTrue(Files.exists(target),
                "target must still exist after a pre-move failure");
        SessionFileReader reader = new SessionFileReader();
        AgentSession survivor = reader.readIfExists(target);
        assertTrue(survivor != null,
                "target must still be a valid, complete session after a failed write");
        assertEquals("json-A-original", survivor.getMessages().get(0).getContent(),
                "target content must be the original, not truncated or partially overwritten");

        // The blocking tmp artifact is cleaned up by the finally block.
        // (deleteIfExists removes a directory only if empty; our injected
        // blocking dir is empty, so it is removed — proving the finally runs.)
        assertFalse(Files.exists(tmp),
                "finally cleanup must remove the tmp artifact even on failure");
    }

    // ========================================================================
    // (5) overwrite-write
    // ========================================================================

    @Test
    void consecutiveOverwritesLeaveLatestContentAndNoTmp() {
        SessionFileWriter writer = new SessionFileWriter();
        Path target = targetFile("overwrite");

        writer.write(target, sampleSession("overwrite", "first-content"));
        assertFalse(Files.exists(tmpFile("overwrite")),
                "no tmp after first write");

        writer.write(target, sampleSession("overwrite", "second-content"));
        assertFalse(Files.exists(tmpFile("overwrite")),
                "no tmp after second write");

        // The target must hold the second (latest) content.
        SessionFileReader reader = new SessionFileReader();
        AgentSession restored = reader.readIfExists(target);
        assertTrue(restored != null);
        assertEquals("second-content", restored.getMessages().get(0).getContent(),
                "target must contain the latest content after consecutive overwrites");
    }

    // ========================================================================
    // (6) end-to-end persistence wiring (Minimum Rules #22 + #23)
    // FileBackedSessionStore.save() -> SessionFileWriter.write() -> disk
    // ========================================================================

    @Test
    void fileBackedStoreSaveWritesViaAtomicPathToDisk() {
        Path root = tempDir.resolve("e2e");
        FileBackedSessionStore store = new FileBackedSessionStore(root);

        AgentSession session = sampleSession("e2e-1", "persisted-via-store");
        store.save(session);

        Path target = root.resolve("e2e-1").resolve(FileBackedSessionStore.SESSION_FILE_NAME);
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");

        assertTrue(Files.exists(target),
                "store.save must persist session.json to disk via SessionFileWriter");
        assertFalse(Files.exists(tmp),
                "store.save must leave no tmp residue (proves atomic move completed)");

        // The on-disk file is complete and readable (end-to-end: the file is
        // the crash-recovery source of truth).
        SessionFileReader reader = new SessionFileReader();
        AgentSession onDisk = reader.readIfExists(target);
        assertTrue(onDisk != null, "on-disk session.json must be complete and readable");
        assertEquals("persisted-via-store", onDisk.getMessages().get(0).getContent());

        // Cross-instance reload proves the persistence pipeline is wired end-to-end.
        FileBackedSessionStore store2 = new FileBackedSessionStore(root);
        AgentSession reloaded = store2.get("e2e-1");
        assertTrue(reloaded != null, "a new store instance must reload the persisted session");
        assertEquals("persisted-via-store", reloaded.getMessages().get(0).getContent());
    }

    // ========================================================================
    // sanity: written bytes are real JSON, not empty/truncated
    // ========================================================================

    @Test
    void writtenBytesAreNonEmptyJson() throws Exception {
        SessionFileWriter writer = new SessionFileWriter();
        Path target = targetFile("bytes");
        writer.write(target, sampleSession("bytes", "payload"));

        byte[] bytes = Files.readAllBytes(target);
        assertTrue(bytes.length > 0, "target file must not be empty after write");
        String text = new String(bytes, StandardCharsets.UTF_8).trim();
        assertTrue(text.startsWith("{") && text.endsWith("}"),
                "target file must contain a JSON object, got: " + text);
    }
}
