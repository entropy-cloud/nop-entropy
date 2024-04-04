/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.xlang.xdef.domain;

import io.nop.api.core.annotations.core.GlobalInstance;
import io.nop.commons.type.StdDataType;
import io.nop.xlang.xdef.IStdDomainHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@GlobalInstance
public class StdDomainRegistry {
    static final StdDomainRegistry _instance = new StdDomainRegistry();

    public static StdDomainRegistry instance() {
        return _instance;
    }

    private Map<String, IStdDomainHandler> domainHandlers = new ConcurrentHashMap<>();

    public StdDomainRegistry() {
        registerDefaults();
    }

    public IStdDomainHandler getStdDomainHandler(String type) {
        return domainHandlers.get(type);
    }

    public void registerStdDomainHandler(IStdDomainHandler handler) {
        domainHandlers.put(handler.getName(), handler);
    }

    public void unregisterStdDomainHandler(IStdDomainHandler handler) {
        domainHandlers.remove(handler.getName(), handler);
    }

    private void registerDefaults() {
        registerStdDomainHandler(new SimpleStdDomainHandlers.VPathType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.XDefRefType());

        registerStdDomainHandler(new SimpleStdDomainHandlers.NopModuleIdType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.NopModuleNameType());

        registerStdDomainHandler(new SimpleStdDomainHandlers.PropNameType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.XmlNameType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.BeanNameType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.VarNameType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.JavaNameType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.PropPathType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.ClassNameType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.PackageNameType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.ConfNameType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.TokenNameType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.PropNameSetType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.WordSetType());

        registerStdDomainHandler(new SimpleStdDomainHandlers.FileTypeType());

        registerStdDomainHandler(new SimpleStdDomainHandlers.GenericTypeType());

        registerStdDomainHandler(new SimpleStdDomainHandlers.JsonType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.XmlType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.XmlBodyType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.XJsonNodeType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.FilterBeanType());

        registerStdDomainHandler(new SimpleStdDomainHandlers.FlagsExprType());

        registerStdDomainHandler(new SimpleStdDomainHandlers.FileType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.FileListType());

        registerStdDomainHandler(new XJsonListDomainHandler());
        registerStdDomainHandler(new SimpleStdDomainHandlers.BooleanOrNumberType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.IntOrStringType());

        registerStdDomainHandler(new SimpleStdDomainHandlers.CsvSetType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.CsvListType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.ClassSetType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.TagSetType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.MultiCsvSetType());

        registerStdDomainHandler(new SimpleStdDomainHandlers.NsNameType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.GenericTypeListType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.NumberType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.SqlObjType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.StdType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.StdDomainType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.StringMapType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.SlotScopeType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.DefTypeType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.VPathListType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.RegexType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.FieldSelectionType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.StdSqlTypeType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.StdSqlTypeListType());

        registerStdDomainHandler(new SimpleStdDomainHandlers.IntSizeType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.LongSizeType());

        registerStdDomainHandler(new SimpleStdDomainHandlers.BoolFlagType());

        registerStdDomainHandler(new SimpleStdDomainHandlers.IntListType());

        registerStdDomainHandler(new SimpleStdDomainHandlers.Base64BytesType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.EncodedBytesType());

        registerStdDomainHandler(new SimpleStdDomainHandlers.CellPosType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.CellRangeType());

        registerStdDomainHandler(new SimpleStdDomainHandlers.SqlOrderByType());

        registerStdDomainHandler(new SimpleStdDomainHandlers.BooleanOrNumberType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.BooleanOrStringType());

        registerStdDomainHandler(new SimpleStdDomainHandlers.IntRangeType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.LongRangeType());

        for (int i = 0; i <= StdDataType.DURATION.ordinal(); i++) {
            StdDataType type = StdDataType.values()[i];
            registerStdDomainHandler(ConverterStdDomainHandler.stdTypeHandler(type));
        }

        registerStdDomainHandler(XplStdDomainHandlers.XPL_TYPE);
        registerStdDomainHandler(XplStdDomainHandlers.XPL_PREDICATE_TYPE);
        registerStdDomainHandler(XplStdDomainHandlers.XPL_TEXT_TYPE);
        registerStdDomainHandler(XplStdDomainHandlers.XPL_XML_TYPE);
        registerStdDomainHandler(XplStdDomainHandlers.XPL_HTML_TYPE);
        registerStdDomainHandler(XplStdDomainHandlers.XPL_NODE_TYPE);
        registerStdDomainHandler(XplStdDomainHandlers.XPL_SQL_TYPE);
        registerStdDomainHandler(XplStdDomainHandlers.XPL_XJSON_TYPE);
        registerStdDomainHandler(XplStdDomainHandlers.XPL_FN_TYPE);

        registerStdDomainHandler(XplStdDomainHandlers.EVAL_CODE_TYPE);

        registerStdDomainHandler(new XplStdDomainHandlers.MockReportExprType());

        registerStdDomainHandler(new XplStdDomainHandlers.ExprType());
        registerStdDomainHandler(new XplStdDomainHandlers.SingleExprType());
        registerStdDomainHandler(new XplStdDomainHandlers.TplExprType());

        registerStdDomainHandler(new XplStdDomainHandlers.XtExprType());
        registerStdDomainHandler(new XplStdDomainHandlers.XtValueType());

        registerStdDomainHandler(new SimpleStdDomainHandlers.JPathType());
        registerStdDomainHandler(new SimpleStdDomainHandlers.XPathType());

        registerStdDomainHandler(new SimpleStdDomainHandlers.XdefAttrType());

        registerStdDomainHandler(new EnumStdDomainHandler());

        registerStdDomainHandler(new XJsonDomainHandler());

        registerStdDomainHandler(new SimpleStdDomainHandlers.FormLayoutType());
    }
}
