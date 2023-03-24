package io.nop.report.demo.biz;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.beans.TreeResultBean;
import io.nop.api.core.beans.WebContentBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.Guard;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.eval.IEvalScope;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.VirtualFileSystem;
import io.nop.core.resource.tpl.ITemplateOutput;
import io.nop.core.resource.tpl.ITextTemplateOutput;
import io.nop.report.core.engine.IReportEngine;
import io.nop.xlang.api.XLang;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

    @BizQuery
    public String renderHtml(@Name("reportName") String reportName) {
        Guard.checkArgument(StringHelper.isValidVPath(reportName));
        String path = REPORT_DEMO_PATH + reportName;

        ITextTemplateOutput output = reportEngine.getHtmlRenderer(path);
        IEvalScope scope = XLang.newEvalScope();
        return output.generateText(scope);
    }

    @BizQuery
    public WebContentBean download(@Name("reportName") String reportName, @Name("renderType") String renderType) {
        Guard.checkArgument(StringHelper.isValidVPath(reportName));
        Guard.notEmpty(renderType, "renderType");

        String path = REPORT_DEMO_PATH + reportName + ".xpt.xlsx";

        ITemplateOutput output = reportEngine.getHtmlRenderer(path);
        IEvalScope scope = XLang.newEvalScope();

        IResource resource = ResourceHelper.getTempResource("demo");
        try {
            output.generateToResource(resource, scope);

            WebContentBean content = new WebContentBean("application/octet-stream", resource.toFile());

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
}
