package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.ai.toolkit.fs.TextResult;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolOutput;
import io.nop.diff.UnifiedDiff;
import io.nop.diff.UnifiedDiffApplier;
import io.nop.diff.UnifiedDiffParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;

public class PatchFileExecutor implements IToolExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(PatchFileExecutor.class);

    public static final String TOOL_NAME = "patch-file";

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public CompletionStage<AiToolCallResult> executeAsync(AiToolCall call, IToolExecuteContext context) {
        return context.getExecutor().submit(() -> doExecute(call, context));
    }

    private AiToolCallResult doExecute(AiToolCall call, IToolExecuteContext context) {
        try {
            String path = call.attrText("path");
            String diffContent = call.childText("input");
            boolean dryRun = call.attrBoolean("dryRun", false);

            if (path == null || path.isEmpty()) {
                return AiToolCallResult.errorResult(call.getId(), "Path is required");
            }

            if (diffContent == null || diffContent.isEmpty()) {
                return AiToolCallResult.errorResult(call.getId(), "Diff content (input) is required");
            }

            IToolFileSystem fs = context.getFileSystem();

            if (!fs.exists(path)) {
                return AiToolCallResult.errorResult(call.getId(), "File does not exist: " + path);
            }

            TextResult fileResult = fs.readText(path, Integer.MAX_VALUE);
            String originalContent = fileResult.getContent();

            UnifiedDiff diff = UnifiedDiffParser.parseSingleDiff(diffContent);
            if (diff == null) {
                return AiToolCallResult.errorResult(call.getId(), "Failed to parse diff content");
            }

            UnifiedDiffApplier applier = new UnifiedDiffApplier();
            String newContent = applier.apply(originalContent, diff);

            if (dryRun) {
                AiToolCallResult result = new AiToolCallResult();
                result.setId(call.getId());
                result.setStatus("success");
                AiToolOutput output = new AiToolOutput();
                output.setBody("Dry run successful. Changes would be applied to: " + path);
                result.setOutput(output);
                return result;
            }

            fs.writeText(path, newContent, false);

            AiToolCallResult result = new AiToolCallResult();
            result.setId(call.getId());
            result.setStatus("success");
            AiToolOutput output = new AiToolOutput();
            output.setBody("Patch applied successfully to: " + path);
            result.setOutput(output);
            return result;
        } catch (Exception e) {
            LOG.debug("Patch file failed", e);
            return AiToolCallResult.errorResult(call.getId(), e);
        }
    }
}
