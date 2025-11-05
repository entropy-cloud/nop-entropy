/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xui.vue;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.commons.util.objects.Pair;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.xml.XNode;
import io.nop.xlang.ast.Expression;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.nop.xui.vue.VueErrors.ARG_SLOT_NAME;
import static io.nop.xui.vue.VueErrors.ARG_TAG_NAME;
import static io.nop.xui.vue.VueErrors.ERR_VUE_SLOT_NOT_ALLOW_SLOT_CHILD;
import static io.nop.xui.vue.VueErrors.ERR_VUE_TEMPLATE_NO_SLOT_NAME;
import static io.nop.xui.vue.VueErrors.ERR_VUE_V_CHILD_NOT_ALLOW_ATTR;
import static io.nop.xui.vue.VueErrors.ERR_VUE_V_CHILD_NOT_ALLOW_SLOT;

/**
 * 解析简化的Vue模板
 */
public class VueTemplateParser {
    public static VueTemplateParser INSTANCE = new VueTemplateParser();

    public VueNode parseTemplate(XNode node) {
        VueNode ret = new VueNode();
        ret.setLocation(node.getLocation());
        ret.setType(VueConstants.TAG_TEMPLATE);
        if (node.hasContent()) {
            ret.setContentExpr(parseContent(node));
        } else if (node.hasChild()) {
            ret.setChildren(parseChildren(node, ret));
        }
        return ret;
    }

    private Expression parseContent(XNode node) {
        ValueWithLocation vl = node.content();
        return parseTemplateExpr(vl);
    }

    private Expression parseTemplateExpr(ValueWithLocation vl) {
        if (vl.isEmpty())
            return null;
        return new VueExpressionParser().parseTemplateExpr(vl.getLocation(), vl.asString());
    }

    private Expression parseExpr(ValueWithLocation vl) {
        if (vl.isEmpty())
            return null;
        return new VueExpressionParser().parseExpr(vl.getLocation(), vl.asString());
    }

    private List<VueNode> parseChildren(XNode node, VueNode vueNode) {
        List<VueNode> children = new ArrayList<>();
        for (XNode child : node.getChildren()) {
            if (child.getTagName().startsWith(VueConstants.V_PREFIX) || child.getTagName().startsWith(VueConstants.V_BIND_PREFIX)) {
                if (!child.hasChild())
                    continue;

                if (child.hasAttr())
                    throw new NopException(ERR_VUE_V_CHILD_NOT_ALLOW_ATTR)
                            .source(node).param(ARG_TAG_NAME, child.getTagName());

                VueNode attrNode = parseVueNode(child);
                if (attrNode.hasSlot())
                    throw new NopException(ERR_VUE_V_CHILD_NOT_ALLOW_SLOT)
                            .source(node).param(ARG_TAG_NAME, child.getTagName());

                String prefix = child.getTagName().startsWith(VueConstants.V_PREFIX) ?
                        VueConstants.V_PREFIX : VueConstants.V_BIND_PREFIX;
                String attrName = child.getTagName().substring(prefix.length());
                vueNode.addProp(attrName, attrNode.getBodyNode());
            } else {
                children.add(parseVueNode(child));
            }
        }
        return children;
    }

