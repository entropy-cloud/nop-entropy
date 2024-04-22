package io.nop.task.utils;

import io.nop.core.lang.xml.XNode;
import io.nop.task.TaskConstants;

import java.util.ArrayList;
import java.util.List;

public class TaskGenHelper {
    public static List<String> getInputNames(XNode node) {
        List<XNode> inputNodes = node.childrenByTag(TaskConstants.TAG_INPUT);
        List<String> ret = new ArrayList<>();
        for (XNode child : inputNodes) {
            String name = child.attrText(TaskConstants.ATTR_NAME);
            ret.add(name);
        }
        return ret;
    }

    public static List<String> getOutputNames(XNode node) {
        List<XNode> list = node.childrenByTag(TaskConstants.TAG_OUTPUT);
        List<String> ret = new ArrayList<>();
        for (XNode child : list) {
            String name = child.attrText(TaskConstants.ATTR_NAME);
            ret.add(name);
        }
        return ret;
    }
}
