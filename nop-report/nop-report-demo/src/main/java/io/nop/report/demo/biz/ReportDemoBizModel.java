package io.nop.report.demo.biz;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.beans.TreeResultBean;
import io.nop.core.resource.IResource;
import io.nop.core.resource.VirtualFileSystem;

import java.util.List;

@BizModel("ReportDemo")
public class ReportDemoBizModel {

    @BizQuery
    public TreeResultBean getDemoReports() {
        TreeResultBean ret = new TreeResultBean();
        ret.setLabel("报表示例");

        List<? extends IResource> groups = VirtualFileSystem.instance().getChildren("/nop/auth/demo");
        for (IResource resource : groups) {

        }
        return ret;
    }
}
