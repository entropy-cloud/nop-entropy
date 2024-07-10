/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.resource.record;

import io.nop.core.resource.IResource;
import io.nop.dataset.record.IRecordInput;
import io.nop.dataset.record.IRecordOutput;

public interface IResourceRecordIO<T> extends
        IResourceRecordInputProvider<T>,
        IResourceRecordOutputProvider<T> {

    IRecordInput<T> openInput(IResource resource, String encoding);

    IRecordOutput<T> openOutput(IResource resource, String encoding);
}