package io.nop.ai.toolkit.model;

import io.nop.ai.toolkit.model._gen._AiToolCall;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.xml.XNode;

public class AiToolCall extends _AiToolCall {
    private XNode node;

    public AiToolCall() {
    }

    public static AiToolCall fromNode(XNode node) {
        if (node == null)
            return null;

        AiToolCall call = new AiToolCall();
        call.node = node;

        String tagName = node.getTagName();
        if (!StringHelper.isEmpty(tagName)) {
            call.setToolName(tagName);
        }

        call.setId(node.attrInt("id", 0));
        call.setExplanation(node.attrText("explanation"));
        call.setTimeoutMs(node.attrInt("timeoutMs", null));

        XNode inputNode = node.childByTag("input");
        if (inputNode != null) {
            call.setInput(inputNode.contentText());
        }

        return call;
    }

    public XNode getNode() {
        return node;
    }

    public void setNode(XNode node) {
        checkAllowChange();
        this.node = node;
    }

    public String attrText(String name) {
        if (node == null)
            return null;
        return node.attrText(name);
    }

    public String attrText(String name, String defaultValue) {
        String value = attrText(name);
        return value != null ? value : defaultValue;
    }

    public Integer attrInt(String name) {
        if (node == null)
            return null;
        return node.attrInt(name, null);
    }

    public int attrInt(String name, int defaultValue) {
        Integer value = attrInt(name);
        return value != null ? value : defaultValue;
    }

    public Long attrLong(String name) {
        if (node == null)
            return null;
        return node.attrLong(name, null);
    }

    public long attrLong(String name, long defaultValue) {
        Long value = attrLong(name);
        return value != null ? value : defaultValue;
    }

    public Boolean attrBoolean(String name) {
        if (node == null)
            return null;
        return node.attrBoolean(name, null);
    }

    public boolean attrBoolean(String name, boolean defaultValue) {
        Boolean value = attrBoolean(name);
        return value != null ? value : defaultValue;
    }

    public Double attrDouble(String name) {
        if (node == null)
            return null;
        return node.attrDouble(name, null);
    }

    public double attrDouble(String name, double defaultValue) {
        Double value = attrDouble(name);
        return value != null ? value : defaultValue;
    }

    public String childText(String tagName) {
        if (node == null)
            return null;
        XNode child = node.childByTag(tagName);
        if (child == null)
            return null;
        return child.contentText();
    }

    public String childText(String tagName, String defaultValue) {
        String value = childText(tagName);
        return value != null ? value : defaultValue;
    }

    public XNode childNode(String tagName) {
        if (node == null)
            return null;
        return node.childByTag(tagName);
    }
}
