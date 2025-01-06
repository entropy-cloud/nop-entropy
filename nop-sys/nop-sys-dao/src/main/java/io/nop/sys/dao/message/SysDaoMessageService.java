package io.nop.sys.dao.message;

import io.nop.api.core.beans.ApiRequest;
import io.nop.api.core.message.IMessageConsumer;
import io.nop.api.core.message.IMessageService;
import io.nop.api.core.message.IMessageSubscription;
import io.nop.api.core.message.MessageSendOptions;
import io.nop.api.core.message.MessageSubscribeOptions;
import io.nop.api.core.message.TopicMessage;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.ApiHeaders;
import io.nop.api.core.util.FutureHelper;
import io.nop.commons.concurrent.executor.IThreadPoolExecutor;
import io.nop.commons.util.DateHelper;
import io.nop.commons.util.MathHelper;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.sys.dao.entity.NopSysEvent;
import jakarta.inject.Inject;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.concurrent.CompletionStage;

public class SysDaoMessageService implements IMessageService {
    private IDaoProvider daoProvider;

    private IThreadPoolExecutor executor;

    public void setExecutor(IThreadPoolExecutor executor) {
        this.executor = executor;
    }

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    protected IEntityDao<NopSysEvent> dao() {
        return daoProvider.daoFor(NopSysEvent.class);
    }

    protected void toEvent(NopSysEvent event, String topic, Object message, long eventTime) {
        event.setEventTopic(topic);
        event.setEventStatus(0);
        event.setEventTime(new Timestamp(eventTime));
        event.setBizDate(DateHelper.millisToDate(eventTime));
        event.setEventName(message.getClass().getSimpleName());
        event.setProcessTime(event.getEventTime());

        if (message instanceof ApiRequest) {
            ApiRequest<?> request = (ApiRequest<?>) message;
            String bizKey = ApiHeaders.getBizKey(request);
            String bizObjName = ApiHeaders.getSvcName(request);
            event.setBizKey(bizKey);
            event.setBizObjName(bizObjName);
            if (bizKey != null) {
                event.setPartitionIndex((int) StringHelper.shortHash(bizObjName + '|' + bizKey));
            }
            String svcAction = ApiHeaders.getSvcAction(request);
            if (svcAction != null) {
                event.setEventName(svcAction);

                if (bizObjName == null) {
                    int pos = svcAction.indexOf('_');
                    if (pos > 0) {
                        bizObjName = svcAction.substring(0, pos);
                        event.setBizObjName(bizObjName);
                    }
                }
            }
            if (request.getHeaders() != null)
                event.setEventHeaders(JsonTool.stringify(request.getHeaders()));
            if (request.getSelection() != null) {
                event.setSelection(request.getSelection().toString());
            }
            if (request.getData() != null)
                event.setEventData(JsonTool.stringify(request.getData()));
        }
    }

    @Override
    public CompletionStage<Void> sendAsync(String topic, Object message, MessageSendOptions options) {
        IEntityDao<NopSysEvent> dao = dao();
        NopSysEvent event = dao.newEntity();
        event.setPartitionIndex(MathHelper.random().nextInt(Short.MAX_VALUE));
        toEvent(event, topic, message, CoreMetrics.currentTimeMillis());

        dao.saveEntityDirectly(event);
        return FutureHelper.success(null);
    }

    @Override
    public CompletionStage<Void> sendMultiAsync(Collection<TopicMessage> messages, MessageSendOptions options) {
        IEntityDao<NopSysEvent> dao = dao();
        long currentTime = CoreMetrics.currentTimeMillis();
        for (TopicMessage message : messages) {
            NopSysEvent event = dao.newEntity();
            event.setPartitionIndex(MathHelper.random().nextInt(Short.MAX_VALUE));
            toEvent(event, message.getTopic(), message.getMessage(), currentTime);
            dao.saveEntityDirectly(event);
        }
        return FutureHelper.success(null);
    }

    @Override
    public IMessageSubscription subscribe(String topic, IMessageConsumer listener, MessageSubscribeOptions options) {
        return null;
    }
}
