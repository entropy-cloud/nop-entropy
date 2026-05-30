/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.model;

import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.util.INeedInit;
import io.nop.commons.util.StringHelper;
import io.nop.commons.lang.ITagSetSupport;
import io.nop.core.type.IGenericType;
import io.nop.core.type.parse.GenericTypeParser;
import io.nop.rpc.model._gen._ApiMethodModel;

import static io.nop.rpc.model.RpcModelConstants.OPTION_REST_PATH;
import static io.nop.rpc.model.RpcModelConstants.OPTION_TCC_CANCEL_METHOD;
import static io.nop.rpc.model.RpcModelConstants.OPTION_TCC_CONFIRM_METHOD;
import static io.nop.rpc.model.RpcModelConstants.OPTION_TIMEOUT;
import static io.nop.rpc.model.RpcModelConstants.OPTION_USE_TCC;

public class ApiMethodModel extends _ApiMethodModel implements IWithOptions, ITagSetSupport, INeedInit {
    private static final String VOID_TYPE = "Void";

    private transient ApiServiceModel serviceModel;

    public ApiMethodModel() {

    }

    @Override
    public void init() {
        init(null);
    }

    public void init(ApiServiceModel serviceModel) {
        if (serviceModel != null) {
            this.serviceModel = serviceModel;
        }
    }

    public String getSimpleRequestMessage() {
        return simplifyMessageType(getRequestMessage());
    }

    public String getSimpleResponseMessage() {
        return simplifyMessageType(getResponseMessage() == null ? null : getResponseMessage().toString());
    }

    public boolean isVoidRequest() {
        return VOID_TYPE.equals(getSimpleRequestMessage());
    }

    @Override
    public String getRequestMessage() {
        return StringHelper.normalizeClassName(super.getRequestMessage(), getBeanPackage(), false);
    }

    @Override
    public IGenericType getResponseMessage() {
        IGenericType type = super.getResponseMessage();
        if (type == null)
            return null;

        String typeName = type.toString();
        String beanPackage = getBeanPackage();
        if (beanPackage != null) {
            GenericTypeParser parser = new GenericTypeParser();
            IGenericType resolved = parser.parseFromText(null, typeName);
            resolved.resolveClassName(name -> StringHelper.normalizeClassName(name, beanPackage, true));
            return resolved;
        }
        return type;
    }

    public String getRestPath() {
        return (String) getOptionValue(OPTION_REST_PATH);
    }

    public void setRestPath(String restPath) {
        setOptionValue(OPTION_REST_PATH, restPath);
    }

    public boolean isUseTcc() {
        return ConvertHelper.toPrimitiveBoolean(getOption(OPTION_USE_TCC), false, NopException::new);
    }

    public void setUseTcc(boolean useTcc) {
        setOptionValue(OPTION_USE_TCC, useTcc);
    }

    public String getTccConfirmMethod() {
        return (String) getOptionValue(OPTION_TCC_CONFIRM_METHOD);
    }

    public void setTccConfirmMethod(String confirmMethod) {
        setOptionValue(OPTION_TCC_CONFIRM_METHOD, confirmMethod);
    }

    public String getTccCancelMethod() {
        return (String) getOptionValue(OPTION_TCC_CANCEL_METHOD);
    }

    public void setTccCancelMethod(String cancelMethod) {
        setOptionValue(OPTION_TCC_CANCEL_METHOD, cancelMethod);
    }

    public Long getTimeout() {
        return ConvertHelper.toLong(getOptionValue(OPTION_TIMEOUT), NopException::new);
    }

    public void setTimeout(Long timeout) {
        setOptionValue(OPTION_TIMEOUT, timeout);
    }

    private String simplifyMessageType(String typeName) {
        if (typeName == null)
            return null;

        String apiPackageName = getApiPackageName();
        if (apiPackageName == null) {
            apiPackageName = guessApiPackageName(typeName);
        }

        if (apiPackageName != null)
            return StringHelper.simplifyJavaType(typeName, apiPackageName + ".beans");

        return StringHelper.simplifyJavaType(typeName);
    }

    private String getBeanPackage() {
        String apiPackageName = getApiPackageName();
        return apiPackageName == null ? null : apiPackageName + ".beans";
    }

    private String getApiPackageName() {
        return serviceModel == null ? null : serviceModel.getApiPackageName();
    }

    private String guessApiPackageName(String typeName) {
        int beanPackagePos = typeName.indexOf(".api.beans.");
        if (beanPackagePos > 0)
            return typeName.substring(0, beanPackagePos + ".api".length());

        int packagePos = typeName.lastIndexOf('.');
        if (packagePos > 0)
            return typeName.substring(0, packagePos);

        return null;
    }
}
