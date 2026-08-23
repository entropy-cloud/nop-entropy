package io.nop.ai.tools.utils;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.context.IServiceContext;

import java.util.regex.Pattern;

import static io.nop.ai.core.NopAiCoreErrors.ARG_SESSION_ID;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_SESSION_ID_INVALID;
import static io.nop.ai.core.NopAiCoreErrors.ERR_AI_SESSION_ID_IS_EMPTY;

public class AiToolsHelper {
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
}
