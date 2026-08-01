package io.nop.ai.core.prompt;

import io.nop.ai.core.api.chat.AiChatOptions;
import io.nop.ai.core.api.messages.AiChatExchange;
import io.nop.ai.core.model.PromptInputModel;
import io.nop.ai.core.model.PromptOutputModel;
import io.nop.core.context.IEvalContext;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.xlang.xdsl.action.IActionModel;

import java.util.List;
import java.util.Map;

public interface IPromptTemplate extends IActionModel {
    /**
     * @return the human-readable name of the template, used for display and lookup
     */
    String getDisplayName();

    /**
     * @return a description of what the template generates (may be empty)
     */
    String getDescription();

    /**
     * @return the declared input models of the template, or an empty list if none are declared
     */
    List<PromptInputModel> getInputs();

    /**
     * @param name the input name
     * @return the declared input model with the given name, or null if not declared
     */
    PromptInputModel getInput(String name);

    /**
     * @return the declared output models of the template, or an empty list if none are declared
     */
    List<PromptOutputModel> getOutputs();

    /**
     * @param name the output name
     * @return the declared output model with the given name, or null if not declared
     */
    PromptOutputModel getOutput(String name);

    /**
     * Applies the template's declared chat settings to the given legacy chat options,
     * filling in only the options that are not already set.
     *
     * @param chatOptions the legacy chat options to configure
     */
    void applyChatOptions(AiChatOptions chatOptions);

    /**
     * Prepares an evaluation scope with the given variables bound (no context).
     *
     * @param vars the template variables
     * @return a new eval scope containing {@code vars}
     */
    default IEvalScope prepareInputs(Map<String, Object> vars) {
        return prepareInputs(vars, null);
    }

    /**
     * Prepares an evaluation scope for template rendering, binding the given variables.
     *
     * @param vars the template variables
     * @param ctx  the evaluation context; when non-null a child scope of the context scope is created
     * @return the prepared eval scope
     */
    IEvalScope prepareInputs(Map<String, Object> vars, IEvalContext ctx);

    /**
     * Renders the prompt text from the given evaluation scope.
     *
     * @param scope the eval scope prepared via {@link #prepareInputs(Map, IEvalContext)}
     * @return the rendered prompt text
     */
    String generatePrompt(IEvalScope scope);

    /**
     * Processes a chat response, parsing the template's declared outputs into the response
     * and running any post-processing function.
     *
     * @param chatResponse the legacy chat response to process
     * @param scope        the eval scope used for parsing
     */
    void processChatResponse(AiChatExchange chatResponse, IEvalScope scope);
}