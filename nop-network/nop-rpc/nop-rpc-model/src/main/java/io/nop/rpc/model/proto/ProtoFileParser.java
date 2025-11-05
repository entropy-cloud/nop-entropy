/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.rpc.model.proto;

import io.nop.api.core.beans.DictBean;
import io.nop.api.core.beans.DictOptionBean;
import io.nop.api.core.util.SourceLocation;
import io.nop.commons.text.tokenizer.TextScanner;
import io.nop.commons.type.BinaryScalarType;
import io.nop.commons.type.StdDataType;
import io.nop.commons.util.StringHelper;
import io.nop.core.lang.json.parse.JsonParser;
import io.nop.core.resource.component.parse.AbstractTextResourceParser;
import io.nop.core.type.IGenericType;
import io.nop.core.type.utils.JavaGenericTypeBuilder;
import io.nop.rpc.model.ApiImportModel;
import io.nop.rpc.model.ApiMessageFieldModel;
import io.nop.rpc.model.ApiMessageModel;
import io.nop.rpc.model.ApiMethodModel;
import io.nop.rpc.model.ApiModel;
import io.nop.rpc.model.ApiOptionModel;
import io.nop.rpc.model.ApiServiceModel;
import io.nop.rpc.model.RpcModelConstants;

import static io.nop.rpc.model.RpcModelErrors.ARG_VERSION;
import static io.nop.rpc.model.RpcModelErrors.ERR_RPC_INVALID_IMPORT_PATH;
import static io.nop.rpc.model.RpcModelErrors.ERR_RPC_UNSUPPORTED_PROTO_VERSION;

public class ProtoFileParser extends AbstractTextResourceParser<ApiModel> {
    @Override
    protected ApiModel doParseText(SourceLocation loc, String text) {
        TextScanner sc = TextScanner.fromString(loc, text);

        ApiModel model = new ApiModel();
        String protoVersion = parseSyntax(sc);
        model.prop_set(RpcModelConstants.EXT_PROTO_VERSION, protoVersion);

        parseOptions(sc, model);

        String packageName = parsePackage(sc);
        model.setApiPackageName(packageName);

        parseModel(sc, model);
        sc.checkEnd();
        return model;
    }

    private String parseSyntax(TextScanner sc) {
        sc.skipBlank();
        sc.matchToken("syntax");
        sc.match('=');
        String proto = sc.nextJavaString();
        if (!"proto3".equals(proto))
            throw sc.newError(ERR_RPC_UNSUPPORTED_PROTO_VERSION)
                    .param(ARG_VERSION, proto);
        sc.match(';');
        return proto;
    }

    private void parseOptions(TextScanner sc, ApiModel model) {
        sc.skipBlank();
        String desc = parseDocumentation(sc);

        while (sc.tryMatchToken("option")) {
            ApiOptionModel optionModel = parseOptionRest(sc);
            optionModel.setDescription(desc);
            model.addOption(optionModel);
            desc = parseDocumentation(sc);
        }

        model.setDescription(desc);
    }

    private String parsePackage(TextScanner sc) {
        sc.matchToken("package");
        String packageName = sc.nextWordPath();
        sc.match(';');
        return packageName;
    }

    private void parseModel(TextScanner sc, ApiModel model) {
        while (!sc.isEnd()) {
            String desc = parseDocumentation(sc);
            if (sc.tryMatchToken("import")) {
                ApiImportModel importModel = parseImportRest(sc);
                model.addImport(importModel);
            } else if (sc.tryMatchToken("service")) {
                ApiServiceModel serviceModel = parseServiceRest(sc);
                serviceModel.setDescription(desc);
                model.addService(serviceModel);
            } else if (sc.tryMatchToken("message")) {
                ApiMessageModel messageModel = parseMessageRest(sc);
                messageModel.setDescription(desc);
                model.addMessage(messageModel);
            } else if (sc.tryMatchToken("enum")) {
                DictBean dict = parseEnumRest(sc);
                dict.setDescription(desc);
                model.addDict(dict);
            } else if (sc.tryMatchToken("option")) {
                ApiOptionModel optionModel = parseOptionRest(sc);
                model.addOption(optionModel);
            } else {
                throw sc.newUnexpectedError();
            }
        }
    }

    private ApiImportModel parseImportRest(TextScanner sc) {
        ApiImportModel importModel = new ApiImportModel();
        String path = sc.nextJavaString();
        if (!StringHelper.isValidFilePath(path))
            throw sc.newError(ERR_RPC_INVALID_IMPORT_PATH);
        importModel.setFrom(path);
        sc.skipBlank();
        sc.match(';');
        return importModel;
    }

