package io.nop.ai.toolkit.model;

import io.nop.ai.toolkit.model._gen._AiToolCallResult;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.exceptions.IException;
import io.nop.api.core.exceptions.NopException;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.core.lang.xml.XNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AiToolCallResult extends _AiToolCallResult {
    private static final Logger LOG = LoggerFactory.getLogger(AiToolCallResult.class);

    private XNode node;

    public AiToolCallResult() {
    }

    public static AiToolCallResult fromNode(XNode node) {
        if (node == null)
            return null;

        AiToolCallResult result = new AiToolCallResult();
        result.setNode(node);

        result.setId(node.attrInt("id", 0));
        result.setStatus(node.attrText("status", "success"));
        result.setExitCode(node.attrInt("exitCode", null));

        XNode outputNode = node.childByTag("output");
        if (outputNode != null) {
            AiToolOutput output = new AiToolOutput();
            output.setBody(outputNode.contentText());
            output.setPath(outputNode.attrText("path"));
            output.setFromLine(outputNode.attrInt("fromLine", null));
            output.setToLine(outputNode.attrInt("toLine", null));
            output.setTotalLines(outputNode.attrInt("totalLines", null));
            result.setOutput(output);
        }

        XNode errorNode = node.childByTag("error");
        if (errorNode != null) {
            AiToolError error = new AiToolError();
            error.setBody(errorNode.contentText());
            error.setPath(errorNode.attrText("path"));
            result.setError(error);
        }

        return result;
    }

    public XNode getNode() {
        return node;
    }

    public void setNode(XNode node) {
        checkAllowChange();
        this.node = node;
    }

    public static AiToolCallResult successResult(int id, String output) {
        AiToolCallResult result = new AiToolCallResult();
        result.setId(id);
        result.setStatus("success");

        AiToolOutput out = new AiToolOutput();
        out.setBody(output);
        result.setOutput(out);

        return result;
    }

    public static AiToolCallResult successResult(int id, String output, String path) {
        AiToolCallResult result = successResult(id, output);
        if (result.getOutput() != null) {
            result.getOutput().setPath(path);
        }
        return result;
    }

    public static AiToolCallResult errorResult(int id, String errorMessage) {
        AiToolCallResult result = new AiToolCallResult();
        result.setId(id);
        result.setStatus("failure");

        AiToolError error = new AiToolError();
        error.setBody(errorMessage);
        result.setError(error);

        return result;
    }

    public static AiToolCallResult errorResult(int id, String errorMessage, int exitCode) {
        AiToolCallResult result = errorResult(id, errorMessage);
        result.setExitCode(exitCode);
        return result;
    }

    public static AiToolCallResult errorResult(int id, Exception e) {
        LOG.error("Tool execution failed", e);

        AiToolCallResult result = new AiToolCallResult();
        result.setId(id);
        result.setStatus("failure");

        AiToolError error = new AiToolError();
        error.setBody(buildErrorMessage(e));
        result.setError(error);

        return result;
    }

    private static String buildErrorMessage(Exception e) {
        ErrorBean errorBean = ErrorMessageManager.instance().buildErrorMessage(
                null, e, true, false, false);

        StringBuilder sb = new StringBuilder();
        sb.append("Error: ").append(errorBean.getErrorCode());

        if (errorBean.getDescription() != null) {
            sb.append("\nMessage: ").append(errorBean.getDescription());
        }

        if (errorBean.getParams() != null && !errorBean.getParams().isEmpty()) {
            sb.append("\nParams: ").append(errorBean.getParams());
        }

        if (errorBean.getSourceLocation() != null) {
            sb.append("\nLocation: ").append(errorBean.getSourceLocation());
        }

        if (errorBean.getErrorStack() != null) {
            sb.append("\nStack:\n").append(errorBean.getErrorStack());
        }

        return sb.toString();
    }
}
