/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.core.resource.record;

import io.nop.commons.record.IRecordInput;
import io.nop.commons.record.IRecordOutput;
import io.nop.core.resource.IResource;

public interface IResourceRecordIO<T> {
    IRecordInput<T> openInput(IResource resource, String encoding);

    IRecordOutput<T> openOutput(IResource resource, String encoding);
}