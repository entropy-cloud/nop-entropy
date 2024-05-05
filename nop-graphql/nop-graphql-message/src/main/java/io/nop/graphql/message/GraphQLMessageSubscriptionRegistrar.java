package io.nop.graphql.message;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageSubscriber;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.Guard;
import io.nop.commons.util.StringHelper;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.graphql.core.GraphQLConstants;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.engine.IGraphQLEngine;
import io.nop.graphql.core.utils.GraphQLNameHelper;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static io.nop.graphql.message.GraphQLMessageErrors.ARG_OPERATION_NAME;
import static io.nop.graphql.message.GraphQLMessageErrors.ARG_REQ_ID;
import static io.nop.graphql.message.GraphQLMessageErrors.ERR_GRAPHQL_MESSAGE_NOT_ALLOWED_OPERATION_NAME;
import static io.nop.graphql.message.GraphQLMessageErrors.ERR_GRAPHQL_MESSAGE_NO_SVC_ACTION_HEADER;
import static io.nop.rpc.core.RpcErrors.ARG_TOPIC;

/**
 * 通过消息队列接收ApiRequest对象，然后派发到GraphQL引擎中执行。由此实现消息服务与GraphQL引擎的适配。
 */
public class GraphQLMessageSubscriptionRegistrar {
    static final Logger LOG = LoggerFactory.getLogger(GraphQLMessageSubscriptionRegistrar.class);

    private IGraphQLEngine graphQLEngine;

    private IMessageSubscriber messageService;
    private List<String> topics;
    private List<String> operationNames;

    private MessageSubscribeOptions subscribeOptions;

    private List<IMessageSubscription> subscriptions;

    @Inject
    public void setGraphQLEngine(IGraphQLEngine graphQLEngine) {
        this.graphQLEngine = graphQLEngine;
    }

    public void setSubscribeOptions(MessageSubscribeOptions subscribeOptions) {
        this.subscribeOptions = subscribeOptions;
    }

    public void setMessageService(IMessageSubscriber messageService) {
        this.messageService = messageService;
    }

    public void setTopics(List<String> topics) {
        this.topics = topics;
    }

    /**
     * 允许的GraphQL operationName
     */
    public void setOperationNames(List<String> operationNames) {
        this.operationNames = operationNames;
    }

    public void register() {
        Guard.notEmpty(operationNames, "operationNames");

        if (topics != null && messageService != null) {
            subscriptions = new ArrayList<>(topics.size());
            for (String topic : topics) {
                IMessageSubscription subscription = messageService.subscribe(topic, this::onMessage, subscribeOptions);
                subscriptions.add(subscription);
            }
        }
    }

    private Object onMessage(String topic, Object o, IMessageConsumeContext context) {
        String locale = null;
        try {
            ApiRequest<?> request = BeanTool.castBeanToType(o, ApiRequest.class);
            locale = ApiHeaders.getLocale(request);
            String operationName = getOperationName(topic, request);
            IGraphQLExecutionContext gqlCtx = graphQLEngine.newRpcContext(null, operationName, request);
            return graphQLEngine.executeRpcAsync(gqlCtx);
        } catch (Exception e) {
            return ErrorMessageManager.instance().buildResponse(locale, e);
        }
    }

    protected String getOperationName(String topic, ApiRequest<?> request) {
        String svcAction = ApiHeaders.getSvcAction(request);
        if (StringHelper.isEmpty(svcAction))
            throw new NopException(ERR_GRAPHQL_MESSAGE_NO_SVC_ACTION_HEADER)
                    .param(ARG_TOPIC, topic)
                    .param(ARG_REQ_ID, ApiHeaders.getId(request));

        String operationName = svcAction;
        if (svcAction.indexOf("__") < 0) {
            String svcName = ApiHeaders.getSvcName(request);
            if (svcName == null)
                svcName = GraphQLConstants.BIZ_OBJ_NAME_ROOT;
            operationName = GraphQLNameHelper.getOperationName(svcName, svcAction);
        }

        if (!StringHelper.matchSimplePatternSet(operationName, operationNames))
            throw new NopException(ERR_GRAPHQL_MESSAGE_NOT_ALLOWED_OPERATION_NAME)
                    .param(ARG_REQ_ID, ApiHeaders.getId(request))
                    .param(ARG_OPERATION_NAME, operationName)
                    .param(ARG_TOPIC, topics);
        return operationName;
    }

    @PreDestroy
    public void destroy() {
        if (subscriptions != null) {
            for (IMessageSubscription subscription : subscriptions) {
                try {
                    subscription.cancel();
                } catch (Exception e) {
                    LOG.error("nop.message.subscription-cancel-fail", e);
                }
            }
            this.subscriptions = null;
        }
    }
}