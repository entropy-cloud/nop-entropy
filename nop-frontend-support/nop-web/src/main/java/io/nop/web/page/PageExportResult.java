/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.web.page;

import java.io.File;
import java.util.List;

/**
 * 页面批量导出结果。pages 为相对导出根目录的输出文件路径清单（不含前导斜杠）；
 * failedPages 收集导出过程中的单页失败（不中断整体导出），供调用方一次看到全部坏页。
 */
public class PageExportResult {
    private final List<String> pages;
    private final List<ErrorItem> failedPages;
    private final File manifestFile;

    public PageExportResult(List<String> pages, List<ErrorItem> failedPages, File manifestFile) {
        this.pages = pages;
        this.failedPages = failedPages;
        this.manifestFile = manifestFile;
    }

    public List<String> getPages() {
        return pages;
    }

    public List<ErrorItem> getFailedPages() {
        return failedPages;
    }

    public File getManifestFile() {
        return manifestFile;
    }

    public static class ErrorItem {
        private final String path;
        private final String errorCode;
        private final String message;

        public ErrorItem(String path, String errorCode, String message) {
            this.path = path;
            this.errorCode = errorCode;
            this.message = message;
        }

        public String getPath() {
            return path;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public String getMessage() {
            return message;
        }
    }
}
