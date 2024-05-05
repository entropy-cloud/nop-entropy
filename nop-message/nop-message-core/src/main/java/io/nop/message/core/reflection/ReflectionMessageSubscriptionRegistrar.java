package io.nop.message.core.reflection;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.annotations.message.MessageListener;
import io.nop.api.core.ioc.IBeanProvider;
import io.nop.api.core.message.IMessageConsumeContext;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageSubscriber;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.commons.functional.ITriFunction;
import io.nop.core.lang.eval.DisabledEvalScope;
import io.nop.core.reflect.IClassModel;
import io.nop.core.reflect.IFunctionArgument;
import io.nop.core.reflect.IFunctionModel;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.type.IGenericType;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ReflectionMessageSubscriptionRegistrar {
    static final Logger LOG = LoggerFactory.getLogger(ReflectionMessageSubscriptionRegistrar.class);

    private List<Object> messageBeans;

    private List<IMessageSubscription> subscriptions;

    private IBeanProvider beanContainer;

    public void setMessageBeans(List<Object> beans) {
        this.messageBeans = beans;
    }

    /**
     * 缺省情况下注入当前bean容器
     */
    @InjectValue("@bean:container")
    public void setBeanContainer(IBeanProvider beanContainer) {
        this.beanContainer = beanContainer;
    }

    public void register() {
        if (messageBeans != null) {
            this.subscriptions = new ArrayList<>();

            for (Object bean : messageBeans) {
                registerSubscriptionBean(bean);
            }
        }
    }

    private void registerSubscriptionBean(Object bean) {
        IClassModel classModel = ReflectionManager.instance().getClassModel(bean.getClass());
        for (IFunctionModel functionModel : classModel.getMethods()) {
            MessageListener listener = functionModel.getAnnotation(MessageListener.class);
            if (listener == null)
                continue;

            registerListener(listener, bean, functionModel);
        }
    }

    private void registerListener(MessageListener listener, Object bean, IFunctionModel functionModel) {
        IMessageSubscriber subscriber = (IMessageSubscriber) beanContainer.getBean(listener.messageServiceBean());
        MessageSubscribeOptions options = null;
        if (!listener.subscribeOptionsBean().isEmpty()) {
            options = (MessageSubscribeOptions) beanContainer.getBean(listener.subscribeOptionsBean());
        }

        ITriFunction<String, Object, IMessageConsumeContext, Object[]> argsBuilder = argsBuilder(functionModel);

        IMessageConsumer consumer = new IMessageConsumer() {
            @Override
            public Object onMessage(String topic, Object message, IMessageConsumeContext context) {
                Object[] args = argsBuilder.apply(topic, message, context);
                return functionModel.invoke(bean, args, DisabledEvalScope.INSTANCE);
            }
        };

        for (String topic : listener.topic()) {
            IMessageSubscription subscription = subscriber.subscribe(topic, consumer, options);
            subscriptions.add(subscription);
        }
    }

    @SuppressWarnings("rawtypes")
    private ITriFunction<String, Object, IMessageConsumeContext, Object[]> argsBuilder(IFunctionModel functionModel) {
        ITriFunction[] argBuilders = new ITriFunction[functionModel.getArgCount()];
        int i = 0;
        for (IFunctionArgument argModel : functionModel.getArgs()) {
            if (argModel.getRawClass() == IMessageConsumeContext.class) {
                argBuilders[i] = (topic, message, context) -> context;
            } else if (argModel.getName().equals("message")) {
                argBuilders[i] = buildMessageArg(argModel);
            } else if (argModel.getName().equals("topic")) {
                argBuilders[i] = (topic, message, context) -> topic;
            } else if (argModel.getRawClass() == String.class) {
                argBuilders[i] = (topic, message, context) -> topic;
            } else if (i == 0) {
                argBuilders[i] = (topic, message, context) -> message;
            } else {
                throw new IllegalArgumentException("nop.err.unsupported-message-argument:" + argModel.getName() + "," + argModel.getType());
            }
            i++;
        }
        return (topic, message, context) -> {
            Object[] args = new Object[argBuilders.length];
            int index = 0;
            for (ITriFunction func : argBuilders) {
                args[index] = func.apply(topic, message, context);
                index++;
            }
            return args;
        };
    }

    private ITriFunction<String, Object, IMessageConsumeContext, Object> buildMessageArg(IFunctionArgument argModel) {
        IGenericType returnType = argModel.getType();
        return (topic, message, ctx) -> BeanTool.castBeanToType(message, returnType);
    }

    @PreDestroy
    public void destroy() {
        if (subscriptions != null) {
            for (IMessageSubscription subscription : subscriptions) {
                try {
                    subscription.cancel();
                } catch (Exception e) {
                    LOG.error("nop.err.message.cancel-subscription-fail", e);
                }
            }
        }
    }
}