package io.nop.graphql.message;

import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.exceptions.ErrorCode.define;

@Locale("zh-CN")
public interface GraphQLMessageErrors {
    String ARG_REQ_ID = "reqId";
    String ARG_OPERATION_NAME = "operationName";

    ErrorCode ERR_GRAPHQL_MESSAGE_NO_SVC_ACTION_HEADER =
            define("nop.err.graphql.message.no-svc-action-header",
                    "GraphQL消息缺少nop-svc-action头");

    ErrorCode ERR_GRAPHQL_MESSAGE_NOT_ALLOWED_OPERATION_NAME =
            define("nop.err.graphql.message.not-allowed-operation-name",
                    "GraphQL消息对应的操作[{operationName}]不在允许范围之内", ARG_OPERATION_NAME);

}
