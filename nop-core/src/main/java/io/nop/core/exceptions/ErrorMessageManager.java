/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.core.exceptions;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.api.core.beans.ApiResponse;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.context.ContextProvider;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.IErrorMessageManager;
import io.nop.api.core.exceptions.IException;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.exceptions.NopRebuildException;
import io.nop.commons.util.StringHelper;
import io.nop.commons.util.objects.Pair;
import io.nop.core.CoreConstants;
import io.nop.core.i18n.I18nMessageManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.nop.core.CoreConfigs.CFG_ERROR_MESSAGE_PUBLIC_FOR_NO_MAPPING;

@GlobalInstance
public class ErrorMessageManager implements IErrorMessageManager {
    static final Logger LOG = LoggerFactory.getLogger(ErrorMessageManager.class);

    static ErrorMessageManager _instance = new ErrorMessageManager();

    public static final ErrorMessageManager instance() {
        return _instance;
    }

    private IErrorMessageBuilder errorMessageBuilder;
    private IErrorMessageVarResolver varResolver;

    private Map<String, ErrorCodeMapping> errorCodeMappings = new ConcurrentHashMap<>();

    /**
     * 同一个错误码可以根据参数的不同细化映射到不同的对外响应错误码，并显示不同的错误描述喜喜
     */
    private Map<String, List<SubErrorCodeMapping>> subMappings = new ConcurrentHashMap<>();

    public void setErrorMessageBuilder(IErrorMessageBuilder builder) {
        this.errorMessageBuilder = builder;
    }

    public void setErrorMessageVarResolver(IErrorMessageVarResolver varResolver) {
        this.varResolver = varResolver;
    }

    public void addErrorCodeMappings(Map<String, ErrorCodeMapping> errorCodeMappings) {
        if (errorCodeMappings != null) {
            errorCodeMappings.forEach((key, value) -> {
                int pos = key.indexOf('?');
                if (pos < 0) {
                    ErrorCodeMapping old = this.errorCodeMappings.put(key, value);
                    if (old != null) {
                        LOG.info("nop.core.exceptions.replace-error-code-mapping:key={},loc={},oldLoc-{}", key,
                                value.getLocation(), old.getLocation());
                    }
                } else {
                    // 细化的错误码映射，格式为 errorCode?paramName=paramValue&paramName2=paramValue2
                    String code = key.substring(0, pos);
                    Map<String, String> filter = StringHelper.parseSimpleQuery(key.substring(pos + 1));
                    SubErrorCodeMapping subMapping = new SubErrorCodeMapping(filter, value);
                    List<SubErrorCodeMapping> list = this.subMappings.computeIfAbsent(code,
                            k -> new CopyOnWriteArrayList<>());
                    synchronized (list) {
                        boolean found = false;
                        for (int i = 0, n = list.size(); i < n; i++) {
                            SubErrorCodeMapping old = list.get(i);
                            if (old.isSameFilter(subMapping)) {
                                found = true;
                                list.set(i, subMapping);
                                LOG.info("nop.core.exceptions.replace-error-code-mapping:key={},loc={},oldLoc-{}", key,
                                        value.getLocation(), old.getMapping().getLocation());
                                break;
                            }
                        }
                        if (!found) {
                            list.add(subMapping);
                        }
                    }
                }
            });
        }
    }

    public void clearErrorCodeMappings() {
        this.errorCodeMappings.clear();
    }

    public void loadErrorCodeMappings() {
        Map<String, ErrorCodeMapping> mappings = new ErrorCodeMappingsLoader().loadErrorCodeMappings();
        this.addErrorCodeMappings(mappings);
    }

    public String getErrorDescription(String locale, String errorCode, Map<String, ?> params) {
        String message = I18nMessageManager.instance().getMessage(locale, errorCode, null);
        if (message == null)
            return null;

        message = resolveDescription(locale, message, params);
        return message;
    }

    @Override
    public ErrorBean resolveErrorBean(ErrorBean error, boolean onlyPublic) {
        if (error.isResolved())
            return error;

        Pair<ErrorBean, ErrorCodeMapping> pair = applyMapping(ContextProvider.currentLocale(), error, onlyPublic);
        if (pair == null) {
            return error;
        }
        return pair.getFirst();
    }

