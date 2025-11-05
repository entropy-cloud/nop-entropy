/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.model;

public interface RpcModelConstants {
    String API_IMPL_PATH = "/nop/rpc/imp/api.imp.xml";

    String XDSL_SCHEMA_API = "/nop/schema/api.xdef";

    String EXT_PROTO_VERSION = "ext:protoVersion";


    String PROTO_TYPE_EMPTY = "google.protobuf.Empty";

    String PROTO_TYPE_ANY = "google.protobuf.Any";

    String OPTION_TCC_CANCEL_METHOD = "nop_tcc_cancel_method";

    String OPTION_TCC_CONFIRM_METHOD = "nop_tcc_confirm_method";

    String OPTION_TIMEOUT = "nop_timeout";

    String OPTION_REST_PATH = "nop_rest_path";

    String OPTION_USE_TCC = "nop_use_tcc";

    String OPTION_EXAMPLE = "nop_example";
}
