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

import java.io.OutputStream;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class DownloadHelper {
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
                resource.delete();
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