    public String resolveDescription(String locale, String message, Map<String, ?> params) {
        if (params != null && !params.isEmpty() && message.indexOf('{') >= 0) {
            message = StringHelper.renderTemplate(message, name -> {
                if (!params.containsKey(name)) {
                    return "{" + name + "}";
                }
                Object param = params.get(name);
                if (param == null)
                    return "";
                return resolveTemplateVar(locale, name, param, params);
            });
        }
        return message;
    }

    protected String resolveTemplateVar(String locale, String name, Object value, Map<String, ?> params) {
        if (varResolver != null)
            return varResolver.resolveVar(locale, name, value, params);

        String str = StringHelper.safeToString(value);
        if (ApiConstants.PARAM_ENTITY_NAME.equals(name) || ApiConstants.PARAM_REF_ENTITY_NAME.equals(name)) {
            str = getEntityDisplayName(locale, str);
        } else if (ApiConstants.PARAM_FIELD_NAME.equals(name) || ApiConstants.PARAM_PROP_NAME.equals(name)) {
            str = getFieldDisplayName(locale, str, params);
        }
        return str;
    }

    protected boolean isWrapException(Throwable e) {
        return e instanceof IException && ((IException) e).isWrapException();
    }

    @Override
    public Throwable getRealCause(Throwable e) {
        Throwable cause = e.getCause();
        if (cause == e || cause == null)
            return e;

        if (isWrapException(e))
            return getRealCause(cause);

        if (e instanceof IException)
            return e;

        return getRealCause(cause);
    }

    public Throwable getRootCause(Throwable e) {
        Throwable cause = getRealCause(e);
        if (cause == null || cause == e)
            return cause;

        return getRootCause(cause);
    }

    protected String getEntityDisplayName(String locale, String entityName) {
        String displayName = I18nMessageManager.instance().getMessage(locale, "entity.label." + entityName, null);
        if (displayName == null) {
            int pos = entityName.lastIndexOf('.');
            if (pos > 0) {
                displayName = I18nMessageManager.instance().getMessage(locale,
                        "entity.label." + entityName.substring(pos + 1), null);
            }
        }
        if (displayName != null) {
            return displayName;// return displayName + '(' + entityName + ')';
        }
        return entityName;
    }

    protected String getFieldDisplayName(String locale, String fieldName, Map<String, ?> params) {
        String entityName = (String) params.get(ApiConstants.PARAM_ENTITY_NAME);
        String fullFieldName = fieldName;
        boolean useFullName = false;
        if (!StringHelper.isEmpty(entityName)) {
            fullFieldName = entityName + '.' + fieldName;
            useFullName = true;
        }

        String displayName = I18nMessageManager.instance().getMessage(locale, "prop.label." + fullFieldName, null);
        if (displayName == null && useFullName) {
            int pos = entityName.lastIndexOf('.');
            if (pos > 0) {
                fullFieldName = entityName.substring(pos + 1) + '.' + fieldName;
                displayName = I18nMessageManager.instance().getMessage(locale, "prop.label." + fullFieldName, null);
            }
        }
        if (displayName != null) {
            return displayName + '(' + StringHelper.lastPart(fieldName, '.') + ')';
        }
        return fieldName;
    }

    public ErrorBean defaultBuildErrorMessage(String locale, Throwable e, boolean includeStack) {
        ErrorBean error = new ErrorBean();
        if (e instanceof IException) {
            IException exp = (IException) e;
            if (exp.getStatus() != 0)
                error.setStatus(exp.getStatus());
            error.setErrorCode(exp.getErrorCode());
            error.setDescription(exp.getDescription());
            error.setParams(exp.getParams());
            error.setSourceLocation(ConvertHelper.toString(exp.getErrorLocation()));
            error.setBizFatal(exp.isBizFatal());
            error.setDetails(exp.getDetails());

            // 如果是NopRebuildException，则可能是根据ApiResponse重建的Exception
            if (e instanceof NopRebuildException)
                error.setForPublic(((NopRebuildException) e).isForPublic());

        } else {
            error.setErrorCode(e.getClass().getName());
            error.setDescription(e.getMessage());
        }

        if (includeStack) {
            error.setErrorStack(getStacktrace(e));
        }
        return error;
    }

