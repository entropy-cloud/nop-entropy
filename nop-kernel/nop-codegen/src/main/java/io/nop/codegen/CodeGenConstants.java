/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.codegen;

public interface CodeGenConstants {
    String NS_ATTR = "attr";
    String NS_SLOT = "slot";
    String NS_ON = "on";

    String NS_V_ON = "v-on";
    String NS_V_BIND = "v-bind";
    String NS_V_SLOT = "v-slot";
    String TAG_TEMPLATE = "template";

    String ATTR_J_TYPE = "j:type";
    String ATTR_SCOPE = "scope";

    String PROP_TYPE = "type";
    String PROP_BODY = "body";

    String ATTR_EXPR_PREFIX = "@:";

    String CODEGEN_TEMPLATES_PATH = "/templates/";
    String CODEGEN_TEMP_PATH = "temp:/codegen/";

    String INIT_FILE_NAME = "@init.xrun";

    String VAR_CODE_GEN_LOOP = "codeGenLoop";

    String VAR_CODE_GENERATOR = "codeGenerator";

    String VAR_CODE_GEN_MODEL_PATH = "codeGenModelPath";

    String VAR_CODE_GEN_MODEL = "codeGenModel";

    String VAR_CODE_GEN_PROJECT = "codeGenProject";
}