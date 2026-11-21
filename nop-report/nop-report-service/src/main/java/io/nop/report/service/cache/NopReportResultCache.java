/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.report.service.cache;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.dao.jdbc.IJdbcTemplate;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.resource.IResource;
import io.nop.core.resource.impl.FileResource;
import io.nop.core.resource.tpl.ITemplateOutput;
import io.nop.dao.api.IDaoProvider;
import io.nop.excel.model.ExcelWorkbook;
import io.nop.report.core.engine.IReportEngine;
import io.nop.report.dao.entity.NopReportDefinition;
import io.nop.report.service.NopReportConfigs;
import io.nop.report.dao.entity.NopReportResultFile;
import jakarta.inject.Inject;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 报表结果文件缓存（见 ai-dev/design/nop-report/result-cache.md）。
 * <p>
 * 键控：rptId + SHA-256(规范化参数 JSON) + renderType(fileType) + 数据版本 bizDate。
 * 命中不越权：所有入口先经 {@link IReportEngine#getXptModel}（WI1 报表级授权），授权先于缓存查询。
 * 字段映射（不新增 ORM 列）：renderType→fileType、参数摘要→rptParams、数据版本→bizDate、TTL 以 createTime 判定；
 * status：0=生成中 / 1=完成 / 2=失败（内部约定，不复用 core/active-status）。
 */
public class NopReportResultCache {
    public static final int STATUS_GENERATING = 0;
    public static final int STATUS_DONE = 1;
    public static final int STATUS_FAILED = 2;

    @Inject
    IReportEngine reportEngine;

    @Inject
    IDaoProvider daoProvider;

    @Inject
    IJdbcTemplate jdbcTemplate;

    /**
     * 进程内同 key 并发去重锁（单 JVM 语义；多实例去重需 DB 唯一约束，超出本轮范围）。
     */
    private final ConcurrentHashMap<String, Object> keyLocks = new ConcurrentHashMap<>();

    /**
     * 测试与 IoC 用：替换引擎（spy 计数渲染次数）。
     */
    public void setReportEngine(IReportEngine reportEngine) {
        this.reportEngine = reportEngine;
    }

    /**
     * 缓存查询键（不含报表路径——已由 rptId 承载）。
     */
    public String cacheKey(String renderType, Map<String, Object> params, LocalDate bizDate) {
        String paramsDigest = sha256Hex(JsonTool.stringify(params == null ? Map.of() : params));
        String biz = bizDate == null ? LocalDate.now().toString() : bizDate.toString();
        return renderType + ":" + paramsDigest + ":" + biz;
    }

    /**
     * 同步获取：命中返回记录；未命中同步渲染落盘落库后返回。
     */
    public NopReportResultFile getOrGenerate(String reportPath, String renderType,
                                             Map<String, Object> params, LocalDate bizDate) {
        String rptId = requireReportId(reportPath);

        if (!cacheEnabled()) {
            // 开关关闭：直接渲染，不读不写缓存
            NopReportResultFile direct = newRecord(rptId, cacheKey(renderType, params, bizDate), renderType,
                    JsonTool.stringify(params == null ? Map.of() : params), bizDate);
            direct.setStatus(STATUS_DONE);
            doGenerate(direct, reportPath, renderType, params);
            return direct;
        }

        String cacheKey = cacheKey(renderType, params, bizDate);
        String normalizedParams = JsonTool.stringify(params == null ? Map.of() : params);

        // 进程内锁：同 key 并发下载只生成一次（锁内二次查重）
        Object lock = keyLocks.computeIfAbsent(rptId + ":" + cacheKey, k -> new Object());
        synchronized (lock) {
            NopReportResultFile cached = findDone(rptId, cacheKey);
            if (cached != null)
                return cached;

            return generate(reportPath, rptId, renderType, cacheKey, normalizedParams, bizDate, params);
        }
    }

    /**
     * 异步生成：查重后落生成中记录，线程池渲染；返回缓存键供轮询。
     */
    public String generateAsync(String reportPath, String renderType,
                                Map<String, Object> params, LocalDate bizDate) {
        if (!cacheEnabled()) {
            // 开关关闭：同步渲染，不落缓存
            NopReportResultFile record = newRecord(requireReportId(reportPath),
                    cacheKey(renderType, params, bizDate), renderType,
                    JsonTool.stringify(params == null ? Map.of() : params), bizDate);
            record.setStatus(STATUS_DONE);
            doGenerate(record, reportPath, renderType, params);
            return cacheKey(renderType, params, bizDate);
        }
        String rptId = requireReportId(reportPath);
        String cacheKey = cacheKey(renderType, params, bizDate);
        String normalizedParams = JsonTool.stringify(params == null ? Map.of() : params);

        if (findDone(rptId, cacheKey) != null)
            return cacheKey;
        if (findByStatus(rptId, cacheKey, STATUS_GENERATING) != null)
            return cacheKey;

        NopReportResultFile record = newRecord(rptId, cacheKey, renderType, normalizedParams, bizDate);
        record.setStatus(STATUS_GENERATING);
        daoProvider.daoFor(NopReportResultFile.class).saveEntity(record);

        GlobalExecutors.globalWorker().execute(() -> {
            // 异步线程无 ORM session：状态回写走直接 SQL
            try {
                doGenerate(record, reportPath, renderType, params);
                updateStatusByJdbc(record.getSid(), STATUS_DONE, record.getFilePath(),
                        record.getFileName(), record.getFileLength(), null);
            } catch (Exception e) {
                updateStatusByJdbc(record.getSid(), STATUS_FAILED, null, null, 0L, String.valueOf(e));
            }
        });
        return cacheKey;
    }

    public NopReportResultFile findByStatus(String rptId, String cacheKey, int status) {
        NopReportResultFile probe = daoProvider.daoFor(NopReportResultFile.class).newEntity();
        probe.setRptId(rptId);
        probe.setRptParams(cacheKey);
        probe.setStatus(status);
        List<NopReportResultFile> list = daoProvider.daoFor(NopReportResultFile.class).findAllByExample(probe);
        return latest(list);
    }

    public NopReportResultFile findDone(String rptId, String cacheKey) {
        NopReportResultFile record = findByStatus(rptId, cacheKey, STATUS_DONE);
        if (record == null)
            return null;
        if (isExpired(record))
            return null;
        return record;
    }

    boolean cacheEnabled() {
        return Boolean.TRUE.equals(NopReportConfigs.CFG_REPORT_RESULT_CACHE_ENABLED.get());
    }

    boolean isExpired(NopReportResultFile record) {
        long ttl = NopReportConfigs.CFG_REPORT_RESULT_CACHE_TTL.get();
        if (ttl <= 0 || record.getCreateTime() == null)
            return false;
        long ageMillis = System.currentTimeMillis() - record.getCreateTime().getTime();
        return ageMillis > ttl * 1000L;
    }

    NopReportResultFile generate(String reportPath, String rptId, String renderType,
                                 String cacheKey, String normalizedParams, LocalDate bizDate,
                                 Map<String, Object> params) {
        NopReportResultFile record = newRecord(rptId, cacheKey, renderType, normalizedParams, bizDate);
        try {
            doGenerate(record, reportPath, renderType, params);
            record.setStatus(STATUS_DONE);
        } catch (Exception e) {
            record.setStatus(STATUS_FAILED);
            record.setDescription(String.valueOf(e));
            daoProvider.daoFor(NopReportResultFile.class).saveEntity(record);
            throw NopException.adapt(e);
        }
        daoProvider.daoFor(NopReportResultFile.class).saveEntity(record);
        return record;
    }

    private void doGenerate(NopReportResultFile record, String reportPath, String renderType,
                            Map<String, Object> params) {
        ExcelWorkbook workbook = reportEngine.getXptModel(reportPath);
        ITemplateOutput output = reportEngine.getRendererForXptModel(workbook, renderType);

        java.io.File dir = targetDir();
        java.io.File file = new java.io.File(dir, record.getFilePath());
        if (!file.getParentFile().exists())
            file.getParentFile().mkdirs();
        // 临时文件渲染 + 原子改名：并发/中断不会把半成品留在最终路径
        java.io.File tmp = new java.io.File(file.getParentFile(),
                file.getName() + "." + UUID.randomUUID() + ".tmp");
        try {
            FileResource resource = new FileResource(tmp);
            output.generateToResource(resource, scopeWithParams(params));
            renameFile(tmp, file);
        } finally {
            if (tmp.exists())
                tmp.delete();
        }

        if (!cacheEnabled()) {
            // 开关关闭：文件落在临时目录且记录不入库，filePath 记实际绝对路径供 download 定位
            record.setFilePath(file.getAbsolutePath());
        }
        record.setFileName(file.getName());
        record.setFileLength((long) file.length());
    }

    /**
     * 开关开启 = 缓存目录；关闭 = 系统临时目录（不向缓存目录产生孤儿文件）。
     */
    java.io.File targetDir() {
        String path = cacheEnabled() ? NopReportConfigs.CFG_REPORT_RESULT_CACHE_DIR.get()
                : System.getProperty("java.io.tmpdir") + "/nop-report-result";
        java.io.File dir = new java.io.File(path);
        if (!dir.exists())
            dir.mkdirs();
        return dir;
    }

    /**
     * 解析记录文件：filePath 为绝对路径（缓存关闭时的临时目录产物）直接用；否则按缓存目录拼接。
     */
    public static java.io.File resolveRecordFile(NopReportResultFile record) {
        java.io.File file = new java.io.File(record.getFilePath());
        if (!file.isAbsolute())
            file = new java.io.File(NopReportConfigs.CFG_REPORT_RESULT_CACHE_DIR.get(), record.getFilePath());
        return file;
    }

    private static void renameFile(java.io.File tmp, java.io.File file) {
        try {
            Files.move(tmp.toPath(), file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                throw NopException.adapt(ex);
            }
        } catch (IOException e) {
            throw NopException.adapt(e);
        }
    }

    private io.nop.core.lang.eval.IEvalScope scopeWithParams(Map<String, Object> params) {
        io.nop.core.lang.eval.IEvalScope scope = io.nop.xlang.api.XLang.newEvalScope();
        if (params != null && !params.isEmpty())
            scope.setLocalValue(null, io.nop.report.core.params.ReportParamResolver.VAR_PARAM, params);
        return scope;
    }

    /**
     * 手动失效：删除缓存记录并 best-effort 删除文件。
     */
    public void invalidate(String rptId) {
        NopReportResultFile probe = daoProvider.daoFor(NopReportResultFile.class).newEntity();
        probe.setRptId(rptId);
        List<NopReportResultFile> list = daoProvider.daoFor(NopReportResultFile.class).findAllByExample(probe);
        for (NopReportResultFile record : list) {
            if (!StringHelper.isEmpty(record.getFilePath())) {
                IResource resource = new FileResource(resolveRecordFile(record));
                if (resource.exists())
                    resource.delete();
            }
        }
        // 直接 SQL 删除（ORM session 边界：跨 session 实体 delete 需 attach，SQL 最简）
        io.nop.core.lang.sql.SQL sql = io.nop.core.lang.sql.SQL.begin()
                .append("delete from nop_report_result_file where RPT_ID = ")
                .param(rptId)
                .end();
        jdbcTemplate.executeUpdate(sql);
    }

    private NopReportResultFile latest(List<NopReportResultFile> list) {
        NopReportResultFile ret = null;
        for (NopReportResultFile record : list) {
            if (ret == null || (record.getCreateTime() != null && ret.getCreateTime() != null
                    && record.getCreateTime().after(ret.getCreateTime())))
                ret = record;
        }
        return ret;
    }

    private NopReportResultFile newRecord(String rptId, String cacheKey, String renderType,
                                          String normalizedParams, LocalDate bizDate) {
        NopReportResultFile record = daoProvider.daoFor(NopReportResultFile.class).newEntity();
        record.setRptId(rptId);
        record.setRptParams(cacheKey);
        record.setFileType(renderType);
        record.setFileLength(0L);
        // filePath 存相对缓存目录的相对路径（precision 100 限制；下载时拼接 result-cache.dir）
        String hex = sha256Hex(cacheKey);
        String relPath = rptId + "/" + hex + "." + renderType;
        record.setFileName(hex + "." + renderType);
        record.setFilePath(relPath);
        record.setStatus(STATUS_GENERATING);
        record.setBizDate(bizDate == null ? LocalDate.now() : bizDate);
        record.setDescription(normalizedParams.length() > 1000
                ? normalizedParams.substring(0, 1000) : normalizedParams);
        return record;
    }

    private void updateStatusByJdbc(String sid, int status, String filePath, String fileName, long length, String error) {
        io.nop.core.lang.sql.SQL.SqlBuilder sb = io.nop.core.lang.sql.SQL.begin()
                .append("update nop_report_result_file set STATUS = ").param(status);
        if (filePath != null) {
            sb.append(", FILE_PATH = ").param(filePath)
                    .append(", FILE_NAME = ").param(fileName)
                    .append(", FILE_LENGTH = ").param(length);
        }
        if (error != null)
            sb.append(", DESCRIPTION = ").param(error.length() > 1000 ? error.substring(0, 1000) : error);
        sb.append(" where SID = ").param(sid);
        jdbcTemplate.executeUpdate(sb.end());
    }

    static String sha256Hex(String text) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String requireReportId(String reportPath) {
        NopReportDefinition probe = new NopReportDefinition();
        probe.setRptText(reportPath);
        NopReportDefinition definition = daoProvider.daoFor(NopReportDefinition.class).findFirstByExample(probe);
        if (definition == null)
            throw new NopException(ReportCacheErrors.ERR_REPORT_CACHE_NO_DEFINITION)
                    .param(ReportCacheErrors.ARG_REPORT_PATH, reportPath);
        return definition.getRptId();
    }
}
