/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.service.audit;

import io.nop.api.core.annotations.txn.Transactional;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.time.CoreMetrics;
import io.nop.api.core.audit.AuditRequest;
import io.nop.api.core.audit.IAuditService;
import io.nop.auth.dao.entity.NopAuthOpLog;
import io.nop.commons.concurrent.batch.AbstractBatchProcessService;
import io.nop.dao.api.IDaoProvider;
import io.nop.dao.api.IEntityDao;

import javax.inject.Inject;
import java.sql.Timestamp;
import java.util.List;
import java.util.stream.Collectors;

public class AuditServiceImpl extends AbstractBatchProcessService<AuditRequest> implements IAuditService {
    @Inject
    IDaoProvider daoProvider;

    @Override
    public void saveAudit(AuditRequest request) {
        try {
            getQueue().send(request);
        } catch (Exception e) {
            throw NopException.adapt(e);
        }
    }

    @Transactional
    @Override
    protected void doProcess(List<AuditRequest> list) {
        IEntityDao<NopAuthOpLog> dao = daoProvider.daoFor(NopAuthOpLog.class);

        List<NopAuthOpLog> logs = list.stream().map(this::buildLog).collect(Collectors.toList());
        dao.batchSaveEntities(logs);
    }

    NopAuthOpLog buildLog(AuditRequest request) {
        NopAuthOpLog log = new NopAuthOpLog();
        log.setBizActionName(request.getAction());
        log.setBizObjName(request.getBizObj());
        Timestamp time = request.getActionTime();
        if (time == null)
            time = CoreMetrics.currentTimestamp();

        log.setErrorCode(request.getErrorCode());
        log.setResultStatus(request.getResultStatus());
        log.setTitle(request.getMessage());

        log.setSessionId(request.getSessionId());
        log.setOpRequest(request.getRequestData());
        log.setOpResponse(request.getResponseData());
        log.setUsedTime(request.getUsedTime());
        log.setUserName(request.getUserName());
        log.setCreateTime(time);
        log.setCreatedBy(request.getUserName());
        return log;
    }
}