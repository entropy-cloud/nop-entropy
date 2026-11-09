package io.nop.biz.download;

import io.nop.api.core.beans.WebContentBean;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.util.IoHelper;
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.core.resource.zip.IZipOutput;
import io.nop.core.resource.zip.IZipTool;
import io.nop.core.resource.zip.ZipOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 2.3-C4-005（plan 2551 裁定 E）：下载临时文件的一次性清理必须可观测——原形态
 * {@code resource.delete()} 返回值被忽略、异常随 lambda 被定时器吞掉，删除失败即文件永驻。
 * 修正：清理结果 WARN 记录（结构化键 {@code nop.warn.download.temp-clean-fail}）；
 * 平台级兜底对账见 {@link DownloadTempReconciler}（周期扫描 temp:/download 整树）。
 */
public class DownloadHelper {
    private static final Logger LOG = LoggerFactory.getLogger(DownloadHelper.class);

    /**
     * R2-n-2 锚定：当前调用面（downloadZip 唯一公开形态）的等待窗上界常量——对账器的
     * {@code nop.download.temp-max-age} 配置必须大于此值（构建期校验，见 DownloadTempReconciler），
     * 防止对账误删仍在等待窗口内的在途下载文件。
     */
    public static final int MAX_WAIT_MINUTES = 60;

    public static WebContentBean downloadZip(String fileName, int waitMinutes,
                                             Consumer<IZipOutput> action,
                                             ZipOptions zipOptions) {
        return downloadZip(ResourceHelper.getTempResource("download"), ResourceHelper.getZipTool(),
                fileName, waitMinutes, action, zipOptions);
    }

    static WebContentBean downloadZip(IResource resource, IZipTool zipTool, String fileName, int waitMinutes,
                                      Consumer<IZipOutput> action, ZipOptions zipOptions) {
        IZipOutput zipOutput = null;
        OutputStream os = null;
        try {
            os = resource.getOutputStream();
            zipOutput = zipTool.newZipOutput(os, zipOptions);

            action.accept(zipOutput);
            zipOutput.flush();

            WebContentBean content = new WebContentBean("application/zip",
                    resource.toFile(), fileName);

            GlobalExecutors.globalTimer().schedule(() -> {
                try {
                    boolean deleted = resource.delete();
                    if (!deleted) {
                        // 2.3-C4-005：删除失败必须可观测（原形态 boolean 被忽略→文件永驻无痕）
                        LOG.warn("nop.warn.download.temp-clean-fail:path={},reason=delete-returned-false",
                                resource.getPath());
                    }
                } catch (Throwable e) {
                    // 定时器任务体内异常原被吞——显式 WARN 留痕，下次对账扫描兜底
                    LOG.warn("nop.warn.download.temp-clean-fail:path={}", resource.getPath(), e);
                }
                return null;
            }, waitMinutes, TimeUnit.MINUTES);
            IoHelper.safeCloseObject(zipOutput);
            return content;
        } catch (Exception e) {
            // newZipOutput抛错时zipOutput仍为null，必须一并关闭底层os，避免文件描述符泄漏
            IoHelper.safeCloseObject(zipOutput);
            IoHelper.safeCloseObject(os);
            resource.delete();
            throw NopException.adapt(e);
        }
    }
}