    private ApiOptionModel parseOptionRest(TextScanner sc) {
        ApiOptionModel optionModel = new ApiOptionModel();
        optionModel.setName(sc.nextWord());
        sc.skipBlank();
        sc.match('=');
        Object value = new JsonParser().parseJsonDoc(sc);
        optionModel.setValue(value);
        sc.skipBlank();
        sc.match(';');
        return optionModel;
    }

    private DictBean parseEnumRest(TextScanner sc) {
        DictBean enumBean = new DictBean();
        enumBean.setValueType(StdDataType.INT.getName());
        enumBean.setName(sc.nextWord());

        sc.skipBlank();
        sc.match('{');
        while (!sc.isEnd()) {
            if (sc.cur == '}')
                break;
            String label = sc.nextWord();
            sc.match('=');
            int value = sc.nextInt();
            DictOptionBean item = new DictOptionBean();
            item.setLabel(label);
            item.setValue(value);
            sc.match(';');
        }
        sc.match('}');
        return enumBean;
    }

    private ApiMessageModel parseMessageRest(TextScanner sc) {
        ApiMessageModel messageModel = new ApiMessageModel();
        messageModel.setName(sc.nextWord());
        sc.skipBlank();

        sc.match('{');
        while (!sc.isEnd()) {
            if (sc.cur == '}')
                break;

            if (sc.tryMatchToken("option")) {
                messageModel.addOption(parseOptionRest(sc));
            } else {
                ApiMessageFieldModel fieldModel = parseField(sc);
                messageModel.addField(fieldModel);
            }
        }
        sc.match('}');

        return messageModel;
    }

    private ApiMessageFieldModel parseField(TextScanner sc) {
        ApiMessageFieldModel fieldModel = new ApiMessageFieldModel();
        boolean repeated = false;
        if (sc.tryMatchToken("optional")) {
            fieldModel.setMandatory(false);
        } else if (sc.tryMatchToken("repeated")) {
            repeated = true;
            fieldModel.setMandatory(false);
        } else {
            fieldModel.setMandatory(true);
        }

        ProtoDataType dataType = parseDataType(sc);
        if (dataType.getScalarType() != null) {
            fieldModel.setBinaryScalarType(dataType.getScalarType());
        }
        IGenericType type = dataType.toGenericType(fieldModel.isMandatory());
        if (repeated) {
            type = JavaGenericTypeBuilder.buildListType(type);
        }

        fieldModel.setType(type);

        fieldModel.setName(sc.nextWord());
        sc.skipBlank();
        sc.match('=');
        fieldModel.setPropId(sc.nextInt());
        sc.skipBlank();
        sc.match(';');
        return fieldModel;
    }

    private ApiServiceModel parseServiceRest(TextScanner sc) {
        ApiServiceModel serviceModel = new ApiServiceModel();
        serviceModel.setName(sc.nextWord());
        sc.skipBlank();
        sc.match('{');

        while (!sc.isEnd()) {
            if (sc.cur == '}') {
                break;
            }

            if (sc.tryMatchToken("option")) {
                serviceModel.addOption(parseOptionRest(sc));
            } else {
                ApiMethodModel methodModel = parseMethod(sc);
                serviceModel.addMethod(methodModel);
            }
        }

        sc.match('}');

        return serviceModel;
    }

    private ApiMethodModel parseMethod(TextScanner sc) {
        ApiMethodModel ret = new ApiMethodModel();
        sc.matchToken("rpc");
        ret.setName(sc.nextWord());
        sc.skipBlank();
        sc.match('(');
        String requestMessage = sc.nextWordPath();
        sc.skipBlank();
        ret.setRequestMessage(requestMessage);
        sc.match(')');
        sc.matchToken("returns");
        sc.match('(');

        IGenericType responseMessage = parseDataType(sc).toGenericType(false);
        sc.skipBlank();
        ret.setResponseMessage(responseMessage);

        sc.match(')');
        sc.match(';');
        return ret;
    }

    private String parseDocumentation(TextScanner sc) {
        String result = null;
        while (true) {
            sc.skipBlank();
            if (sc.cur != '/') {
                return result;
            }
            long pos = sc.cur;
            String comment = sc.skipJavaComment(true);
            if (sc.cur == pos)
                return result;
            result = (result == null) ? comment : (result + "\n" + comment);
        }
    }

    private ProtoDataType parseDataType(TextScanner sc) {
        String name = sc.nextWordPath();
        sc.skipBlank();
        if (name.equals("map")) {
            sc.match('<');
            sc.matchToken("string");
            sc.match(',');
            ProtoDataType valueType = parseDataType(sc);
            return ProtoDataType.makeMapType(valueType);
        }

        BinaryScalarType scalarType = BinaryScalarType.fromText(name);
        if (scalarType != null)
            return ProtoDataType.makeScalarType(scalarType);

        return ProtoDataType.makeNamedType(name);
    }
}