    protected ErrorBean defaultBuildCause(String locale, Throwable e, boolean includeStack, boolean onlyPublic) {
        Throwable cause = getRootCause(e);
        if (cause != null && cause != e) {
            ErrorBean causeBean = buildErrorMessage(locale, cause, includeStack, onlyPublic);
            return causeBean;
        }
        return null;
    }

    protected String getStacktrace(Throwable e) {
        if (e instanceof NopException) {
            List<String> stack = ((NopException) e).getXplStack();
            if (!stack.isEmpty()) {
                if (stack.size() > 5) {
                    stack = stack.subList(0, 5);
                }
            }
            return StringHelper.join(stack, "\n");
        }

        StringBuilder sb = new StringBuilder();
        StackTraceElement[] stack = e.getStackTrace();
        if (stack != null) {
            int count = 0;
            for (int i = 0, n = stack.length; i < n; i++) {
                StackTraceElement element = stack[i];
                if (shouldIgnore(element))
                    continue;

                if (sb.length() > 0)
                    sb.append('\n');
                sb.append(element);
                count++;
                if (count >= 5)
                    break;
            }
        }
        return sb.toString();
    }

    protected boolean shouldIgnore(StackTraceElement element) {
        return false;
    }

    @Override
    public ErrorBean buildErrorMessage(String locale, Throwable e, boolean includeStack, boolean onlyPublic, boolean logError) {
        return _buildErrorMessage(locale, e, includeStack, onlyPublic, logError).getFirst();
    }

    private Pair<ErrorBean, ErrorCodeMapping> _buildErrorMessage(String locale, Throwable e, boolean includeStack,
                                                                 boolean onlyPublic, boolean logError) {
        if (logError)
            NopException.logIfNotTraced(LOG, "nop.build-error-message", e);

        ErrorBean errorBean;
        if (errorMessageBuilder == null) {
            e = getRealCause(e);
            errorBean = defaultBuildErrorMessage(locale, e, includeStack);
        } else {
            errorBean = errorMessageBuilder.buildErrorMessage(locale, e, includeStack);
        }

        Pair<ErrorBean, ErrorCodeMapping> pair = applyMapping(locale, errorBean, onlyPublic);
        if (pair == null)
            return buildSysError(locale);

        ErrorCodeMapping mapping = pair.getRight();
        if (mapping != null) {
            if (mapping.isIncludeCause()) {
                ErrorBean causeBean = defaultBuildCause(locale, e, includeStack, onlyPublic);
                pair.getLeft().setCause(causeBean);
            }
        }
        return pair;
    }

    protected Pair<ErrorBean, ErrorCodeMapping> applyMapping(String locale, ErrorBean errorBean, boolean onlyPublic) {
        ErrorCodeMapping mapping = getErrorCodeMapping(errorBean.getErrorCode(), errorBean.getParams());
        if (onlyPublic) {
            if (mapping == null) {
                // 虽然没有找到错误码映射，但是明确标记了是公开异常，则可以直接返回异常信息
                if (errorBean.isForPublic()) {
                    errorBean.setResolved(true);
                    return Pair.of(errorBean, mapping);
                }
            }

            if (mapping == null) {
                if (!CFG_ERROR_MESSAGE_PUBLIC_FOR_NO_MAPPING.get()) {
                    return null;
                }
            } else if (mapping.isInternal()) {
                return null;
            }
        }

        errorBean = errorBean.cloneInstance();
        errorBean.setForPublic(true);

        if (mapping != null) {
            mapError(locale, mapping, errorBean);
        }

        String desc = getErrorDescription(locale, errorBean.getErrorCode(), errorBean.getParams());
        if (desc != null) {
            errorBean.setDescription(desc);
        } else {
            if (errorBean.hasParam() && errorBean.getDescription() != null) {
                errorBean.setDescription(resolveDescription(locale, errorBean.getDescription(), errorBean.getParams()));
            }
        }

        if (errorBean.getDetails() != null) {
            Map<String, ErrorBean> details = new LinkedHashMap<>(errorBean.getDetails().size());
            for (Map.Entry<String, ErrorBean> entry : errorBean.getDetails().entrySet()) {
                ErrorBean detail = entry.getValue();
                Pair<ErrorBean, ErrorCodeMapping> mapped = applyMapping(locale, detail, onlyPublic);
                if (mapped == null)
                    continue;
                details.put(entry.getKey(), mapped.getFirst());
            }
            errorBean.setDetails(details);
        }

        errorBean.setResolved(true);
        return Pair.of(errorBean, mapping);
    }

