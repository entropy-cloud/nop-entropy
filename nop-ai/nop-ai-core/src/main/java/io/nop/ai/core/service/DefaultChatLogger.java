package io.nop.ai.core.service;

import io.nop.ai.api.secure.SecureDefault;
import io.nop.ai.api.chat.ChatRequest;
import io.nop.ai.api.chat.ChatResponse;
import io.nop.ai.api.chat.messages.ChatMessage;
import io.nop.ai.api.chat.IChatLogger;
import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.json.JSON;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.util.FileHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalDate;
import java.util.regex.Pattern;

@SecureDefault
public class DefaultChatLogger implements IChatLogger {
    static final Logger LOG = LoggerFactory.getLogger(DefaultChatLogger.class);

    private static final Pattern[] CREDENTIAL_PATTERNS = {
            Pattern.compile("(api[_-]?key|apikey|secret|token|password|passwd|credential)\\s*[:=]\\s*['\"]?[A-Za-z0-9_\\-./+]{8,}['\"]?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(Authorization|Bearer|X-API-Key)\\s*[:=]\\s*['\"]?\\S+['\"]?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(sk-[A-Za-z0-9]{20,})", Pattern.CASE_INSENSITIVE),
    };

    private String logDir;
    private boolean redactCredentials = true;
    // 3.1-MA-21（plan 2555 裁定 I / R1-M4）：聊天日志保留期（天），缺省 0 = 不清理。
    // 运维边界：清理为写入路径上的惰性目录级删除（logDir/yyyy/MM-dd/ 分区），
    // 每日期至多全量扫描一次（lastSweepDate 节流），不承担实时性保证。
    private int retentionDays = 0;
    private volatile LocalDate lastSweepDate;

    @InjectValue("@cfg:nop.ai.service.log-dir|/nop/ai/log")
    public void setLogDir(String logDir) {
        this.logDir = logDir;
    }

    @InjectValue("@cfg:nop.ai.service.redact-credentials|true")
    public void setRedactCredentials(boolean redactCredentials) {
        this.redactCredentials = redactCredentials;
    }

    @InjectValue("@cfg:nop.ai.chat-log.retention-days|0")
    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    @Override
    public void logRequest(ChatRequest request) {
        ChatMessage message = request.getLastMessage();
        String content = message.getContent();
        LOG.info("request:role={},content=\n{}", message.getRole(), redactIfNeeded(content));

        if (isValidLogDir()) {
            IResource resource = getResource(request, "-request.yaml");
            if (redactCredentials) {
                String redacted = JSON.serialize(request, true);
                for (Pattern p : CREDENTIAL_PATTERNS) {
                    redacted = p.matcher(redacted).replaceAll("$1: ***REDACTED***");
                }
                ResourceHelper.writeText(resource, redacted);
            } else {
                ResourceHelper.writeText(resource, JSON.serialize(request, true));
            }
            // 3.1-MA-21（plan 2555 裁定 I）：写入路径惰性保留期清理（once-per-date 节流）
            maybeSweepExpired();
        }
    }

    @Override
    public void logResponse(ChatRequest request, ChatResponse response) {
        LOG.info("response:promptTokens={},completionTokens={},content=\n{}",
                response.getPromptTokens(), response.getCompletionTokens(),
                redactIfNeeded(response.getFullContent()));

        if (isValidLogDir()) {
            IResource resource = getResource(request, "-response.yaml");
            if (redactCredentials) {
                String redacted = JSON.serialize(response, true);
                for (Pattern p : CREDENTIAL_PATTERNS) {
                    redacted = p.matcher(redacted).replaceAll("$1: ***REDACTED***");
                }
                ResourceHelper.writeText(resource, redacted, "UTF-8");
            } else {
                ResourceHelper.writeText(resource, JSON.serialize(response, true), "UTF-8");
            }
            // 3.1-MA-21（plan 2555 裁定 I）：写入路径惰性保留期清理（once-per-date 节流）
            maybeSweepExpired();
        }
    }

    private String redactIfNeeded(String content) {
        if (!redactCredentials || content == null) return content;
        String result = content;
        for (Pattern p : CREDENTIAL_PATTERNS) {
            result = p.matcher(result).replaceAll("***REDACTED***");
        }
        return result;
    }

    boolean isValidLogDir() {
        return !StringHelper.isEmpty(logDir) && !logDir.equals("none");
    }

    protected IResource getResource(ChatRequest request, String postfix) {
        return ChatLogHelper.getSessionResource(logDir, request, postfix);
    }

    /**
     * 3.1-MA-21（plan 2555 裁定 I / R1-M4 修订）：写入路径惰性保留期清理。
     * retentionDays<=0（缺省 0）时不做任何清理；>0 时删除早于
     * {@code 今天 - retentionDays} 的日期分区目录（布局 {@code logDir/yyyy/MM-dd/}，
     * 目录级删除）。once-per-date 节流：内存 lastSweepDate 保证每日期至多一次全量
     * 扫描，避免每请求目录 IO 热点。清理失败仅记日志，不影响日志写入主路径。
     */
    void maybeSweepExpired() {
        if (retentionDays <= 0 || StringHelper.isEmpty(logDir))
            return;
        LocalDate today = CoreMetrics.currentDate();
        if (today.equals(lastSweepDate))
            return;
        synchronized (this) {
            if (today.equals(lastSweepDate))
                return;
            lastSweepDate = today;
            try {
                sweepExpired(today.minusDays(retentionDays));
            } catch (Exception e) {
                LOG.warn("nop.ai.chat-log.sweep-fail:logDir={},retentionDays={}", logDir, retentionDays, e);
            }
        }
    }

    void sweepExpired(LocalDate cutoff) {
        File root = new File(logDir);
        File[] yearDirs = root.listFiles(File::isDirectory);
        if (yearDirs == null)
            return;
        for (File yearDir : yearDirs) {
            int year = parseYear(yearDir.getName());
            if (year <= 0)
                continue;
            File[] dayDirs = yearDir.listFiles(File::isDirectory);
            if (dayDirs == null)
                continue;
            for (File dayDir : dayDirs) {
                LocalDate date = parseMonthDay(year, dayDir.getName());
                if (date != null && date.isBefore(cutoff)) {
                    boolean deleted = FileHelper.deleteAll(dayDir);
                    if (deleted) {
                        LOG.info("nop.ai.chat-log.sweep-deleted:dir={}", dayDir);
                    } else {
                        LOG.warn("nop.ai.chat-log.sweep-delete-fail:dir={}", dayDir);
                    }
                }
            }
            // 机会性清理空年份目录
            String[] rest = yearDir.list();
            if (rest != null && rest.length == 0) {
                yearDir.delete();
            }
        }
    }

    static int parseYear(String name) {
        if (name == null || name.length() != 4)
            return -1;
        try {
            return Integer.parseInt(name);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static LocalDate parseMonthDay(int year, String name) {
        // 布局与 ChatLogHelper.getSessionResource 一致：MM-dd（StringHelper.padInt 两位）
        if (name == null || name.length() != 5 || name.charAt(2) != '-')
            return null;
        try {
            int month = Integer.parseInt(name.substring(0, 2));
            int day = Integer.parseInt(name.substring(3, 5));
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            return null;
        }
    }
}