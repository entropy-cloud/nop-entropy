/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.ai.core.command;

import io.nop.core.lang.xml.XNode;

public class ToolCommandResult {

    public static final int STATUS_SUCCESS = 0;
    public static final int STATUS_ERROR = 1;

    private final String id;
    private final int status;
    private final String error;
    private final String output;

    public ToolCommandResult(String id, int status, String error, String output) {
        this.id = id;
        this.status = status;
        this.error = error;
        this.output = output;
    }

    public static ToolCommandResult success(String id) {
        return new ToolCommandResult(id, STATUS_SUCCESS, null, null);
    }

    public static ToolCommandResult success(String id, String output) {
        return new ToolCommandResult(id, STATUS_SUCCESS, null, output);
    }

    public static ToolCommandResult error(String id, String errorMessage) {
        return new ToolCommandResult(id, STATUS_ERROR, errorMessage, null);
    }

    public static ToolCommandResult error(String id, Exception e) {
        return new ToolCommandResult(id, STATUS_ERROR, e.getMessage(), null);
    }

    public String getId() {
        return id;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getOutput() {
        return output;
    }

    public boolean isSuccess() {
        return status == STATUS_SUCCESS;
    }

    public boolean hasId() {
        return id != null && !id.isEmpty();
    }

    public XNode toNode() {
        XNode node = XNode.make("tool-result");
        if (hasId()) {
            node.setAttr("id", id);
        }
        node.setAttr("status", String.valueOf(status));
        if (error != null) {
            node.setAttr("error", error);
        }
        if (output != null) {
            node.setContentValue(output);
        }
        return node;
    }
}