    protected Pair<ErrorBean, ErrorCodeMapping> buildSysError(String locale) {
        ErrorBean error = new ErrorBean();
        error.setErrorCode(CoreConstants.ERROR_SYS_ERR);
        ErrorCodeMapping mapping = getErrorCodeMapping(CoreConstants.ERROR_SYS_ERR, Collections.emptyMap());
        if (mapping != null) {
            mapError(locale, mapping, error);
        }
        if (error.getDescription() == null) {
            error.setDescription(getErrorDescription(locale, CoreConstants.ERROR_SYS_ERR, Collections.emptyMap()));
        }
        error.setResolved(true);
        return Pair.of(error, mapping);
    }

    @Override
    public ApiResponse<?> buildResponse(String locale, Throwable e) {
        Pair<ErrorBean, ErrorCodeMapping> pair = _buildErrorMessage(locale, e, false, true, true);
        return _buildResponse(pair);
    }

    @Override
    public ApiResponse<?> buildResponse(String locale, ErrorBean error) {
        Pair<ErrorBean, ErrorCodeMapping> pair = applyMapping(locale, error, true);
        if (pair == null) {
            pair = buildSysError(locale);
        }
        return _buildResponse(pair);
    }

    private ApiResponse<?> _buildResponse(Pair<ErrorBean, ErrorCodeMapping> pair) {
        ErrorBean error = pair.getFirst();
        ErrorCodeMapping mapping = pair.getRight();
        int httpStatus = mapping == null ? 0 : mapping.getHttpStatus();
        int status = mapping == null || mapping.getStatus() == null ? error.getStatus() : mapping.getStatus();
        return _buildResponse(error, httpStatus, status);
    }

    protected ErrorCodeMapping getErrorCodeMapping(String errorCode, Map<String, Object> params) {
        // 同样的异常码，不同的参数，可以映射到不同的错误消息上
        List<SubErrorCodeMapping> list = subMappings.get(errorCode);
        if (list != null) {
            for (SubErrorCodeMapping sub : list) {
                if (sub.matchParams(params))
                    return sub.getMapping();
            }
        }

        return errorCodeMappings.get(errorCode);
    }

    protected void mapError(String locale, ErrorCodeMapping mapping, ErrorBean error) {
        if (!StringHelper.isEmpty(mapping.getMessageKey())) {
            String desc = this.getErrorDescription(locale, mapping.getMessageKey(), error.getParams());
            if (desc != null) {
                error.setDescription(desc);
            }
        }

        Map<String, Object> params = mappingToParams(mapping, error);

        if (!StringHelper.isEmpty(mapping.getMapToCode())) {
            error.setErrorCode(mapping.getMapToCode());

            if (StringHelper.isEmpty(mapping.getMessageKey())) {
                String desc = this.getErrorDescription(locale, mapping.getMapToCode(), params);
                if (desc != null) {
                    error.setDescription(desc);
                }
            }
        }

        if (!mapping.isReturnParams()) {
            error.setParams(null);
        } else {
            error.setParams(params);
        }

        if (mapping.getBizFatal() != null) {
            error.setBizFatal(mapping.getBizFatal());
        }
    }

    private Map<String, Object> mappingToParams(ErrorCodeMapping mapping, ErrorBean error) {
        if (mapping.getMapToParams() == null)
            return error.getParams();

        Map<String, Object> ret = new TreeMap<>();
        mapping.getMapToParams().forEach((target, src) -> {
            Object param = error.getParam(src);
            ret.put(target, param);
        });
        return ret;
    }

    private ApiResponse<Void> _buildResponse(ErrorBean error, int httpStatus, int status) {
        ApiResponse<Void> rep = new ApiResponse<>();
        rep.setStatus(status);
        rep.setHttpStatus(httpStatus);
        rep.setError(error);
        return rep;
    }
}