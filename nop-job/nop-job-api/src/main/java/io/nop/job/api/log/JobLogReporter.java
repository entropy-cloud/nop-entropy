package io.nop.job.api.log;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.FutureHelper;
import io.nop.http.api.client.HttpRequest;
import io.nop.http.api.client.IHttpClient;
import io.nop.http.api.client.IHttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static io.nop.job.api.JobApiErrors.ARG_REPORT_URL;
import static io.nop.job.api.JobApiErrors.ERR_JOB_LOG_REPORT_DISABLED;

/**
 * worker 侧日志上报 client（plan 2254，可选行为）。
 * <p>
 * 上报地址 = worker 部署时受信配置 {@code nop.job.log.report-url}，**不随请求头下发**
 * （避免恶意请求伪造上报地址导致日志泄露或诱导 SSRF）。未配置时 reporter 显式关闭
 * （{@link #isEnabled()} 返回 false），不吞异常、不产生任何网络请求。
 * <p>
 * 上报失败 best-effort：异步发送 + 吞错（记录 WARN 日志），**不影响任务执行与状态机**。
 * 业务代码在 worker 侧执行上下文中显式调用（可选 SLF4J/logback appender 属后续增强）。
 */
public class JobLogReporter {
    static final Logger LOG = LoggerFactory.getLogger(JobLogReporter.class);

    private String reportUrl;
    private IHttpClient httpClient;

    public void setReportUrl(String reportUrl) {
        this.reportUrl = reportUrl;
    }

    /**
     * 普通 setter（可选注入先例）：容器无 IHttpClient bean 时 reporter 不启用。
     */
    public void setHttpClient(IHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public boolean isEnabled() {
        return reportUrl != null && !reportUrl.isBlank() && httpClient != null;
    }

    public void report(TaskLogEntry entry) {
        if (!isEnabled()) {
            throw new NopException(ERR_JOB_LOG_REPORT_DISABLED)
                    .param(ARG_REPORT_URL, reportUrl);
        }
        reportAsync(entry);
    }

    /**
     * best-effort 异步上报：失败仅 WARN 日志，调用方不感知异常。
     */
    public CompletionStage<Void> reportAsync(TaskLogEntry entry) {
        if (!isEnabled()) {
            return CompletableFuture.completedFuture(null);
        }
        ApiRequest<TaskLogEntry> request = new ApiRequest<>();
        request.setData(entry);
        HttpRequest httpRequest = HttpRequest.post(reportUrl).body(request);
        try {
            return httpClient.fetchAsync(httpRequest, null)
                    .handle((response, err) -> {
                        if (err != null) {
                            LOG.warn("nop.job.log.report-failed:taskId={}", entry.getJobTaskId(), err);
                            return null;
                        }
                        if (response.getHttpStatus() >= 400) {
                            LOG.warn("nop.job.log.report-http-failed:taskId={},statusCode={},body={}",
                                    entry.getJobTaskId(), response.getHttpStatus(), response.getBodyAsString());
                        }
                        return null;
                    });
        } catch (Exception e) {
            LOG.warn("nop.job.log.report-failed:taskId={}", entry.getJobTaskId(), e);
            return CompletableFuture.completedFuture(null);
        }
    }
}
