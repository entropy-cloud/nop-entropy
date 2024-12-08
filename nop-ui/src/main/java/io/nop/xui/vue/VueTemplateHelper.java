/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xui.vue;

import io.nop.commons.text.IndentPrinter;
import io.nop.core.lang.xml.XNode;
import io.nop.xui.vue.react.VueNodeToReact;

public class VueTemplateHelper {
    public static String vueTemplateToReact(XNode template) {
        if (template == null)
            return "<></>";

        VueNode vueNode = new VueTemplateParser().parseTemplate(template);
        return vueNodeToReact(vueNode);
    }

    public static String vueNodeToReact(VueNode vueNode){
        if (vueNode == null)
            return "<></>";

        IndentPrinter out = new IndentPrinter(100);
        new VueNodeToReact().render(vueNode, out);
        return out.toString();
    }
}
