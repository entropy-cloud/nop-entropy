package io.nop.report.demo.biz;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.beans.TreeResultBean;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@BizModel("ReportDemo")
public class ReportDemoBizModel {
    private final String REPORT_DEMO_PATH = "/nop/auth/demo";

    @BizQuery
    public TreeResultBean getDemoReports() {
        TreeResultBean ret = new TreeResultBean();
        ret.setLabel("报表示例");

        List<? extends IResource> groups = VirtualFileSystem.instance().getChildren(REPORT_DEMO_PATH);
        List<TreeResultBean> nodes = new ArrayList<>();
        for (IResource resource : groups) {
            List<TreeResultBean> reports = getReportBeans(resource);
            if (!reports.isEmpty()) {
                TreeResultBean bean = new TreeResultBean();
                bean.setLabel(resource.getName());
                bean.setValue(resource.getName());
                bean.setChildren(reports);
                nodes.add(bean);
            }
        }
        ret.setChildren(nodes);
        return ret;
    }

    List<TreeResultBean> getReportBeans(IResource resource) {
        List<? extends IResource> children = VirtualFileSystem.instance().getChildren(resource.getStdPath());
        if (children.isEmpty())
            return Collections.emptyList();

        List<TreeResultBean> ret = new ArrayList<>();
        for (IResource child : children) {
            if (child.getName().endsWith(".xpt.xlsx")) {
                TreeResultBean bean = new TreeResultBean();
                String rptName = StringHelper.removeTail(child.getName(), ".xpt.xlsx");
                bean.setLabel(rptName);
                bean.setValue(StringHelper.removeHead(resource.getPath(), REPORT_DEMO_PATH));
                ret.add(bean);
            }
        }
        return ret;
    }
}