    private VueNode parseVueNode(XNode node) {
        VueNode ret = new VueNode();
        ret.setLocation(node.getLocation());
        ret.setType(node.getTagName());

        node.forEachAttr((name, vl) -> {
            if (name.equals(VueConstants.PROP_REF)) {
                ret.setRef(vl.asString());
            } else if (name.equals(VueConstants.V_IF)) {
                Expression expr = parseExpr(vl);
                ret.setIfExpr(expr);
            } else if (name.equals(VueConstants.V_FOR)) {
                parseFor(vl, ret);
            } else if (name.equals(VueConstants.V_HTML)) {
                Expression expr = parseExpr(vl);
                ret.setHtmlExpr(expr);
            } else if (name.equals(VueConstants.V_BIND_KEY) || name.equals(VueConstants.V_KEY)) {
                Expression expr = parseExpr(vl);
                ret.setKeyExpr(expr);
            } else if (name.equals(VueConstants.V_BIND_IS) || name.equals(VueConstants.V_IS)) {
                Expression expr = parseExpr(vl);
                ret.setIsExpr(expr);
            } else if (name.startsWith(VueConstants.V_BIND_PREFIX)) {
                String key = name.substring(VueConstants.V_BIND_PREFIX.length());
                Expression expr = parseExpr(vl);
                ret.addProp(key, expr);
            } else if (name.startsWith(VueConstants.V_PREFIX)) {
                // 扩展语法 v:value 等价于v-bind:value
                String key = name.substring(VueConstants.V_PREFIX.length());
                Expression expr = parseExpr(vl);
                ret.addProp(key, expr);
            } else if (name.startsWith(VueConstants.V_ON_PREFIX)) {
                String event = name.substring(VueConstants.V_ON_PREFIX.length());
                Expression expr = parseExpr(vl);
                ret.addEventListener(event, expr);
            } else {
                ret.addProp(name, vl.asString());
            }
        });

        if (node.hasContent()) {
            ret.setContentExpr(parseContent(node));
        } else if (node.hasChild()) {
            List<VueNode> children = parseChildren(node, ret);
            Map<String, VueSlot> slots = null;
            Iterator<VueNode> it = children.iterator();
            while (it.hasNext()) {
                VueNode child = it.next();
                if (child.getType().equals(VueConstants.TAG_TEMPLATE)) {
                    it.remove();

                    Pair<String, String> slotArg = parseSlotArg(child);
                    if (slotArg == null)
                        throw new NopException(ERR_VUE_TEMPLATE_NO_SLOT_NAME)
                                .loc(child.getLocation());

                    if (child.getSlots() != null)
                        throw new NopException(ERR_VUE_SLOT_NOT_ALLOW_SLOT_CHILD)
                                .loc(child.getLocation()).param(ARG_SLOT_NAME, slotArg.getKey());

                    if (slots == null)
                        slots = new LinkedHashMap<>();

                    VueSlot slot = new VueSlot();
                    slot.setLocation(child.getLocation());
                    slot.setSlotName(slotArg.getKey());
                    slot.setSlotVar(slotArg.getValue());
                    slot.setContent(child.getContentExpr());
                    slot.setChildren(child.getChildren());
                    slots.put(slot.getSlotName(), slot);
                }
            }

            ret.setSlots(slots);

            if (!children.isEmpty()) {
                ret.setChildren(children);
            }
        }
        return ret;
    }

    private void parseFor(ValueWithLocation vl, VueNode node) {
        TextScanner sc = TextScanner.fromString(vl.getLocation(), vl.asString());
        sc.skipBlank();
        if (sc.tryMatch('(')) {
            String varName = sc.nextJavaVar();
            sc.skipBlank();
            node.setItemVarName(varName);
            if (sc.tryMatch(',')) {
                String indexName = sc.nextJavaVar();
                node.setIndexVarName(indexName);
                sc.skipBlank();
            }
            sc.match(')');
        } else {
            String varName = sc.nextJavaVar();
            sc.skipBlank();
            node.setItemVarName(varName);
        }
        sc.match("in");
        Expression expr = new VueExpressionParser().parseExpr(sc);
        sc.checkEnd();
        node.setItemsExpr(expr);
    }

    private Pair<String, String> parseSlotArg(VueNode node) {
        if (node.getProps() == null)
            return null;

        for (Map.Entry<String, Object> entry : node.getProps().entrySet()) {
            String name = entry.getKey();
            if (name.startsWith(VueConstants.V_SLOT_PREFIX)) {
                String slotName = name.substring(VueConstants.V_SLOT_PREFIX.length());
                String slotVar = (String) entry.getValue();
                return Pair.of(slotName, slotVar);
            }
        }
        return null;
    }


}