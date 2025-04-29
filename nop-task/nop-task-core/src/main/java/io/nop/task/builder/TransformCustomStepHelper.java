package io.nop.task.builder;

import io.nop.api.core.annotations.lang.EvalMethod;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.utils.XNodeHelper;
import io.nop.core.lang.xml.XNode;
import io.nop.task.TaskConstants;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.xdsl.action.BizActionGenHelper;
import io.nop.xlang.xpl.IXplTag;
import io.nop.xlang.xpl.XplConstants;
import io.nop.xlang.xpl.xlib.XplLibHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.nop.task.TaskErrors.ARG_CUSTOM_TYPE;
import static io.nop.task.TaskErrors.ERR_TASK_INVALID_CUSTOM_TYPE;

public class TransformCustomStepHelper {

    @EvalMethod
    public static Object transformCustomStep(IEvalScope scope, XNode root, XNode node) {
        String customType = node.attrText(TaskConstants.ATTR_CUSTOM_TYPE);
        if (StringHelper.isEmpty(customType) || customType.indexOf(':') < 0 || !StringHelper.isValidXmlName(customType)) {
            throw new NopException(ERR_TASK_INVALID_CUSTOM_TYPE)
                    .source(node).param(ARG_CUSTOM_TYPE, customType);
        }

        IXplTag tag = getGeneratorTag(root, node, customType);
        if (tag != null) {
            Map<String, Object> args = new HashMap<>();
            args.put(TaskConstants.VAR_NODE, node);
            args.put(XLangConstants.SCOPE_VAR_DSL_ROOT, root);
            XNode ret = tag.generateNode(scope, args);
            if (ret.isDummyNode())
                return ret.getChildren();
            return ret;
        } else {
            return transformCustomStepToXpl(root, node, customType);
        }
    }

    static IXplTag getGeneratorTag(XNode root, XNode node, String customType) {
        String ns = StringHelper.getNamespace(customType);
        String path = node.getUrlForXmlns(ns);
        if (path == null)
            path = root.getUrlForXmlns(ns);
        if (StringHelper.isEmpty(path) || !path.endsWith(XplConstants.POSTFIX_XLIB))
            return null;
        String tagName = customType.substring(ns.length() + 1);
        return XplLibHelper.getTag(path, tagName + "-generator", true);
    }

    /**
     * 准换custom节点内容为xpl节点。
     *
     * <pre>
     *     <custom name="test" customType="gpt:simple" gpt:temperature="3">
     *       <input name="a" />
     *       <input name="b" />
     *
     *      <gpt:prompt> xxx  </gpt:prompt>
     *
     *    </custom>
     *
     *    转换为
     *
     *    <xpt name="test">
     *      <input name="a" />
     *      <input name="b" />
     *
     *      <source>
     *          <gpt:simple temperature="3" a="${a}" b="${b}”>
     *              <prompt>xxx</prompt>
     *          </gpt:simple>
     *      </source>
     *  </step>
     * </pre>
     *
     * @param node 待转换的节点， 不会修改此节点的内容
     * @return 转换后的节点
     */

    public static XNode transformCustomStepToXpl(XNode root, XNode node) {
        String customType = node.attrText(TaskConstants.ATTR_CUSTOM_TYPE);
        if (StringHelper.isEmpty(customType) || customType.indexOf(':') < 0 || !StringHelper.isValidXmlName(customType)) {
            throw new NopException(ERR_TASK_INVALID_CUSTOM_TYPE)
                    .source(node).param(ARG_CUSTOM_TYPE, customType);
        }
        return transformCustomStepToXpl(root, node, customType);
    }

    private static XNode transformCustomStepToXpl(XNode root, XNode node, String customType) {
        XNode ret = node.cloneInstance();
        ret.setTagName(TaskConstants.STEP_TYPE_XPL);
        ret.removeAttr(TaskConstants.ATTR_CUSTOM_TYPE);

        ret.removeJsonPrefix();

        String ns = customType.substring(0, customType.indexOf(':'));

        XNode source = ret.makeChild(TaskConstants.TAG_SOURCE);
        XNode bodyNode = source.makeChild(customType);
        String path = node.getUrlForXmlns(ns);
        if (path == null)
            path = root.getUrlForXmlns(ns);
        if (!StringHelper.isEmpty(path) && path.endsWith(XplConstants.POSTFIX_XLIB)) {
            bodyNode.setAttr(XplConstants.ATTR_XPL_LIB, path);
        }
        List<String> names = BizActionGenHelper.getInputNames(ret);
        for (String name : names) {
            bodyNode.setAttr(name, "${" + name + "}");
        }

        XNodeHelper.moveAttrWithNs(ret, bodyNode, ns, true);

        XNodeHelper.moveChildWithNs(ret, bodyNode, ns, true);

        return ret;
    }

}
