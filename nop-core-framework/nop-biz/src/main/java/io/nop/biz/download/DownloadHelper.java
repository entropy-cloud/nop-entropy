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
        IResource resource = ResourceHelper.getTempResource("download");
        IZipTool zipTool = ResourceHelper.getZipTool();
        IZipOutput zipOutput = null;
        try {

            OutputStream os = resource.getOutputStream();
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
            IoHelper.safeCloseObject(zipOutput);
            resource.delete();
            throw NopException.adapt(e);
        }
    }
}
