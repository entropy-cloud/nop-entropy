package io.nop.sys.dao.naming;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.annotations.orm.SingleSession;
import io.nop.api.core.beans.FilterBeans;
import io.nop.api.core.beans.query.QueryBean;
import io.nop.api.core.beans.query.QueryFieldBean;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.util.Guard;
import io.nop.cluster.discovery.ServiceInstance;
import io.nop.cluster.naming.INamingService;
import io.nop.commons.concurrent.executor.GlobalExecutors;
import io.nop.commons.util.TagsHelper;
import io.nop.core.lang.json.JsonTool;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;
import io.nop.sys.dao.entity.NopSysServiceInstance;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SysDaoNamingService implements INamingService {
    static final Logger LOG = LoggerFactory.getLogger(SysDaoNamingService.class);

    private IDaoProvider daoProvider;
    private Duration autoUpdateInterval;
    private boolean autoCleanup;
    private Duration cleanupInterval;
    private Future<?> cleanupFuture;
    private String groupName;

    public void setAutoUpdateInterval(Duration autoUpdateInterval) {
        this.autoUpdateInterval = autoUpdateInterval;
    }

    public void setCleanupInterval(Duration cleanupInterval) {
        this.cleanupInterval = cleanupInterval;
    }

    public void setAutoCleanup(boolean autoCleanup) {
        this.autoCleanup = autoCleanup;
    }

    @InjectValue("@cfg:nop.application.group|DEFAULT")
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    @Inject
    public void setDaoProvider(IDaoProvider daoProvider) {
        this.daoProvider = daoProvider;
    }

    IEntityDao<NopSysServiceInstance> dao() {
        return daoProvider.daoFor(NopSysServiceInstance.class);
    }

    long getMaxUpdateInterval() {
        if (autoUpdateInterval != null)
            return autoUpdateInterval.toMillis() + 1000;
        return 60 * 1000;
    }

    @PostConstruct
    public void init() {
        if (autoCleanup && cleanupInterval != null && cleanupInterval.toMillis() > 0) {
            cleanupFuture = GlobalExecutors.globalTimer().scheduleWithFixedDelay(this::cleanup, cleanupInterval.toMillis(), cleanupInterval.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    @PreDestroy
    public void destroy() {
        if (cleanupFuture != null) {
            cleanupFuture.cancel(false);
            cleanupFuture = null;
        }
    }

    void cleanup() {
        IEntityDao<NopSysServiceInstance> dao = dao();
        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(NopSysServiceInstance.PROP_NAME_groupName, groupName));
        query.addFilter(FilterBeans.lt(NopSysServiceInstance.PROP_NAME_updateTime, new Timestamp(CoreMetrics.currentTimeMillis() - 2 * getMaxUpdateInterval())));
        query.addFilter(FilterBeans.eq(NopSysServiceInstance.PROP_NAME_isEphemeral, true));
        dao.deleteByQuery(query);
    }

    @SingleSession
    @Override
    public void registerInstance(ServiceInstance instance) {
        if (instance.getGroupName() == null)
            instance.setGroupName(groupName);

        Guard.checkEquals(groupName, instance.getGroupName());

        IEntityDao<NopSysServiceInstance> dao = dao();
        NopSysServiceInstance entity = dao.getEntityById(instance.getInstanceId());
        if (entity != null) {
            copyToEntity(entity, instance);
            entity.setUpdateTime(CoreMetrics.currentTimestamp());
            dao.updateEntity(entity);
        } else {
            entity = toEntity(instance);
            dao.saveEntity(entity);
        }
    }

    @Override
    public void updateInstance(ServiceInstance instance) {
        registerInstance(instance);
    }

    @Override
    public void unregisterInstance(ServiceInstance instance) {
        try {
            dao().deleteEntityById(instance.getInstanceId());
        } catch (Exception e) {
            LOG.error("nop.err.cluster.delete-service-instance-fail", e);
        }
    }

    @Override
    public List<String> getServices() {
        IEntityDao<NopSysServiceInstance> dao = dao();
        QueryBean query = new QueryBean();
        query.distinct().addField(QueryFieldBean.forField(NopSysServiceInstance.PROP_NAME_serviceName));
        query.addFilter(FilterBeans.eq(NopSysServiceInstance.PROP_NAME_groupName, groupName));
        query.addFilter(FilterBeans.gt(NopSysServiceInstance.PROP_NAME_updateTime, new Timestamp(CoreMetrics.currentTimeMillis() - getMaxUpdateInterval())));
        return dao.selectStringFieldByQuery(query);
    }

    @Override
    public List<ServiceInstance> getInstances(String serviceName) {
        IEntityDao<NopSysServiceInstance> dao = dao();

        QueryBean query = new QueryBean();
        query.addFilter(FilterBeans.eq(NopSysServiceInstance.PROP_NAME_serviceName, serviceName));
        query.addFilter(FilterBeans.eq(NopSysServiceInstance.PROP_NAME_groupName, groupName));

        // 如果长时间没有更新，则认为服务实例已经失效
        query.addFilter(FilterBeans.gt(NopSysServiceInstance.PROP_NAME_updateTime, new Timestamp(CoreMetrics.currentTimeMillis() - getMaxUpdateInterval())));
        return dao.findAllByQuery(query).stream().map(this::fromEntity).sorted().collect(Collectors.toList());
    }

    protected NopSysServiceInstance toEntity(ServiceInstance instance) {
        NopSysServiceInstance entity = new NopSysServiceInstance();
        copyToEntity(entity, instance);
        return entity;
    }

    protected void copyToEntity(NopSysServiceInstance entity, ServiceInstance instance) {
        String groupName = instance.getGroupName() == null ? "DEFAULT" : instance.getGroupName();

        entity.setInstanceId(instance.getInstanceId());
        entity.setServiceName(instance.getServiceName());
        entity.setServerAddr(instance.getAddr());
        entity.setServerPort(instance.getPort());
        entity.setClusterName(instance.getClusterName() == null ? "DEFAULT" : instance.getClusterName());
        entity.setGroupName(groupName);


        entity.setIsEnabled(instance.isEnabled());
        entity.setIsEphemeral(instance.isEphemeral());
        entity.setIsHealthy(instance.isHealthy());
        if (instance.getMetadata() != null)
            entity.setMetaData(JsonTool.serialize(instance.getMetadata(), false));
        entity.setTagsText(TagsHelper.toString(instance.getTags()));
        entity.setWeight(instance.getWeight());
    }

    protected ServiceInstance fromEntity(NopSysServiceInstance entity) {
        ServiceInstance instance = new ServiceInstance();
        instance.setInstanceId(entity.getInstanceId());
        instance.setServiceName(entity.getServiceName());
        instance.setAddr(entity.getServerAddr());
        instance.setPort(entity.getServerPort());
        instance.setClusterName(entity.getClusterName());
        instance.setGroupName(entity.getGroupName());
        instance.setEnabled(entity.getIsEnabled());
        instance.setEphemeral(entity.getIsEphemeral());
        instance.setHealthy(entity.getIsHealthy());
        instance.setWeight(entity.getWeight());
        if (entity.getMetaData() != null)
            instance.setMetadata((Map<String, String>) JsonTool.parseNonStrict(entity.getMetaData()));
        instance.setTags(TagsHelper.parse(entity.getTagsText(), ','));
        return instance;
    }
}