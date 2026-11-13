package io.nop.ai.tools.utils;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;

import static io.nop.ai.core.NopAiCoreErrors.ARG_SESSION_ID;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_SESSION_ID_INVALID;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_SESSION_ID_IS_EMPTY;

public class AiToolsHelper {
    private static final Logger LOG = LoggerFactory.getLogger(AiToolsHelper.class);

    static final String HEADER_CHAT_SESSION_ID = "nop-chat-session-Id";

    /**
     * Same allow-list as nop-ai-core's {@code ChatLogHelper} and nop-ai-agent's
     * {@code SessionIds.requireValidIdentifier}: the client-supplied chat session
     * id is embedded into a file path under the thought-storage dir, so any
     * character outside [A-Za-z0-9_-] would allow path traversal outside the
     * storage dir (audit ai-toolkit-skills P0). Fail-closed: reject, never
     * silently sanitize — a session id is an identity key, rewriting it would
     * silently merge distinct sessions.
     */
    private static final Pattern SAFE_SESSION_ID = Pattern.compile("^[A-Za-z0-9_-]+$");

    public static String makeChatSessionId(IServiceContext ctx) {
        String sessionId = (String) ctx.getRequestHeader(HEADER_CHAT_SESSION_ID);
        if (StringHelper.isEmpty(sessionId)) {
            sessionId = StringHelper.generateUUID();
        }
        return requireValidSessionId(sessionId);
    }

    /**
     * Path-traversal guard for session ids that are concatenated into file names
     * (counterpart of {@code FileToolBizModel.getProjectDir}'s projectName
     * cleaning, but fail-closed instead of sanitizing). Rejects {@code null},
     * empty, and any id containing a character outside {@code [A-Za-z0-9_-]} —
     * this catches {@code /}, {@code \}, {@code ..}, absolute paths, NUL,
     * whitespace and any Unicode.
     *
     * @return the validated session id (unchanged)
     */
    public static String requireValidSessionId(String sessionId) {
        if (StringHelper.isEmpty(sessionId)) {
            throw new NopException(ERR_AI_SESSION_ID_IS_EMPTY);
        }
        if (!SAFE_SESSION_ID.matcher(sessionId).matches()) {
            throw new NopException(ERR_AI_SESSION_ID_INVALID)
                    .param(ARG_SESSION_ID, sessionId);
        }
        return sessionId;
    }

    /**
     * Atomic replace for local file writes (plan 2557 C1-005, ruling C): copied
     * verbatim from the {@code ThoughtStorage.atomicWriteText} template — write
     * to a temp file in the same directory, then rename over the target, so a
     * partial write or crash never truncates the previous content. {@code
     * ATOMIC_MOVE} is attempted first; plain {@code Files.move} with {@code
     * REPLACE_EXISTING} is the explicit fallback for filesystems without
     * atomic rename support. Applies ONLY to local {@code FileResource} write
     * sites; virtual (VFS, non-local) resources keep their original in-place
     * write path.
     */
    public static void atomicWriteText(File target, String text) {
        FileHelper.assureParent(target);
        File temp = new File(target.getParentFile(), target.getName() + ".tmp" + UUID.randomUUID());
        try {
            Files.writeString(temp.toPath(), text, StandardCharsets.UTF_8);
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw NopException.adapt(e);
        } finally {
            try {
                Files.deleteIfExists(temp.toPath());
            } catch (IOException e) {
                LOG.debug("Failed to delete temp file after write: {}", temp, e);
            }
        }
    }
}
