package io.nop.ai.agent.guardrail;

import io.nop.ai.agent.engine.AgentExecutionContext;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Production-grade prompt injection guardrail based on the OpenSquilla
 * injection taxonomy (prompt_override / role_hijack / exfiltration /
 * invisible_char), per design doc
 * {@code nop-ai-agent-security-and-permissions.md} §5.2.
 * <p>
 * Execution semantics:
 * <ul>
 * <li>{@link GuardrailMode#OFF} — detection disabled, always Pass.</li>
 * <li>{@link GuardrailMode#REPORT} — detection runs; on hit the finding is
 * logged via WARN and the content is allowed (Pass).</li>
 * <li>{@link GuardrailMode#ENFORCE} (default) — detection runs; on hit a
 * {@link GuardrailResult.BlockResult} is returned with the threat class and
 * matched fragment as the reason.</li>
 * </ul>
 * The same rule set applies to both {@link GuardrailDirection#INPUT} and
 * {@link GuardrailDirection#OUTPUT} (defense in depth — an LLM echoing back an
 * injected instruction is also detected).
 */
public class PromptInjectionGuardrail implements IContentGuardrail {

    static final Logger LOG = LoggerFactory.getLogger(PromptInjectionGuardrail.class);

    private static final int LOG_TRUNCATE_LENGTH = 200;

    private static final Pattern PROMPT_OVERRIDE = Pattern.compile(
            "(?i)(ignore\\s+(all\\s+)?(previous|prior|above|earlier)\\s+(instructions|prompts|directions|context)|"
                    + "disregard\\s+(all\\s+)?(previous|prior|above)\\s+(instructions|prompts|directions)|"
                    + "forget\\s+(all\\s+)?(previous|prior)\\s+(instructions|prompts|context|conversation)|"
                    + "you\\s+(are|now\\s+are)\\s+(no\\s+longer\\s+bound|free\\s+from)|"
                    + "override\\s+(your|all|the)\\s+(instructions|prompts|system\\s+prompt)|"
                    + "do\\s+not\\s+follow\\s+(your|the)\\s+(instructions|system\\s+prompt|rules))");

    private static final Pattern ROLE_HIJACK = Pattern.compile(
            "(?i)(pretend\\s+(you\\s+are|to\\s+be)\\s+(the\\s+)?(system|admin|root|developer)|"
                    + "you\\s+(are\\s+now|now\\s+are|are)\\s+(the\\s+)?(system|admin|root|developer|assistant\\s+admin)|"
                    + "act\\s+as\\s+(the\\s+)?(system|admin|root|developer)|"
                    + "role\\s*[:=]\\s*(system|admin|root|developer)|"
                    + "your\\s+(new\\s+)?role\\s+is\\s+(system|admin|root|developer)|"
                    + "you\\s+are\\s+now\\s+in\\s+developer\\s+mode|"
                    + "simulate\\s+being\\s+(the\\s+)?(system|admin|root))");

    private static final Pattern EXFILTRATION = Pattern.compile(
            "(?i)(print|reveal|show|display|leak|exfiltrate|repeat)\\s+(your|the|my|me)\\s+(system\\s+prompt|instructions|prompt|initial\\s+instructions|"
                    + "secret|secrets|api\\s+key|keys|password|passwords|credentials|tokens)|"
                    + "(what\\s+is|tell\\s+me)\\s+(your|the)\\s+(system\\s+prompt|api\\s+key|secret|password|instructions)|"
                    + "exfiltrate\\s+(data|secrets|content|information)|"
                    + "send\\s+(me|us|the)\\s+(your\\s+)?(api\\s+key|system\\s+prompt|instructions|secret)");

    private static final Pattern INVISIBLE_CHAR = Pattern.compile("[\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2064\\uFEFF\\u00AD\\u2066-\\u2069]");

    private final GuardrailMode mode;

    public PromptInjectionGuardrail() {
        this(GuardrailMode.ENFORCE);
    }

    public PromptInjectionGuardrail(GuardrailMode mode) {
        this.mode = mode != null ? mode : GuardrailMode.ENFORCE;
    }

    public GuardrailMode getMode() {
        return mode;
    }

    @Override
    public GuardrailResult check(GuardrailDirection direction, String content, AgentExecutionContext ctx) {
        if (mode == GuardrailMode.OFF || content == null || content.isEmpty()) {
            return GuardrailResult.PassResult.instance();
        }

        List<String> findings = new ArrayList<>();
        addMatch(findings, "prompt_override", PROMPT_OVERRIDE, content);
        addMatch(findings, "role_hijack", ROLE_HIJACK, content);
        addMatch(findings, "exfiltration", EXFILTRATION, content);
        addMatch(findings, "invisible_char", INVISIBLE_CHAR, content);

        if (findings.isEmpty()) {
            return GuardrailResult.PassResult.instance();
        }

        String reason = "prompt injection detected (" + String.join(", ", findings) + ")";

        if (mode == GuardrailMode.REPORT) {
            LOG.warn("PromptInjectionGuardrail[REPORT]: {} direction={} content={}", reason, direction, truncate(content));
            return GuardrailResult.PassResult.instance();
        }

        return new GuardrailResult.BlockResult(reason);
    }

    private static void addMatch(List<String> findings, String threatClass, Pattern pattern, String content) {
        if (pattern.matcher(content).find()) {
            findings.add(threatClass);
        }
    }

    private static String truncate(String content) {
        return content.length() > LOG_TRUNCATE_LENGTH
                ? content.substring(0, LOG_TRUNCATE_LENGTH) + "..." : content;
    }
}
