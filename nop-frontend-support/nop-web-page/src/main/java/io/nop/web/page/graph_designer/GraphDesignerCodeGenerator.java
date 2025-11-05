/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.web.page.graph_designer;

import io.nop.commons.util.StringHelper;
import io.nop.web.page.WebPageConstants;
import io.nop.xlang.api.XLang;
import io.nop.web.page.graph_designer.model.GraphDesignerEdgeModel;
import io.nop.web.page.graph_designer.model.GraphDesignerModel;
import io.nop.web.page.graph_designer.model.GraphDesignerNodeModel;
import io.nop.xui.model.UiFormModel;

import java.util.HashMap;
import java.util.Map;

public class GraphDesignerCodeGenerator {
    private final GraphDesignerModel model;
    private final String codeGenLib;

    public GraphDesignerCodeGenerator(GraphDesignerModel model, String codeGenLib) {
        this.model = model;
        if (StringHelper.isEmpty(codeGenLib)) {
            codeGenLib = model.getCodeGenLib();
        }
        this.codeGenLib = codeGenLib;
        if (StringHelper.isEmpty(codeGenLib))
            throw new IllegalArgumentException("nop.err.empty-code-gen-lib");
    }

    /**
     * 收集所有节点和边的样式，生成css文本
     */
    public String generateCss(String designerId) {
        StringBuilder sb = new StringBuilder();
       // appendStyle(sb, model.getStyle());
        if (model.getNodes() != null) {
            for (GraphDesignerNodeModel nodeModel : model.getNodes()) {
               // String style = nodeModel.getStyle();
               // appendStyle(sb, style);
            }
        }

        if (model.getEdges() != null) {
            for (GraphDesignerEdgeModel edgeModel : model.getEdges()) {
               // String style = edgeModel.getStyle();
               // appendStyle(sb, style);
            }
        }
        String css = sb.toString();
        if (!StringHelper.isEmpty(designerId))
            css = StringHelper.replace(css, "#designer ", "#" + designerId + " ");
        return css;
    }

    private void appendStyle(StringBuilder sb, String style) {
        if (style != null) {
            if (sb.length() > 0)
                sb.append('\n');
            sb.append(style);
        }
    }

    public String generateJs() {
        Map<String, Object> vars = new HashMap<>();
        vars.put(WebPageConstants.VAR_CODE_GEN_MODEL, model);
        return XLang.getTagAction(codeGenLib, "GenerateJs").generateText(XLang.newEvalScope(vars));
    }

    /**
     * 生成整个设计器所对应的前台页面定义
     *
     * @return JSON格式的页面定义
     */
    public Map<String, Object> generateSchema() {
        Map<String, Object> vars = new HashMap<>();
        vars.put(WebPageConstants.VAR_CODE_GEN_MODEL, model);
        return (Map<String, Object>) XLang.getTagAction(codeGenLib, "GenerateSchema").generateXjson(XLang.newEvalScope(vars));
    }

    /**
     * 生成前台编辑表单所对应的页面定义
     *
     * @param form 表单模型
     * @return 页面JSON定义
     */
    public Map<String, Object> generateForm(UiFormModel form) {
        Map<String, Object> vars = new HashMap<>();
        vars.put(WebPageConstants.VAR_CODE_GEN_MODEL, model);
        vars.put(WebPageConstants.VAR_FORM_MODEL, form);
        return (Map<String, Object>) XLang.getTagAction(codeGenLib, "GenerateForm").generateXjson(XLang.newEvalScope(vars));
    }
}