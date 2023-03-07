/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons.io.serialize;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public interface IStateSerializable {
    void state_saveTo(ObjectOutput out) throws IOException;

    void state_loadFrom(ObjectInput in) throws ClassNotFoundException, IOException;
}
