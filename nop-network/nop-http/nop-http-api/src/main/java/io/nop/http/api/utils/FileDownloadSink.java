/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.http.api.utils;

import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.progress.IProgressListener;
import io.nop.http.api.HttpApiErrors;
import io.nop.http.api.client.DownloadOptions;
import io.nop.http.api.client.IHttpOutputFile;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;

/**
 * 下载落盘状态机：.part 续传状态、206 追加/200 截断、写入时同步计算 SHA-256/SHA-1、
 * 完成后摘要校验与原子改名。三个客户端实现共用，保证跨实现的 .part 布局与失败语义一致。
 * 协议契约见 ai-dev/design/nop-network/file-transfer-design.md
 *
 * <p>4.3-C6-013（plan 2561 裁定 M，残留语义声明）：<b>直写路径零回滚</b>——
 * {@link #begin(int, String, long)} 的非文件形态/未启用续传分支
 * {@code out = targetFile.getOutputStream()} 直接写目标文件本体，无 .part 临时名 +
 * 原子改名中介。该路径上传输中途失败（IO 异常、校验失败、取消）已在目标文件留下
 * <b>部分内容且无回滚/清理动作</b>（{@link #close()} 仅关流保留已写数据）；调用方若需
 * 原子交付语义，必须自行写入临时文件后移动。此残留语义为本批显式声明，临时名重构
 * 避让后续 C6-001(P0)/C3-005(P1) 下载链修复批（plan 2561 Non-Goals）。</p>
 */
public class FileDownloadSink {
    private final IHttpOutputFile targetFile;
    private final DownloadOptions options;
    private final Object progressMessage;

    private OutputStream out;
    private File partFile;
    private long startOffset;
    private long bytesWritten;
    private long totalBytes = -1;

    private final MessageDigest sha256 = FileTransferHelper.newDigest(FileTransferHelper.SHA256);
    private final MessageDigest sha1 = FileTransferHelper.newDigest(FileTransferHelper.SHA1);

    public FileDownloadSink(IHttpOutputFile targetFile, DownloadOptions options, Object progressMessage) {
        this.targetFile = targetFile;
        this.options = options != null ? options : new DownloadOptions();
        this.progressMessage = progressMessage;
    }

    /**
     * 续传起始偏移：优先 .part 已有长度，否则 initialOffset
     */
    public long resolveOffset() {
        File part = resolvePartFile();
        if (part != null && part.exists() && options.isResume())
            return part.length();
        return options.getInitialOffset();
    }

    private File resolvePartFile() {
        File target = targetFile.toFile();
        if (target == null || !options.isResume())
            return null;
        return FileTransferHelper.partFileOf(target);
    }

    /**
     * 是否走 .part 续传落盘形态（目标为本地文件且启用续传）。返回 false 即<b>直写形态</b>：
     * 数据直接写入 {@code targetFile.getOutputStream()}，不具备 .part 隔离与原子改名
     * （4.3-C6-013 残留语义，见类注释）。
     */
    public boolean isFileMode() {
        return targetFile.toFile() != null && options.isResume();
    }

    public File getPartFile() {
        return partFile;
    }

    public long getStartOffset() {
        return startOffset;
    }

    public long getBytesWritten() {
        return bytesWritten;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public MessageDigest digestFor(String algorithm) {
        return FileTransferHelper.SHA1.equals(algorithm) ? sha1 : sha256;
    }

    /**
     * 按响应状态打开输出。416/非 2xx 不写盘，由调用方决定重试或以错误状态完成
     */
    public void begin(int status, String contentRangeHeader, long contentLength) throws IOException {
        startOffset = resolveOffset();
        if (status == 206) {
            long[] range = FileTransferHelper.parseContentRange(contentRangeHeader);
            if (range == null || range[0] != startOffset) {
                throw new NopException(HttpApiErrors.ERR_HTTP_RESPONSE_ERROR)
                        .param(HttpApiErrors.ARG_HTTP_STATUS, status)
                        .param(HttpApiErrors.ARG_BODY, "invalid content-range for resume: " + contentRangeHeader);
            }
            totalBytes = range[1];
        } else {
            // 服务端不支持 Range 或资源已变更：整体重传
            startOffset = 0;
            totalBytes = contentLength;
        }

        if (isFileMode()) {
            partFile = resolvePartFile();
            File parent = partFile.getParentFile();
            if (parent != null)
                parent.mkdirs();

            if (status == 206) {
                // 摘要须覆盖"已有 + 续写"的全部字节：先重读本地 .part 预热
                FileTransferHelper.primeDigestFromFile(sha256, partFile, startOffset);
                FileTransferHelper.primeDigestFromFile(sha1, partFile, startOffset);
                out = new FileOutputStream(partFile, true);
            } else {
                out = new FileOutputStream(partFile, false);
            }
        } else {
            // 直连输出（无文件形态或未启用续传）：恒为整体写入
            // 4.3-C6-013（plan 2561 裁定 M）：直写目标本体，无临时名中介——中途失败零回滚（见类注释）
            startOffset = 0;
            out = targetFile.getOutputStream();
        }
        out = new BufferedOutputStream(FileTransferHelper.wrap(FileTransferHelper.wrap(out, sha256), sha1));
    }

    public void write(byte[] bytes, int offset, int length) throws IOException {
        if (out == null)
            throw new IllegalStateException("sink not started");
        out.write(bytes, offset, length);
        bytesWritten += length;

        IProgressListener listener = options.getProgressListener();
        if (listener != null) {
            long received = startOffset + bytesWritten;
            listener.onProgress(progressMessage, received, totalBytes > 0 ? totalBytes : received);
        }
    }

    /**
     * 流结束：对账总字节数（已知时）
     */
    public void finish() throws IOException {
        if (out != null) {
            out.flush();
        }
        if (totalBytes > 0 && startOffset + bytesWritten != totalBytes) {
            throw new NopException(HttpApiErrors.ERR_HTTP_RESPONSE_ERROR)
                    .param(HttpApiErrors.ARG_BODY, "downloaded size mismatch: expected total=" + totalBytes
                            + ", received=" + (startOffset + bytesWritten));
        }
    }

    /**
     * 校验并把 .part 原子改名为目标文件。校验失败删除 .part（损坏数据不能作为续传基底）。
     *
     * <p>C3-005（plan 2522，错误以成功交付族）：rename 无条件强制——校验来源缺失
     * （{@code expected == null}）仅豁免校验、不豁免落盘收尾；此前 null 分支直接返回，
     * 下载报成功而目标文件永不产生（.part 滞留）。</p>
     *
     * <p>4.3-C6-013（plan 2561 裁定 M，残留语义声明）：直写形态（{@code partFile == null}）
     * 下本方法<b>只校验不回滚</b>——校验失败时无 .part 可删，已直写入目标的损坏内容
     * 原样残留，仅以异常告知调用方；清理责任在调用方。</p>
     */
    public void verifyAndRename(FileTransferHelper.ExpectedChecksum expected) throws IOException {
        if (expected != null) {
            try {
                FileTransferHelper.verifyChecksum(expected, digestFor(expected.getAlgorithm()));
            } catch (Exception e) {
                if (partFile != null)
                    Files.deleteIfExists(partFile.toPath());
                throw e;
            }
        }
        if (partFile != null) {
            Files.move(partFile.toPath(), targetFile.toFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void close() {
        if (out != null) {
            try {
                out.close();
            } catch (IOException ignored) {
                // 关闭失败时保留已写数据供续传，不掩盖主流程结果
            }
        }
    }
}
