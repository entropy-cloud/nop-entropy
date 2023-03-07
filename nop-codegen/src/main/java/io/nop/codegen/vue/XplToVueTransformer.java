/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.codegen.vue;

import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.ValueWithLocation;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.json.CompactXNodeToJsonTransformer;

import static io.nop.codegen.CodeGenConstants.ATTR_EXPR_PREFIX;
import static io.nop.codegen.CodeGenConstants.ATTR_SCOPE;
import static io.nop.codegen.CodeGenConstants.NS_ATTR;
import static io.nop.codegen.CodeGenConstants.NS_ON;
import static io.nop.codegen.CodeGenConstants.NS_SLOT;
import static io.nop.codegen.CodeGenConstants.NS_V_BIND;
import static io.nop.codegen.CodeGenConstants.NS_V_ON;
import static io.nop.codegen.CodeGenConstants.NS_V_SLOT;
import static io.nop.codegen.CodeGenConstants.TAG_TEMPLATE;
import static io.nop.core.CoreConstants.ATTR_J_LIST;

/**
 * xpl使用规范的xml格式和前缀语法，它和vue template语法之间存在一一对应关系。具体语法对应如下 1. on:xx="handleXX" 对应于 v-on:xx="handleXX" 2. xx="@: yy" 对应于
 * v-bind:xx="yy" 3. <slot:mySlot scope="xxx"> 对应于 <template v-slot:mySlot="xxx"> 4. <attr:myAttr> 转换为json对象，然后作为父节点的属性
 * <p>
 * 转换为json属性时采用如下规则： 1. 如果属性文本符合整数或者boolean值格式，则按照对应格式解析。 2. 如果以@: 为前缀，则被认为是脚本值。 例如
 * <node a="1" b="true" c="xx" d="@: x+3" /> 对应于 node: { a: 1, b:true, c: "xx", d: x+3 } 3.
 * 节点如果没有属性，只有content，则标签名作为key, content作为值。例如 <description>xxx</description>对应于 description: "xxx" 4.
 * 如果节点标记了j:type="list"，则该节点不应具有属性，它对应于数组值。 数组元素的tagName为_，表示忽略该属性，否则tagName对应type属性。
 * <body j:type="list"><a id="xx" /><_ name="v" /></body> 对应于 body: [{type:"a",id:"xx"}, {name:"v"} ]
 */
public class XplToVueTransformer {

    private final CompactXNodeToJsonTransformer toJson = new CompactXNodeToJsonTransformer() {

    };

    public XNode transformNode(XNode node) {
        XNode ret = XNode.make(node.getTagName());
        ret.setLocation(node.getLocation());
        transformAttrs(node, ret);
        transformBody(node, ret);
        return ret;
    }

    private void transformAttrs(XNode source, XNode target) {
        source.forEachAttr((name, vl) -> {
            if (!isIgnoredAttr(name)) {
                if (StringHelper.startsWithNamespace(name, NS_ON)) {
                    String key = NS_V_ON + ':' + name.substring(NS_ON.length() + 1);
                    target.setAttr(key, vl);
                } else {
                    String value = vl.asString();
                    if (value != null && value.startsWith(ATTR_EXPR_PREFIX)) {
                        value = value.substring(ATTR_EXPR_PREFIX.length()).trim();
                        target.attrValueLoc(NS_V_BIND + ':' + name, ValueWithLocation.of(vl.getLocation(), value));
                    } else {
                        target.attrValueLoc(name, vl);
                    }
                }
            }
        });
    }

    private boolean isIgnoredAttr(String attrName) {
        return attrName.equals(ATTR_J_LIST);
    }

    private void transformBody(XNode source, XNode target) {
        if (source.hasContent()) {
            target.content(source.content());
        } else {
            for (XNode child : source.getChildren()) {
                String childName = child.getTagName();
                if (StringHelper.startsWithNamespace(childName, NS_ATTR)) {
                    String text = getAttrNodeText(child);
                    String name = childName.substring(NS_ATTR.length() + 1);
                    target.setAttr(child.getLocation(), NS_V_BIND + ':' + name, text);
                } else if (StringHelper.startsWithNamespace(childName, NS_SLOT)) {
                    String slotName = childName.substring(NS_SLOT.length() + 1);
                    String scope = child.attrText(ATTR_SCOPE);
                    if (scope == null)
                        scope = "";
                    XNode template = XNode.make(TAG_TEMPLATE);
                    String attrName = NS_V_SLOT + ':' + slotName;
                    template.setAttr(child.attrLoc(ATTR_SCOPE), attrName, scope);
                    target.appendChild(template);

                    transformSlotBody(child, template);
                } else {
                    XNode newChild = transformNode(child);
                    target.appendChild(newChild);
                }
            }
        }
    }

    private void transformSlotBody(XNode source, XNode target) {
        if (source.hasContent()) {
            target.content(source.content());
        } else {
            for (XNode child : source.getChildren()) {
                target.appendChild(transformNode(child));
            }
        }
    }

    private String getAttrNodeText(XNode node) {
        Object value = toJson.transformToObject(node);
        return JsonTool.stringify(value);
    }
}