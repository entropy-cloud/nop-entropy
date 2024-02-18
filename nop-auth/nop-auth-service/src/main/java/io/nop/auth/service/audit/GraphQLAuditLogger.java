package io.nop.auth.service.audit;

import io.nop.api.core.annotations.ioc.InjectValue;
import io.nop.api.core.audit.AuditRequest;
import io.nop.api.core.audit.IAuditService;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.graphql.GraphQLResponseBean;
import io.nop.api.core.config.AppConfig;
import io.nop.api.core.time.CoreMetrics;
import io.nop.commons.util.StringHelper;
import io.nop.core.exceptions.ErrorMessageManager;
import io.nop.core.i18n.I18nMessageManager;
import io.nop.graphql.core.IGraphQLExecutionContext;
import io.nop.graphql.core.IGraphQLLogger;
import io.nop.graphql.core.ast.GraphQLFieldDefinition;
import io.nop.graphql.core.ast.GraphQLFieldSelection;
import io.nop.graphql.core.ast.GraphQLOperation;
import io.nop.graphql.core.ast.GraphQLOperationType;
import io.nop.graphql.core.ast.GraphQLSelection;
import io.nop.graphql.core.ast.GraphQLSelectionSet;
import jakarta.inject.Inject;

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;

public class GraphQLAuditLogger implements IGraphQLLogger {
    private Set<String> auditMutationPatterns;

    private Set<String> auditQueryPatterns;

    private IAuditService auditService;

    @InjectValue("@cfg:nop.auth.graphql.audit-mutation-patterns|")
    public void setAuditMutationPatterns(Set<String> auditMutationPatterns) {
        this.auditMutationPatterns = auditMutationPatterns;
    }

    @InjectValue("@cfg:nop.auth.graphql.audit-query-patterns|")
    public void setAuditQueryPatterns(Set<String> auditQueryPatterns) {
        this.auditQueryPatterns = auditQueryPatterns;
    }

    @Inject
    public void setAuditService(IAuditService auditService) {
        this.auditService = auditService;
    }

    protected boolean shouldAudit(GraphQLOperationType operationType, String operationName) {
        if (operationType == GraphQLOperationType.query || operationType == null) {
            if (auditQueryPatterns != null) {
                return StringHelper.matchSimplePatternSet(operationName, auditQueryPatterns);
            }
        } else {
            if (auditMutationPatterns != null) {
                return StringHelper.matchSimplePatternSet(operationName, auditMutationPatterns);
            }
        }
        return false;
    }

    private boolean shouldAudit(IGraphQLExecutionContext context) {
        GraphQLOperation operation = context.getOperation();
        GraphQLSelectionSet selectionSet = operation.getSelectionSet();
        if (selectionSet == null || selectionSet.isEmpty())
            return false;

        FieldSelectionBean selection = context.getFieldSelection();
        for (String field : selection.getFields().keySet()) {
            if (shouldAudit(operation.getOperationType(), field))
                return true;
        }
        return false;
    }

    @Override
    public void onRpcExecute(IGraphQLExecutionContext context, long beginTime,
                             ApiResponse<?> response, Throwable exception) {
        if (!shouldAudit(context))
            return;

        AuditRequest audit = newAuditRequest(context, beginTime);
        if (response != null) {
            audit.setResultStatus(response.getStatus());
            audit.setErrorCode(response.getCode());
            audit.setRetMessage(response.getMsg());
        }
        addException(audit, exception);
        auditService.saveAudit(audit);
    }

    @Override
    public void onGraphQLExecute(IGraphQLExecutionContext context, long beginTime,
                                 GraphQLResponseBean response, Throwable exception) {
        if (!shouldAudit(context))
            return;

        AuditRequest audit = newAuditRequest(context, beginTime);
        if (response != null) {
            audit.setResultStatus(response.getStatus());
            audit.setErrorCode(response.getErrorCode());
            audit.setRetMessage(response.getMsg());
        }
        addException(audit, exception);
        auditService.saveAudit(audit);
    }

    AuditRequest newAuditRequest(IGraphQLExecutionContext context, long beginTime) {
        long usedTime = CoreMetrics.currentTimeMillis() - beginTime;

        AuditRequest audit = new AuditRequest();
        audit.setActionTime(new Timestamp(beginTime));
        audit.setUsedTime(usedTime);
        audit.setTenantId(context.getContext().getTenantId());
        if (context.getUserContext() != null) {
            audit.setUserName(context.getUserContext().getUserName());
            audit.setUserId(context.getUserContext().getUserId());
            audit.setSessionId(context.getUserContext().getSessionId());
        } else {
            audit.setUserName("-");
        }
        audit.setAppId(AppConfig.appName());

        audit.setOperation(getOperation(context));
        audit.setDescription(getDescription(context));

        return audit;
    }

    String getOperation(IGraphQLExecutionContext context) {
        StringBuilder sb = new StringBuilder();

        FieldSelectionBean selection = context.getFieldSelection();
        for (String field : selection.getFields().keySet()) {
            if (sb.length() > 0)
                sb.append('|');
            sb.append(field);
        }

        return sb.toString();
    }

    String getDescription(IGraphQLExecutionContext context) {
        StringBuilder sb = new StringBuilder();
        Set<String> names = new HashSet<>();

        for (GraphQLSelection selection : context.getOperation().getSelectionSet().getSelections()) {
            if (selection instanceof GraphQLFieldSelection) {
                GraphQLFieldDefinition field = ((GraphQLFieldSelection) selection).getFieldDefinition();
                if (names.add(field.getOperationName())) {
                    String desc = field.getDescription();
                    if (desc != null) {
                        String locale = AppConfig.defaultLocale();
                        desc = I18nMessageManager.instance().resolveI18nVar(locale, desc);
                    } else {
                        desc = field.getOperationName();
                    }
                    if (sb.length() > 0)
                        sb.append('|');
                    sb.append(desc);
                }
            }
        }

        return sb.toString();
    }


    void addException(AuditRequest audit, Throwable exception) {
        if (exception != null) {
            String locale = AppConfig.defaultLocale();
            ErrorBean error = ErrorMessageManager.instance().buildErrorMessage(locale, exception, false, false);
            audit.setResultStatus(error.getStatus());
            audit.setRetMessage(error.getDescription());
            audit.setErrorCode(error.getErrorCode());
        }
    }
}
