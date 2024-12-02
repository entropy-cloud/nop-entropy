/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.batch.gen;

import io.nop.batch.core.BatchConstants;

public interface BatchGenConstants {
    String MODEL_TYPE_BATCH_GEN = "batch-gen";
    String FILE_TYPE_GEN_JSON = "batch-gen.json";
    String FILE_TYPE_GEN_JSON5 = "batch-gen.json5";
    String FILE_TYPE_GEN_YAML = "batch-gen.yaml";

    String POSTFIX_BATCH_GEN_XLSX = ".batch-gen.xlsx";

    String XDSL_BATCH_GEN_IMP_PATH = "/nop/batch/imp/batch-gen.imp.xml";

    String VAR_CHUNK_CONTEXT = BatchConstants.VAR_BATCH_CHUNK_CTX;
    String VAR_CHUNK_RESPONSE = "response";
}
