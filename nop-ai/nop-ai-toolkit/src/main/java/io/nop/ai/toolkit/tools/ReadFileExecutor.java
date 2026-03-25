package io.nop.ai.toolkit.tools;

import io.nop.ai.toolkit.api.IToolExecuteContext;
import io.nop.ai.toolkit.api.IToolExecutor;
import io.nop.ai.toolkit.fs.IToolFileSystem;
import io.nop.ai.toolkit.fs.TextResult;
import io.nop.ai.toolkit.fs.LineResult;
import io.nop.ai.toolkit.model.AiToolCall;
import io.nop.ai.toolkit.model.AiToolCallResult;
import io.nop.ai.toolkit.model.AiToolOutput;
import java.util.concurrent.CompletionStage;

public class ReadFileExecutor implements IToolExecutor {
    public static final String TOOL_NAME = "read-file";
    private static final int DEFAULT_MAX_CHARS = 100000;

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

            Integer fromLine = call.attrInt("fromLine");
            Integer toLine = call.attrInt("toLine");
            Integer lastLines = call.attrInt("lastLines");

            String content;
            int totalLines = 0;
            int actualFromLine = 0;
            int actualToLine = 0;

            if (lastLines != null && lastLines > 0) {
                totalLines = fs.countLines(path, Integer.MAX_VALUE);
                actualFromLine = Math.max(1, totalLines - lastLines + 1);
                actualToLine = totalLines;
                LineResult lineResult = fs.readLines(path, actualFromLine, actualToLine, 10000);
                content = lineResult.toLineNumberedContent();
            } else if (fromLine != null && toLine != null && fromLine > 0 && toLine > 0) {
                actualFromLine = fromLine;
                actualToLine = toLine;
                LineResult lineResult = fs.readLines(path, fromLine, toLine, 10000);
                totalLines = lineResult.getTotalLines();
                content = lineResult.toLineNumberedContent();
            } else {
                TextResult textResult = fs.readText(path, DEFAULT_MAX_CHARS);
                content = textResult.getContent();
                totalLines = content.split("\n", -1).length;
                actualFromLine = 1;
                actualToLine = totalLines;
            }

            AiToolCallResult result = AiToolCallResult.successResult(call.getId(), content);
            AiToolOutput output = result.getOutput();
            if (output != null) {
                output.setPath(path);
                output.setTotalLines(totalLines);
                output.setFromLine(actualFromLine);
                output.setToLine(actualToLine);
            }
            return result;
        } catch (Exception e) {
            return AiToolCallResult.errorResult(call.getId(), e.getMessage());
        }
    }
}
