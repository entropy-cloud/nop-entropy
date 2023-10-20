/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.file.core.chunk;

import io.nop.core.context.IServiceContext;
import io.nop.core.resource.IResourceStore;
import io.nop.file.core.UploadResponseBean;

public class ChunkFileUploadHandler {
    private IResourceStore chunkFileStore;

    public StartChunkResponseBean startChunkApi(StartChunkRequestBean request,
                                                IServiceContext ctx) {
        return null;
    }

    public ChunkResponseBean chunkApi(ChunkRequestBean request, IServiceContext ctx) {
        return null;
    }

    /**
     * 等所有分块上传完后，将上传文件收集到的 eTag 信息合并一起，再次请求后端完成文件上传。
     */
    public UploadResponseBean finishChunkApi(FinishChunkRequestBean request, IServiceContext ctx) {
        return null;
    }
}