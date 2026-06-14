package io.nop.ai.agent.repair;

import io.nop.ai.agent.engine.AgentExecutionContext;
import io.nop.ai.api.chat.messages.ChatToolCall;
import io.nop.ai.toolkit.api.IToolManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Composite repairer that runs four deterministic stages in a fixed order,
 * threading the output of each stage into the next:
 * <ol>
 *   <li>Tool-name normalization (canonicalize + set-match)</li>
 *   <li>Argument structure repair (guarantee non-null Map)</li>
 *   <li>Argument value coercion (schema-aware type coercion)</li>
 *   <li>Argument cleanup (remove nulls and schema-absent args)</li>
 * </ol>
 *
 * <p>Each stage is itself an {@code IToolCallRepairer} (composable, individually
 * testable). The ChainRepairer holds an optional {@code IToolManager} reference
 * (injected at construction) so stages 3/4 can resolve tool schema. When
 * {@code IToolManager} is null, stages 3/4 degrade to schema-agnostic no-ops
 * while stage 1 still set-matches when {@code ctx.getAgentModel().getTools()}
 * is non-empty.
 *
 * <p>This is an <b>opt-in</b> capability. The Builder default remains
 * {@code NoOpToolCallRepairer.INSTANCE}.
 */
public class ChainRepairer implements IToolCallRepairer {

    private final List<IToolCallRepairer> stages;

    /**
     * Construct a ChainRepairer with the default 4-stage chain, wiring the
     * optional {@code IToolManager} into the schema-aware stages.
     *
     * @param toolManager optional tool manager for schema resolution; null
     *                    causes stages 3/4 to degrade to schema-agnostic
     *                    no-ops
     */
    public ChainRepairer(IToolManager toolManager) {
        this.stages = new ArrayList<>(4);
        this.stages.add(new ToolNameNormalizationStage());
        this.stages.add(new ArgumentStructureRepairStage());
        this.stages.add(new ArgumentValueCoercionStage(toolManager));
        this.stages.add(new ArgumentCleanupStage(toolManager));
    }

    /**
     * Construct a ChainRepairer with a custom list of stages (primarily for
     * testing). The stages are run in iteration order.
     */
    ChainRepairer(List<IToolCallRepairer> stages) {
        this.stages = new ArrayList<>(stages);
    }

    /**
     * Factory method that constructs a ChainRepairer with the default 4-stage
     * chain wired with the given {@code IToolManager}.
     */
    public static ChainRepairer withDefaults(IToolManager toolManager) {
        return new ChainRepairer(toolManager);
    }

    /**
     * Returns the list of stages in execution order (for testing/inspection).
     */
    List<IToolCallRepairer> getStages() {
        return stages;
    }

    @Override
    public ChatToolCall repair(ChatToolCall toolCall, AgentExecutionContext ctx) {
        ChatToolCall current = toolCall;
        for (IToolCallRepairer stage : stages) {
            current = stage.repair(current, ctx);
        }
        return current;
    }
}
