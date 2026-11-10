package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.xlang.delta.DeltaMerger;
import io.nop.xlang.xdsl.XDslKeys;
import java.util.concurrent.CompletionStage;

public class ApplyDeltaExecutor implements IToolExecutor {
    public static final String TOOL_NAME = "apply-delta";

    /**
     * 3.2-C8-008（plan 2557 裁定 H）：既有 10M 读回帽提为命名常量并补回归钉
     * （TestFileToolOutputClamps）——帽面不回退。
     */
    public static final int MAX_READ_CHARS = 10_000_000;

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
            IToolFileSystem fs = context.getFileSystem();
            if (fs == null) {
                return AiToolCallResult.errorResult(call.getId(), "File system not available");
            }

            String path = call.attrText("path", "");
            if (path.isEmpty()) {
                return AiToolCallResult.errorResult(call.getId(), "Path is required");
            }

            String deltaPath = call.attrText("deltaPath");
            XNode deltaContentNode = call.childNode("deltaContent");
            boolean dryRun = call.attrBoolean("dryRun", false);

            if (!fs.exists(path)) {
                return AiToolCallResult.errorResult(call.getId(), "Target file does not exist: " + path);
            }

            XNode deltaNode;
            if (deltaContentNode != null && deltaContentNode.hasChild()) {
                deltaNode = deltaContentNode.child(0);
            } else if (deltaPath != null && !deltaPath.isEmpty()) {
                String deltaXml = fs.readText(deltaPath, 1000000).getContent();
                deltaNode = XNodeParser.instance().parseFromText(null, deltaXml);
            } else {
                return AiToolCallResult.errorResult(call.getId(), "Either deltaContent or deltaPath is required");
            }

            String baseContent = fs.readText(path, MAX_READ_CHARS).getContent();
            XNode baseNode = XNodeParser.instance().parseFromText(null, baseContent);

            DeltaMerger merger = new DeltaMerger(XDslKeys.DEFAULT);
            merger.merge(baseNode, deltaNode, null, false);

            String mergedContent = baseNode.xml();

            if (dryRun) {
                return AiToolCallResult.successResult(call.getId(), mergedContent);
            } else {
                fs.writeText(path, mergedContent, false);
                return AiToolCallResult.successResult(call.getId(), 
                    "Delta applied successfully. File updated: " + path);
            }
        } catch (Exception e) {
            return AiToolCallResult.errorResult(call.getId(), String.valueOf(e));
        }
    }
}
