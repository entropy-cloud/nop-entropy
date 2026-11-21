/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.service.cache;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.api.core.annotations.biz.BizQuery;
import io.nop.api.core.annotations.biz.BizMutation;
import io.nop.api.core.annotations.core.Name;
import io.nop.api.core.beans.WebContentBean;
import io.nop.dao.api.IDaoProvider;
import io.nop.report.dao.entity.NopReportResultFile;
import io.nop.api.core.annotations.orm.SingleSession;
import jakarta.inject.Inject;

import io.nop.api.core.exceptions.NopException;
import io.nop.report.service.auth.NopReportAuthChecker;

import java.time.LocalDate;
import java.util.Map;

/**
 * 报表结果缓存接口（WI6）：下载（命中或同步生成）、异步生成状态轮询、手动失效。
 * 全部 action 先经 IReportEngine.getXptModel 报表级授权（WI1），授权先于缓存查询（命中不越权）。
 */
@BizModel("NopReportCache")
public class NopReportCacheBizModel {

    @Inject
    NopReportResultCache resultCache;

    @Inject
    NopReportAuthChecker authChecker;

    @Inject
    IDaoProvider daoProvider;

    @SingleSession
    @BizQuery
    public WebContentBean download(@Name("reportPath") String reportPath,
                                   @Name("renderType") String renderType,
                                   @Name("params") Map<String, Object> params,
                                   @Name("bizDate") LocalDate bizDate) {
        reportPathAuth(reportPath);
        NopReportResultFile record = resultCache.getOrGenerate(reportPath, renderType, params, bizDate);
        // filePath 可能是绝对路径（缓存关闭时落在临时目录），否则按缓存目录拼接
        java.io.File file = NopReportResultCache.resolveRecordFile(record);
        io.nop.core.resource.impl.FileResource resource = new io.nop.core.resource.impl.FileResource(file);
        return new WebContentBean(mimeType(renderType), resource.toFile(), record.getFileName(), false);
    }

    @SingleSession
    @BizQuery
    public NopReportResultFile getResultStatus(@Name("reportPath") String reportPath,
                                               @Name("cacheKey") String cacheKey) {
        // 授权先行（与 download 同一入口检查）
        reportPathAuth(reportPath);
        String rptId = requireReportId(reportPath);
        NopReportResultFile record = resultCache.findByStatus(rptId, cacheKey, NopReportResultCache.STATUS_GENERATING);
        if (record == null)
            record = resultCache.findDone(rptId, cacheKey);
        if (record == null)
            record = resultCache.findByStatus(rptId, cacheKey, NopReportResultCache.STATUS_FAILED);
        return record;
    }

    @SingleSession
    @BizMutation
    public void invalidate(@Name("reportPath") String reportPath) {
        reportPathAuth(reportPath);
        resultCache.invalidate(requireReportId(reportPath));
    }

    private void reportPathAuth(String reportPath) {
        // 入口边界授权（WI1 语义修订：引擎内部不校验，由对外 action 显式调用）
        authChecker.checkReportAccess(reportPath);
    }

    private String requireReportId(String reportPath) {
        io.nop.report.dao.entity.NopReportDefinition probe =
                daoProvider.daoFor(io.nop.report.dao.entity.NopReportDefinition.class).newEntity();
        probe.setRptText(reportPath);
        io.nop.report.dao.entity.NopReportDefinition definition =
                daoProvider.daoFor(io.nop.report.dao.entity.NopReportDefinition.class).findFirstByExample(probe);
        if (definition == null)
            throw new NopException(ReportCacheErrors.ERR_REPORT_CACHE_NO_DEFINITION)
                    .param(ReportCacheErrors.ARG_REPORT_PATH, reportPath);
        return definition.getRptId();
    }

    private String mimeType(String renderType) {
        switch (renderType) {
            case "html":
                return "text/html";
            case "pdf":
                return "application/pdf";
            case "csv":
                return "text/csv";
            case "fods":
                return "application/vnd.oasis.opendocument.spreadsheet";
            case "rtf":
                return "application/rtf";
            case "png":
                return "application/zip"; // 多页输出为 zip
            default:
                return "application/octet-stream";
        }
    }
}
