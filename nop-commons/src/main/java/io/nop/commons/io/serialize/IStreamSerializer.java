/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.io.serialize;

import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStream;

public interface IStreamSerializer {
    ObjectOutput getObjectOutput(OutputStream os);

    ObjectInput getObjectInput(InputStream is);

    void serializeToStream(Object o, OutputStream os);

    Object deserializeFromStream(InputStream is);
}
