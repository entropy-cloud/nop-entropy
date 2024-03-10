/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.commons.service;

import io.nop.api.core.exceptions.NopException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.atomic.AtomicInteger;

import static io.nop.commons.CommonErrors.ARG_SERVICE;
import static io.nop.commons.CommonErrors.ERR_SERVICE_NOT_ACTIVE;
import static io.nop.commons.CommonErrors.ERR_SERVICE_NOT_ALLOW_START_AFTER_STOP;

public abstract class LifeCycleSupport implements ILifeCycle {
    private static final Logger LOG = LoggerFactory.getLogger(LifeCycleSupport.class);

    private AtomicInteger status = new AtomicInteger(ServiceStatus.CREATED.ordinal());
    private boolean allowRestart = true;
    private String id;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String toString() {
        return getClass().getSimpleName() + "[" + getId() + "]";
    }

    /**
     * 调用stop之后是否允许调用start来再次启动
     *
     * @return
     */
    public boolean isAllowRestart() {
        return allowRestart;
    }

    public void setAllowRestart(boolean allowRestart) {
        this.allowRestart = allowRestart;
    }

    public boolean isActive() {
        return status.get() == ServiceStatus.ACTIVE.ordinal();
    }

    public ServiceStatus getStatus() {
        return ServiceStatus.values()[status.get()];
    }

    protected void checkIsActive() {
        if (getStatus() != ServiceStatus.ACTIVE)
            throw new NopException(ERR_SERVICE_NOT_ACTIVE).param(ARG_SERVICE, this);
    }

    @PostConstruct
    @Override
    public void start() {
        if (!isAllowRestart()) {
            if (getStatus() == ServiceStatus.STOPPED)
                throw new NopException(ERR_SERVICE_NOT_ALLOW_START_AFTER_STOP).param(ARG_SERVICE, this);
        }

        if (status.compareAndSet(ServiceStatus.CREATED.ordinal(), ServiceStatus.STARTING.ordinal())) {
            LOG.debug("nop.commons.service.starting:service={}", this);
            try {
                doStart();
                status.compareAndSet(ServiceStatus.STARTING.ordinal(), ServiceStatus.ACTIVE.ordinal());
            } catch (Exception e) {
                LOG.debug("nop.commons.service.start-fail:service={}", this, e);
                stop();
                throw NopException.adapt(e);
            }
            LOG.debug("nop.commons.service.started:service={}", this);
        }
    }

    synchronized boolean beginStop() {
        if (getStatus().isAllowStop()) {
            status.set(ServiceStatus.STOPPING.ordinal());
            return true;
        }
        return false;
    }

    @PreDestroy
    @Override
    public void stop() {
        if (beginStop()) {
            try {
                doStop();
            } catch (Exception e) {
                LOG.error("nop.commons.service.stop-fail:service={}", this, e);
                // ignore error
            } finally {
                ServiceStatus endStatus = isAllowRestart() ? ServiceStatus.CREATED : ServiceStatus.STOPPED;
                status.compareAndSet(ServiceStatus.STOPPING.ordinal(), endStatus.ordinal());
            }
            LOG.debug("nop.commons.service.stop:service={}", this);
        }
    }

    protected abstract void doStart();

    protected abstract void doStop();
}