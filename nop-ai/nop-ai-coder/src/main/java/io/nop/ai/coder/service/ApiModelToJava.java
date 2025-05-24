package io.nop.ai.coder.service;

import io.nop.commons.util.StringHelper;
import io.nop.rpc.model.ApiMessageFieldModel;
import io.nop.rpc.model.ApiMessageModel;
import io.nop.rpc.model.ApiMethodModel;
import io.nop.rpc.model.ApiModel;
import io.nop.rpc.model.ApiServiceModel;

import java.util.Set;

public class ApiModelToJava {
    private final StringBuilder sb = new StringBuilder();
    private final Set<String> selectedServiceNames;
    private final Set<String> selectedMethodNames;

    public ApiModelToJava(Set<String> selectedServiceNames, Set<String> selectedMethodNames) {
        this.selectedServiceNames = selectedServiceNames;
        this.selectedMethodNames = selectedMethodNames;
    }

    public ApiModelToJava() {
        this(null, null);
    }

    public ApiModelToJava appendApiModel(ApiModel apiModel) {
        for (ApiServiceModel service : apiModel.getServices()) {
            if (!isSelectedService(service))
                continue;
            appendServiceModel(service);
        }

        for (ApiMessageModel message : apiModel.getMessages()) {
            appendMessageModel(message);
        }
        return this;
    }

    public ApiModelToJava appendMessageModel(ApiMessageModel message) {
        sb.append("class ").append(message.getName());
        sb.append("{\n");
        for (ApiMessageFieldModel field : message.getFields()) {
            String name = field.getName();
            String type = StringHelper.simplifyJavaType(field.getType().toString());
            sb.append("    ").append(type).append(" ").append(name).append(";\n");
        }
        sb.append("}\n\n");
        return this;
    }

    public ApiModelToJava appendServiceModel(ApiServiceModel service) {
        sb.append("/** ");
        if (service.getDisplayName() != null) {
            sb.append(service.getDisplayName()).append(": \n");
        }
        if (service.getDescription() != null)
            sb.append(service.getDescription());
        sb.append(" */\n");

        sb.append("interface ").append(service.getName());
        sb.append("{\n");
        for (ApiMethodModel method : service.getMethods()) {
            if (!isSelectedMethod(method))
                continue;
            appendMethodModel(method);
        }
        sb.append("}\n\n");
        return this;
    }

    public ApiModelToJava beginService(ApiServiceModel service) {
        sb.append("/** ");
        if (service.getDisplayName() != null) {
            sb.append(service.getDisplayName()).append(": \n");
        }
        if (service.getDescription() != null)
            sb.append(service.getDescription());
        sb.append(" */\n");

        sb.append("interface ").append(service.getName());
        sb.append("{\n");
        return this;
    }

    public ApiModelToJava endService() {
        sb.append("}\n\n");
        return this;
    }

    public ApiModelToJava appendMethodModel(ApiMethodModel method) {
        sb.append("/** ");
        if (method.getDisplayName() != null) {
            sb.append(method.getDisplayName()).append(": \n");
        }
        if (method.getDescription() != null)
            sb.append(method.getDescription());
        sb.append(" */\n");

        if (method.isMutation())
            sb.append("@BizMutation\n");
        String responseType = StringHelper.simplifyJavaType(method.getResponseMessage().toString());
        String requestType = method.getRequestMessage();
        if (responseType == null)
            responseType = "void";
        sb.append("    ").append(responseType).append(" ").append(method.getName()).append("(@RequestBean ");
        sb.append(requestType).append(" request);\n");
        return this;
    }

    protected boolean isSelectedMethod(ApiMethodModel method) {
        return selectedMethodNames == null || selectedMethodNames.contains(method.getName());
    }

    protected boolean isSelectedService(ApiServiceModel service) {
        return selectedServiceNames == null || selectedServiceNames.contains(service.getName());
    }

    public String toString() {
        return sb.toString();
    }
}
