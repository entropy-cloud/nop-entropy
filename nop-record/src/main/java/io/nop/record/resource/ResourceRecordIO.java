/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.record.resource;

import io.nop.core.resource.IResource;
import io.nop.core.resource.record.IResourceRecordIO;
import io.nop.dataset.record.IRecordInput;
import io.nop.dataset.record.IRecordOutput;

public class ResourceRecordIO<T> implements IResourceRecordIO<T> {
    @Override
    public IRecordInput<T> openInput(IResource resource, String encoding) {
        return null;
    }

    @Override
    public IRecordOutput<T> openOutput(IResource resource, String encoding) {
        return null;
    }
}
