/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.demo.biz;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.annotations.core.Optional;
import io.nop.api.core.beans.TreeResultBean;
import io.nop.api.core.beans.WebContentBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.lang.xml.XNode;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.impl.ByteArrayResource;
import io.nop.core.resource.tpl.ITemplateOutput;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.report.core.XptConstants;
import io.nop.report.core.build.XptModelLoader;
import io.nop.report.core.engine.IReportEngine;
import io.nop.xlang.api.XLang;
import io.nop.xlang.xdsl.DslModelHelper;
import jakarta.inject.Inject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.nop.report.core.XptErrors.ARG_REPORT_NAME;
import static io.nop.report.core.XptErrors.ERR_XPT_UNKNOWN_REPORT_MODEL;

@BizModel("ReportDemo")
public class ReportDemoBizModel {
    private final String REPORT_DEMO_PATH = "/nop/report/demo";

    @Inject
    IReportEngine reportEngine;

    @BizQuery
    public List<TreeResultBean> getDemoReports() {
        List<? extends IResource> groups = VirtualFileSystem.instance().getChildren(REPORT_DEMO_PATH);
        List<TreeResultBean> nodes = new ArrayList<>();
        for (IResource resource : groups) {
            if (resource.getName().equals("ext"))
                continue;
            List<TreeResultBean> reports = getReportBeans(resource);
            if (!reports.isEmpty()) {
                TreeResultBean bean = new TreeResultBean();
                bean.setLabel(resource.getName());
                bean.setValue(null);
                bean.setChildren(reports);
                nodes.add(bean);
            }
        }
        return nodes;
    }

    List<TreeResultBean> getReportBeans(IResource resource) {
        List<? extends IResource> children = VirtualFileSystem.instance().getChildren(resource.getStdPath());
        if (children.isEmpty())
            return Collections.emptyList();

        List<TreeResultBean> ret = new ArrayList<>();
        for (IResource child : children) {
            if (!child.getName().startsWith("~") && child.getName().endsWith(".xpt.xlsx")) {
                TreeResultBean bean = new TreeResultBean();
                String rptName = StringHelper.removeTail(child.getName(), ".xpt.xlsx");
                bean.setLabel(rptName);
                bean.setValue(StringHelper.removeHead(child.getPath(), REPORT_DEMO_PATH));
                ret.add(bean);
            }
        }
        return ret;
    }

    /**
     * 没有@BizQuery和@BizMutation注解的函数不会被发布为服务函数
     */
    public String renderHtml(String reportName) {
        return renderHtml(reportName, null);
    }

    @BizQuery
    public String renderHtml(@Name("reportName") String reportName, @Optional @Name("data") Map<String, Object> data) {
        Guard.checkArgument(StringHelper.isValidVPath(reportName));
        String path = REPORT_DEMO_PATH + reportName;

        ITextTemplateOutput output = reportEngine.getHtmlRenderer(path);
        IEvalScope scope = XLang.newEvalScope();
        // 通过scope可以给报表传参数
        if (data != null)
            scope.setLocalValues(data);
        String text = output.generateText(scope);

        return text;
    }

    public String renderWithXmlModel(String reportName, Map<String, Object> data) {

        String path = REPORT_DEMO_PATH + reportName;

        IEvalScope scope = XLang.newEvalScope();
        // 通过scope可以给报表传参数
        if (data != null)
            scope.setLocalValues(data);

        ExcelWorkbook xptModel = reportEngine.getXptModel(path);
        XNode node = DslModelHelper.dslModelToXNode(XptConstants.XDSL_SCHEMA_WORKBOOK, xptModel);

        path = StringHelper.removeTail(path, ".xlsx") + ".xml";
        node.dump();
        IResource resource = new ByteArrayResource(path, node.xml().getBytes(StandardCharsets.UTF_8), -1L);
        xptModel = XptModelLoader.instance().loadModelFromResource(resource);
        ITextTemplateOutput output = (ITextTemplateOutput) reportEngine.getRendererForXptModel(xptModel, XptConstants.RENDER_TYPE_HTML);
        return output.generateText(scope);
    }

    @BizQuery
    public WebContentBean download(@Name("reportName") String reportName, @Name("renderType") String renderType) {
        Guard.checkArgument(StringHelper.isValidVPath(reportName));
        Guard.notEmpty(renderType, "renderType");

        String path = REPORT_DEMO_PATH + reportName;

        ITemplateOutput output = reportEngine.getRenderer(path, renderType);
        IEvalScope scope = XLang.newEvalScope();

        IResource resource = ResourceHelper.getTempResource("demo");
        try {
            output.generateToResource(resource, scope);

            String fileName = (String) scope.getLocalValue("exportFileName");
            if (StringHelper.isEmpty(fileName)) {
                fileName = StringHelper.removeTail(StringHelper.fileNameNoExt(reportName), ".xpt");
            }
            fileName = StringHelper.removeFileExt(fileName) + "." + renderType;

            WebContentBean content = new WebContentBean("application/octet-stream",
                    resource.toFile(), fileName);

            GlobalExecutors.globalTimer().schedule(() -> {
                resource.delete();
                return null;
            }, 5, TimeUnit.MINUTES);

            return content;
        } catch (Exception e) {
            resource.delete();
            throw NopException.adapt(e);
        }
    }

    @BizQuery
    public WebContentBean downloadModel(@Name("reportName") String reportName) {
        Guard.checkArgument(StringHelper.isValidVPath(reportName));
        String path = REPORT_DEMO_PATH + reportName;

        IResource resource = VirtualFileSystem.instance().getResource(path);
        if (!resource.exists())
            throw new NopException(ERR_XPT_UNKNOWN_REPORT_MODEL).param(ARG_REPORT_NAME, reportName);
        WebContentBean content = new WebContentBean("application/octet-stream", resource,
                StringHelper.fileFullName(reportName));
        return content;
    }
}
