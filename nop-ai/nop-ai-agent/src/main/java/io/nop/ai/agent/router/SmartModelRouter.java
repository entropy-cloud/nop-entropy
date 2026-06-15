package io.nop.ai.agent.router;

import io.nop.ai.agent.budget.BudgetSnapshot;
import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.agent.engine.NopAiAgentException;
import io.nop.ai.api.chat.ChatOptions;
import io.nop.ai.api.chat.messages.ChatMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Plan 209: functional {@link IModelRouter} that routes requests by complexity
 * tier with budget-aware downgrade and a configurable fallback chain (design
 * {@code nop-ai-agent-llm-layer.md} §6.3–6.5).
 *
 * <p><b>Complexity classification (heuristic, first version)</b>: each request
 * is classified into {@link Complexity#SIMPLE}, {@link Complexity#MEDIUM}, or
 * {@link Complexity#COMPLEX} using observable signals — total message content
 * length, the number of available tools, and the presence of code / structured
 * content. The thresholds are configurable with sensible defaults. This is a
 * deliberate first-version choice: a Judge-LLM classifier (design §6.3 Step 3)
 * adds latency and cost and is an independent successor (Non-Goal).
 *
 * <p><b>Tier routing</b>: the integrator configures a primary
 * {@link ChatOptions} (provider+model) per complexity tier. The router selects
 * the primary model for the classified tier, preserving the incoming request's
 * tools and settings (only provider/model — and any other non-null tier fields
 * — are overridden via {@link ChatOptions#merge}).
 *
 * <p><b>Budget-aware downgrade</b>: when {@link AgentExecutionContext#getBudgetSnapshot()}
 * reports {@link BudgetSnapshot#isExceeded() exceeded == true}, the router
 * downgrades to the highest configured tier strictly cheaper than the
 * classified tier. If no cheaper tier is configured, the classified tier is
 * kept (cannot downgrade further) and the routing reason records that the
 * budget was exceeded but no cheaper model was available.
 *
 * <p><b>Fallback chain</b>: each tier may carry an ordered fallback chain. When
 * the ReAct retry loop receives {@code RetryDecision.FALLBACK}, it calls
 * {@link #getFallback(ChatOptions)}; the router returns the next model in the
 * current tier's chain (fully merged with the current options so tools are
 * preserved), or {@code null} when the chain is exhausted (fail-loud).
 *
 * <p>{@link PassThroughModelRouter} remains the shipped default; this class is
 * opt-in (constructed explicitly by the integrator and injected via
 * {@code ReActAgentExecutor.Builder.modelRouter}).
 */
public final class SmartModelRouter implements IModelRouter {

    // ---- Default heuristic thresholds ----
    /** Default total content length (chars) at/above which a request is at least MEDIUM. */
    public static final int DEFAULT_MEDIUM_CHARS = 200;
    /** Default total content length (chars) at/above which a request is COMPLEX. */
    public static final int DEFAULT_COMPLEX_CHARS = 2000;
    /** Default tool count at/above which a request is at least MEDIUM. */
    public static final int DEFAULT_MEDIUM_TOOLS = 2;
    /** Default tool count at/above which a request is COMPLEX. */
    public static final int DEFAULT_COMPLEX_TOOLS = 5;

    private final Map<Complexity, List<ChatOptions>> tierSequences;
    private final int mediumChars;
    private final int complexChars;
    private final int mediumTools;
    private final int complexTools;

    private SmartModelRouter(Map<Complexity, List<ChatOptions>> tierSequences,
                             int mediumChars, int complexChars,
                             int mediumTools, int complexTools) {
        this.tierSequences = Collections.unmodifiableMap(tierSequences);
        this.mediumChars = mediumChars;
        this.complexChars = complexChars;
        this.mediumTools = mediumTools;
        this.complexTools = complexTools;
    }

    @Override
    public RoutingResult route(List<ChatMessage> messages, ChatOptions options, AgentExecutionContext ctx) {
        Objects.requireNonNull(options, "options must not be null");

        Complexity classified = classify(messages, options);
        Complexity target = classified;

        boolean downgraded = false;
        boolean budgetExceededButNoCheaper = false;
        BudgetSnapshot snapshot = ctx != null ? ctx.getBudgetSnapshot() : null;
        if (snapshot != null && snapshot.isExceeded()) {
            Complexity cheaper = highestConfiguredTierBelow(classified);
            if (cheaper != null) {
                target = cheaper;
                downgraded = true;
            } else {
                budgetExceededButNoCheaper = true;
            }
        }

        ChatOptions primary = primaryOf(target);
        if (primary == null) {
            // Minimum Rules #24: no silent null/empty return. The classified
            // (or downgraded) tier has no configured model — a configuration
            // gap the operator must fix.
            throw new NopAiAgentException(
                    "SmartModelRouter has no model configured for complexity tier '"
                            + target.getKey() + "' (classified='" + classified.getKey()
                            + "'). Configure a primary model for this tier.");
        }

        ChatOptions routed = applyModel(options, primary);

        String reason = buildReason(classified, target, downgraded, budgetExceededButNoCheaper);
        // The complexity field records the request's actual classification
        // (not the post-downgrade target tier): a budget-downgraded request is
        // still complex in nature, just routed to a cheaper model — the
        // routingReason carries the downgrade explanation.
        return new RoutingResult(routed, classified.getKey(), reason);
    }

    @Override
    public ChatOptions getFallback(ChatOptions currentOptions) {
        if (currentOptions == null) {
            return null;
        }
        String currentKey = modelKey(currentOptions);
        // Search every tier's full sequence ([primary, fallback1, ...]) in a
        // deterministic order (strongest tier first). When the current model
        // matches an element that has a successor in its sequence, return a
        // merged options built from the successor so the request keeps its
        // tools/settings. When the match is the last element of every sequence
        // that contains it, the chain is exhausted → null (fail-loud).
        for (Complexity tier : new Complexity[]{Complexity.COMPLEX, Complexity.MEDIUM, Complexity.SIMPLE}) {
            List<ChatOptions> seq = tierSequences.get(tier);
            if (seq == null) {
                continue;
            }
            for (int i = 0; i < seq.size(); i++) {
                if (currentKey.equals(modelKey(seq.get(i)))) {
                    if (i + 1 < seq.size()) {
                        return applyModel(currentOptions, seq.get(i + 1));
                    }
                    // Matched the tail of this tier's chain. This tier cannot
                    // provide a further fallback; keep scanning in case the
                    // same model also appears in another tier's chain with a
                    // successor (defensive — chains are normally disjoint).
                    break;
                }
            }
        }
        return null;
    }

    // ========================================================================
    // Classification
    // ========================================================================

    Complexity classify(List<ChatMessage> messages, ChatOptions options) {
        int totalChars = 0;
        boolean hasCode = false;
        boolean hasStructured = false;
        if (messages != null) {
            for (ChatMessage msg : messages) {
                String content = msg.getContent();
                if (content == null || content.isEmpty()) {
                    continue;
                }
                totalChars += content.length();
                if (!hasCode && content.indexOf("```") >= 0) {
                    hasCode = true;
                }
                if (!hasStructured && (content.indexOf('{') >= 0 || content.indexOf('<') >= 0)) {
                    hasStructured = true;
                }
            }
        }
        int toolCount = options.getTools() != null ? options.getTools().size() : 0;

        if (totalChars >= complexChars || toolCount >= complexTools || hasCode) {
            return Complexity.COMPLEX;
        }
        if (totalChars >= mediumChars || toolCount >= mediumTools || hasStructured) {
            return Complexity.MEDIUM;
        }
        return Complexity.SIMPLE;
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private ChatOptions primaryOf(Complexity tier) {
        List<ChatOptions> seq = tierSequences.get(tier);
        return (seq != null && !seq.isEmpty()) ? seq.get(0) : null;
    }

    /**
     * @return the highest configured tier strictly cheaper than {@code tier},
     *         or {@code null} if none is configured
     */
    private Complexity highestConfiguredTierBelow(Complexity tier) {
        Complexity best = null;
        for (Complexity c : Complexity.values()) {
            if (c.getLevel() < tier.getLevel() && tierSequences.containsKey(c)) {
                if (best == null || c.getLevel() > best.getLevel()) {
                    best = c;
                }
            }
        }
        return best;
    }

    /**
     * Build the routed options by copying the incoming options (which carry the
     * agent's tools/settings) and merging the tier model options on top so only
     * the model identity (and any other non-null tier fields) are overridden.
     */
    private static ChatOptions applyModel(ChatOptions incoming, ChatOptions tierOptions) {
        return incoming.copy().merge(tierOptions);
    }

    private static String modelKey(ChatOptions options) {
        String provider = options.getProvider() != null ? options.getProvider() : "";
        String model = options.getModel() != null ? options.getModel() : "";
        return provider + ":" + model;
    }

    private static String buildReason(Complexity classified, Complexity target,
                                      boolean downgraded, boolean budgetExceededButNoCheaper) {
        if (downgraded) {
            return "complexity=" + classified.getKey()
                    + "; budget-exceeded->downgraded to " + target.getKey();
        }
        if (budgetExceededButNoCheaper) {
            return "complexity=" + classified.getKey()
                    + "; budget-exceeded (no cheaper tier configured)";
        }
        return "complexity=" + target.getKey();
    }

    // ========================================================================
    // Builder
    // ========================================================================

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<Complexity, List<ChatOptions>> tierSequences = new EnumMap<>(Complexity.class);
        private int mediumChars = DEFAULT_MEDIUM_CHARS;
        private int complexChars = DEFAULT_COMPLEX_CHARS;
        private int mediumTools = DEFAULT_MEDIUM_TOOLS;
        private int complexTools = DEFAULT_COMPLEX_TOOLS;

        /**
         * Set the primary model {@link ChatOptions} for a complexity tier
         * (provider+model and any other non-null fields applied to the routed
         * request). At least one tier must be configured.
         */
        public Builder tierModel(Complexity tier, ChatOptions options) {
            Objects.requireNonNull(tier, "tier must not be null");
            Objects.requireNonNull(options, "options must not be null");
            tierSequences.computeIfAbsent(tier, t -> new ArrayList<>());
            List<ChatOptions> seq = tierSequences.get(tier);
            if (!seq.isEmpty()) {
                // Replace an existing primary (keep any appended fallbacks).
                seq.set(0, options);
            } else {
                seq.add(options);
            }
            return this;
        }

        /**
         * Append a fallback model to a tier's fallback chain. Fallbacks are
         * consulted in append order when {@link #getFallback} is called for a
         * request routed to this tier's primary (or a prior fallback).
         */
        public Builder fallback(Complexity tier, ChatOptions options) {
            Objects.requireNonNull(tier, "tier must not be null");
            Objects.requireNonNull(options, "options must not be null");
            tierSequences.computeIfAbsent(tier, t -> new ArrayList<>()).add(options);
            return this;
        }

        public Builder mediumChars(int mediumChars) {
            this.mediumChars = mediumChars;
            return this;
        }

        public Builder complexChars(int complexChars) {
            this.complexChars = complexChars;
            return this;
        }

        public Builder mediumTools(int mediumTools) {
            this.mediumTools = mediumTools;
            return this;
        }

        public Builder complexTools(int complexTools) {
            this.complexTools = complexTools;
            return this;
        }

        public SmartModelRouter build() {
            if (tierSequences.isEmpty()) {
                throw new NopAiAgentException(
                        "SmartModelRouter requires at least one configured tier model");
            }
            for (Map.Entry<Complexity, List<ChatOptions>> e : tierSequences.entrySet()) {
                if (e.getValue() == null || e.getValue().isEmpty()) {
                    throw new NopAiAgentException(
                            "SmartModelRouter tier '" + e.getKey().getKey()
                                    + "' has an empty model sequence");
                }
            }
            if (complexChars < mediumChars) {
                throw new NopAiAgentException(
                        "SmartModelRouter complexChars must be >= mediumChars: complexChars="
                                + complexChars + ", mediumChars=" + mediumChars);
            }
            if (complexTools < mediumTools) {
                throw new NopAiAgentException(
                        "SmartModelRouter complexTools must be >= mediumTools: complexTools="
                                + complexTools + ", mediumTools=" + mediumTools);
            }
            // Defensive copies of the sequences.
            Map<Complexity, List<ChatOptions>> copy = new EnumMap<>(Complexity.class);
            for (Map.Entry<Complexity, List<ChatOptions>> e : tierSequences.entrySet()) {
                copy.put(e.getKey(), List.copyOf(e.getValue()));
            }
            return new SmartModelRouter(copy, mediumChars, complexChars, mediumTools, complexTools);
        }
    }
}
