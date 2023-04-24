/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.xlang.xdef.domain;

import io.nop.api.core.beans.FieldSelectionBean;
import io.nop.api.core.beans.query.OrderFieldBean;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;
import io.nop.api.core.json.JsonParseOptions;
import io.nop.api.core.util.SourceLocation;
import io.nop.api.core.validate.IValidationErrorCollector;
import io.nop.commons.bytes.ByteString;
import io.nop.commons.text.MutableString;
import io.nop.commons.text.regex.RegexHelper;
import io.nop.commons.type.StdDataType;
import io.nop.commons.util.StringHelper;
import io.nop.core.CoreConstants;
import io.nop.core.lang.json.IJsonContainer;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.json.jpath.JPath;
import io.nop.core.lang.sql.StdSqlType;
import io.nop.core.lang.xml.IXSelector;
import io.nop.core.lang.xml.XJsonNode;
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.XPathProvider;
import io.nop.core.lang.xml.parse.XNodeParser;
import io.nop.core.model.query.QueryBeanHelper;
import io.nop.core.model.selection.FieldSelectionBeanParser;
import io.nop.core.model.table.CellPosition;
import io.nop.core.model.table.CellRange;
import io.nop.core.reflect.ReflectionManager;
import io.nop.core.reflect.impl.SafeRawTypeResolver;
import io.nop.core.type.IGenericType;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.core.type.parse.GenericTypeParser;
import io.nop.core.type.utils.GenericTypeHelper;
import io.nop.core.type.utils.TypeReference;
import io.nop.xlang.api.XLangCompileTool;
import io.nop.xlang.xdef.IStdDomainHandler;
import io.nop.xlang.xdef.IStdDomainOptions;
import io.nop.xlang.xdef.XDefConstants;
import io.nop.xlang.xdef.XDefTypeDecl;
import io.nop.xlang.xdef.impl.XDefAttribute;
import io.nop.xlang.xdsl.XDslParseHelper;
import io.nop.xlang.xmeta.layout.LayoutModel;
import io.nop.xlang.xmeta.layout.parse.LayoutModelParser;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static io.nop.core.type.PredefinedGenericTypes.I_GENERIC_TYPE_TYPE;
import static io.nop.core.type.PredefinedGenericTypes.X_NODE_TYPE;
import static io.nop.xlang.XLangErrors.ARG_ALLOWED_NAMES;
import static io.nop.xlang.XLangErrors.ARG_ITEM_VALUE;
import static io.nop.xlang.XLangErrors.ARG_PROP_NAME;
import static io.nop.xlang.XLangErrors.ARG_STD_DOMAIN;
import static io.nop.xlang.XLangErrors.ARG_VALUE;
import static io.nop.xlang.XLangErrors.ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN;
import static io.nop.xlang.XLangErrors.ERR_XDEF_STD_DOMAIN_NOT_SUPPORT_PROP;

public class SimpleStdDomainHandlers {
    public static class VPathType extends StringStdDomainHandler {
        public String getName() {
            return XDefConstants.STD_DOMAIN_V_PATH;
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object value,
                                XLangCompileTool cp) {
            String text = value.toString();
            if (!StringHelper.isValidVPath(text))
                throw new NopException(ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN).loc(loc)
                        .param(ARG_STD_DOMAIN, getName()).param(ARG_VALUE, text).param(ARG_PROP_NAME, propName);

            String resourcePath = loc.getPath();
            return StringHelper.absolutePath(resourcePath, text);
        }
    }

    public static class VPathListType extends SimpleStdDomainHandler {
        public String getName() {
            return XDefConstants.STD_DOMAIN_V_PATH_LIST;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            return PredefinedGenericTypes.LIST_STRING_TYPE;
        }

        @Override
        public boolean isFixedType() {
            return true;
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object value,
                                XLangCompileTool cp) {
            List<String> list = ConvertHelper.toCsvList(value, NopException::new);
            if (list == null || list.isEmpty())
                return null;

            List<String> ret = new ArrayList<>(list.size());
            for (String path : list) {
                if (!StringHelper.isValidVPath(path))
                    throw new NopException(ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN).loc(loc)
                            .param(ARG_STD_DOMAIN, getName()).param(ARG_VALUE, path).param(ARG_PROP_NAME, propName);

                String resourcePath = loc.getPath();
                ret.add(StringHelper.absolutePath(resourcePath, path));
            }
            return ret;
        }
    }

    public static class IntListType extends SimpleStdDomainHandler {
        public String getName() {
            return XDefConstants.STD_DOMAIN_INT_LIST;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            return PredefinedGenericTypes.LIST_INT_TYPE;
        }

        @Override
        public boolean isFixedType() {
            return true;
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object value,
                                XLangCompileTool cp) {
            List<String> list = ConvertHelper.toCsvList(value, NopException::new);
            if (list == null || list.isEmpty())
                return null;

            List<Integer> ret = new ArrayList<>(list.size());
            for (String part : list) {
                Integer intValue = ConvertHelper.toInt(part,
                        err -> new NopException(ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN).loc(loc)
                                .param(ARG_STD_DOMAIN, getName()).param(ARG_VALUE, part)
                                .param(ARG_PROP_NAME, propName));

                ret.add(intValue);
            }
            return ret;
        }
    }

    public static class Base64BytesType extends SimpleStdDomainHandler {
        public String getName() {
            return XDefConstants.STD_DOMAIN_BASE64_BYTES;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            return PredefinedGenericTypes.BYTES_TYPE;
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object value,
                                XLangCompileTool cp) {
            if (value instanceof ByteString)
                return value;
            return ByteString.decodeBase64((String) value);
        }

        public String serialize(Object value) {
            if (value == null)
                return null;
            ByteString bs = (ByteString) value;
            return bs.base64();
        }
    }

    public static class EncodedBytesType extends SimpleStdDomainHandler {
        public String getName() {
            return XDefConstants.STD_DOMAIN_ENCODED_BYTES;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            return PredefinedGenericTypes.BYTES_TYPE;
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object value,
                                XLangCompileTool cp) {
            if (value instanceof ByteString)
                return value;
            return ByteString.parseEncodedString((String) value);
        }

        public String serialize(Object value) {
            if (value == null)
                return null;
            ByteString bs = (ByteString) value;
            return bs.toEncodedHex();
        }
    }

    public static class ClassNameType extends CheckStdDomainHandler {
        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_CLASS_NAME;
        }

        @Override
        protected boolean isValid(String text) {
            return StringHelper.isValidClassName(text);
        }
    }

    public static class BeanNameType extends CheckStdDomainHandler {
        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_BEAN_NAME;
        }

        @Override
        protected boolean isValid(String text) {
            // 系统自动生成的名称
            if (text.startsWith("$"))
                return true;
            return StringHelper.isValidClassName(text);
        }
    }

    public static class PackageNameType extends CheckStdDomainHandler {
        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_PACKAGE_NAME;
        }

        @Override
        protected boolean isValid(String text) {
            return StringHelper.isValidClassName(text);
        }
    }

    public static class XmlNameType extends CheckStdDomainHandler {
        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_XML_NAME;
        }

        @Override
        protected boolean isValid(String text) {
            return StringHelper.isValidXmlName(text);
        }
    }

    public static class NsNameType extends CheckStdDomainHandler {
        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_NS_NAME;
        }

        @Override
        protected boolean isValid(String text) {
            return StringHelper.isValidXmlNamespaceName(text);
        }
    }

    public static class VarNameType extends CheckStdDomainHandler {
        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_VAR_NAME;
        }

        @Override
        protected boolean isValid(String text) {
            return StringHelper.isValidSimpleVarName(text);
        }
    }

    public static class JavaNameType extends CheckStdDomainHandler {
        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_JAVA_NAME;
        }

        @Override
        protected boolean isValid(String text) {
            return StringHelper.isValidJavaVarName(text);
        }
    }

    public static class PropNameType extends CheckStdDomainHandler {
        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_PROP_NAME;
        }

        @Override
        protected boolean isValid(String text) {
            return StringHelper.isValidPropName(text);
        }
    }

    public static class FileTypeType extends CheckStdDomainHandler {
        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_FILE_TYPE;
        }

        @Override
        protected boolean isValid(String text) {
            return StringHelper.isValidFileType(text);
        }
    }

    public static abstract class AbstractStringSetType extends SimpleStdDomainHandler {
        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            return PredefinedGenericTypes.SET_STRING_TYPE;
        }

        @Override
        public boolean isFixedType() {
            return true;
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object value,
                                XLangCompileTool cp) {
            Set<String> list = ConvertHelper.toCsvSet(value, NopException::new);
            if (list == null || list.isEmpty())
                return null;

            Set<String> ret = new LinkedHashSet<>(list.size());
            for (String item : list) {
                if (!isValidItem(item))
                    throw new NopException(ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN).loc(loc)
                            .param(ARG_STD_DOMAIN, getName()).param(ARG_VALUE, item).param(ARG_PROP_NAME, propName);

                ret.add(item);
            }
            return ret;
        }

        protected abstract boolean isValidItem(String item);
    }

    public static class PropNameSetType extends AbstractStringSetType {
        public String getName() {
            return XDefConstants.STD_DOMAIN_PROP_NAME_SET;
        }

        @Override
        protected boolean isValidItem(String item) {
            return StringHelper.isValidPropName(item);
        }
    }

    public static class WordSetType extends AbstractStringSetType {
        public String getName() {
            return XDefConstants.STD_DOMAIN_WORD_SET;
        }

        @Override
        protected boolean isValidItem(String item) {
            return !StringHelper.isEmpty(item) && !StringHelper.containsWhitespace(item);
        }
    }

    public static class PropPathType extends CheckStdDomainHandler {
        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_PROP_PATH;
        }

        @Override
        protected boolean isValid(String text) {
            return StringHelper.isValidPropPath(text);
        }
    }

    public static class ConfNameType extends CheckStdDomainHandler {
        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_CONF_NAME;
        }

        @Override
        protected boolean isValid(String text) {
            return StringHelper.isValidConfigVar(text);
        }
    }

    public static class RegexType extends CheckStdDomainHandler {
        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_REGEX;
        }

        @Override
        protected boolean isValid(String text) {
            try {
                RegexHelper.compileRegex(text);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    public static class JsonType extends SimpleStdDomainHandler {
        @Override
        public boolean isFixedType() {
            return true;
        }

        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_JSON;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            return PredefinedGenericTypes.ANY_TYPE;
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object text,
                                XLangCompileTool cp) {
            if (text instanceof IJsonContainer)
                return text;

            JsonParseOptions opts = new JsonParseOptions();
            opts.setKeepLocation(true);
            try {
                return JsonTool.instance().parseFromText(loc, text.toString(), opts);
            } catch (Exception e) {
                throw new NopException(ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN).loc(loc)
                        .param(ARG_STD_DOMAIN, getName()).param(ARG_PROP_NAME, propName).param(ARG_VALUE, text);
            }
        }
    }

    public static class XmlType extends SimpleStdDomainHandler {
        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_XML;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            return X_NODE_TYPE;
        }

        @Override
        public boolean supportXmlChild() {
            return true;
        }

        @Override
        public boolean isFullXmlNode() {
            return true;
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object text,
                                XLangCompileTool cp) {
            throw new NopException(ERR_XDEF_STD_DOMAIN_NOT_SUPPORT_PROP).loc(loc).param(ARG_STD_DOMAIN, getName())
                    .param(ARG_PROP_NAME, propName);
        }

        @Override
        public Object parseXmlChild(IStdDomainOptions options, XNode body, XLangCompileTool cp) {
            XNode node = body.cloneInstance();
            node.setTagName(CoreConstants.DUMMY_TAG_NAME);
            // node.clearAttrs();
            node.freeze(true);
            return node;
        }
    }

    public static class XmlBodyType extends XmlType {
        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_XML_BODY;
        }

        @Override
        public boolean isFullXmlNode() {
            return false;
        }

        @Override
        public Object parseXmlChild(IStdDomainOptions options, XNode body, XLangCompileTool cp) {
            XNode node = body.cloneInstance();
            node.setTagName(CoreConstants.DUMMY_TAG_NAME);
            node.clearAttrs();
            node.freeze(true);
            return node;
        }
    }

    public static class XJsonNodeType extends SimpleStdDomainHandler {
        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_XJSON_NODE;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            return ReflectionManager.instance().buildRawType(XJsonNode.class);
        }

        @Override
        public boolean supportXmlChild() {
            return true;
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object text,
                                XLangCompileTool cp) {
            XNode node;
            if (text instanceof XNode) {
                node = ((XNode) text).cloneInstance();
            } else {
                try {
                    node = XNodeParser.instance().parseFromText(loc, text.toString());
                } catch (Exception e) {
                    throw new NopException(ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN).loc(loc)
                            .param(ARG_STD_DOMAIN, getName()).param(ARG_PROP_NAME, propName).param(ARG_VALUE, text);
                }
            }
            node.setTagName(CoreConstants.DUMMY_TAG_NAME);
            node.freeze(true);
            return new XJsonNode(node);
        }

        @Override
        public Object parseXmlChild(IStdDomainOptions options, XNode body, XLangCompileTool cp) {
            XNode node = body.cloneInstance();
            node.setTagName(CoreConstants.DUMMY_TAG_NAME);
            node.freeze(true);
            return new XJsonNode(node);
        }
    }

    public static class IntSizeType extends SimpleStdDomainHandler {
        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_INT_SIZE;
        }

        @Override
        public boolean isFixedType() {
            return true;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            return PredefinedGenericTypes.INT_TYPE;
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object text,
                                XLangCompileTool cp) {
            if (StringHelper.isEmptyObject(text))
                return null;

            if (text instanceof Number) {
                int value = ((Number) text).intValue();
                if (value < 0)
                    throw new NopException(ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN).loc(loc).param(ARG_PROP_NAME, propName)
                            .param(ARG_STD_DOMAIN, getName()).param(ARG_VALUE, text);
            }

            long value = StringHelper.parseSize(text.toString());
            if (value < 0 || value > Integer.MAX_VALUE)
                throw new NopException(ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN).loc(loc).param(ARG_PROP_NAME, propName)
                        .param(ARG_STD_DOMAIN, getName()).param(ARG_VALUE, text);
            return (int) value;
        }
    }

    public static class LongSizeType extends SimpleStdDomainHandler {
        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_LONG_SIZE;
        }

        @Override
        public boolean isFixedType() {
            return true;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            return PredefinedGenericTypes.LONG_TYPE;
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object text,
                                XLangCompileTool cp) {
            if (StringHelper.isEmptyObject(text))
                return null;
            long value;
            if (text instanceof Number) {
                value = ((Number) text).longValue();
            } else {
                value = StringHelper.parseSize(text.toString());
            }
            if (value < 0)
                throw new NopException(ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN).loc(loc).param(ARG_PROP_NAME, propName)
                        .param(ARG_STD_DOMAIN, getName()).param(ARG_VALUE, text);
            return value;
        }
    }

    public static class CsvSetType extends SimpleStdDomainHandler {
        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_CSV_SET;
        }

        @Override
        public boolean isFixedType() {
            return true;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            return PredefinedGenericTypes.SET_STRING_TYPE;
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object text,
                                XLangCompileTool cp) {
            return ConvertHelper.toCsvSet(text, NopException::new);
        }
    }

    public static class CsvListType extends SimpleStdDomainHandler {
        @Override
        public boolean isFixedType() {
            return true;
        }

        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_CSV_LIST;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            return PredefinedGenericTypes.LIST_STRING_TYPE;
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object text,
                                XLangCompileTool cp) {
            return ConvertHelper.toCsvList(text, NopException::new);
        }
    }

    public static class TagSetType extends SimpleStdDomainHandler {
        @Override
        public boolean isFixedType() {
            return true;
        }

        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_TAG_SET;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            return PredefinedGenericTypes.SET_STRING_TYPE;
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object text,
                                XLangCompileTool cp) {
            return ConvertHelper.toCsvSet(text, NopException::new);
        }
    }

    public static class ClassSetType extends SimpleStdDomainHandler {
        @Override
        public boolean isFixedType() {
            return true;
        }

        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_CLASS_NAME_SET;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            return PredefinedGenericTypes.SET_STRING_TYPE;
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object text,
                                XLangCompileTool cp) {
            Set<String> ret = ConvertHelper.toCsvSet(text, NopException::new);
            if (ret != null) {
                for (String str : ret) {
                    if (!StringHelper.isValidClassName(str)) {
                        throw new NopException(ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN).loc(loc)
                                .param(ARG_STD_DOMAIN, this.getName()).param(ARG_PROP_NAME, propName)
                                .param(ARG_VALUE, text);
                    }
                }
            }
            return ret;
        }
    }

    public static class GenericTypeType implements IStdDomainHandler {
        @Override
        public boolean isFixedType() {
            return true;
        }

        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_GENERIC_TYPE;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            return I_GENERIC_TYPE_TYPE;
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object text,
                                XLangCompileTool cp) {
            // tell cpd to start ignoring code - CPD-OFF
            if (text instanceof IGenericType)
                return text;

            try {
                return new GenericTypeParser().parseFromText(loc, text.toString());
            } catch (Exception e) {
                throw new NopException(ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN, e).loc(loc)
                        .param(ARG_STD_DOMAIN, this.getName()).param(ARG_PROP_NAME, propName).param(ARG_VALUE, text);
            }
            // resume CPD analysis - CPD-ON
        }

        @Override
        public void validate(SourceLocation loc, String propName, Object value, IValidationErrorCollector collector) {
            if (value instanceof IGenericType)
                return;

            String text = value.toString();
            try {
                new GenericTypeParser().rawTypeResolver(SafeRawTypeResolver.INSTANCE).parseFromText(loc, text);
            } catch (Exception e) {
                collector.addException(new NopException(ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN, e).loc(loc)
                        .param(ARG_STD_DOMAIN, this.getName()).param(ARG_PROP_NAME, propName).param(ARG_VALUE, text));
            }
        }
    }

    public static class GenericTypeListType implements IStdDomainHandler {
        @Override
        public boolean isFixedType() {
            return true;
        }

        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_GENERIC_TYPE_LIST;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            return GenericTypeHelper.buildListType(I_GENERIC_TYPE_TYPE);
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object text,
                                XLangCompileTool cp) {
            if (text instanceof List)
                return text;

            try {
                return new GenericTypeParser().parseGenericTypeList(loc, text.toString());
            } catch (Exception e) {
                throw new NopException(ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN, e).loc(loc)
                        .param(ARG_STD_DOMAIN, this.getName()).param(ARG_PROP_NAME, propName).param(ARG_VALUE, text);
            }
        }

        @Override
        public void validate(SourceLocation loc, String propName, Object value, IValidationErrorCollector collector) {
            if (value instanceof IGenericType)
                return;

            String text = value.toString();
            try {
                new GenericTypeParser().rawTypeResolver(SafeRawTypeResolver.INSTANCE).parseGenericTypeList(loc, text);
            } catch (Exception e) {
                collector.addException(new NopException(ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN, e).loc(loc)
                        .param(ARG_STD_DOMAIN, this.getName()).param(ARG_PROP_NAME, propName).param(ARG_VALUE, text));
            }
        }
    }

    public static class NumberType extends SimpleStdDomainHandler {
        @Override
        public boolean isFixedType() {
            return true;
        }

        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_NUMBER;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            return PredefinedGenericTypes.NUMBER_TYPE;
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object text,
                                XLangCompileTool cp) {
            return ConvertHelper.toNumber(text,
                    err -> new NopException(ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN).loc(loc)
                            .param(ARG_STD_DOMAIN, this.getName()).param(ARG_PROP_NAME, propName)
                            .param(ARG_VALUE, text));

        }
    }

    public static class BoolFlagType extends SimpleStdDomainHandler {
        @Override
        public boolean isFixedType() {
            return true;
        }

        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_BOOL_FLAG;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            return PredefinedGenericTypes.BYTE_TYPE;
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object text,
                                XLangCompileTool cp) {
            return ConvertHelper.toByte(text,
                    err -> new NopException(ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN).loc(loc)
                            .param(ARG_STD_DOMAIN, this.getName()).param(ARG_PROP_NAME, propName)
                            .param(ARG_VALUE, text));

        }
    }

    public static class SqlObjType extends SimpleStdDomainHandler {

        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_SQL_OBJ;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            return PredefinedGenericTypes.ANY_TYPE;
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object text,
                                XLangCompileTool cp) {
            if (!(text instanceof MutableString))
                throw new NopException(ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN).loc(loc)
                        .param(ARG_STD_DOMAIN, this.getName()).param(ARG_PROP_NAME, propName).param(ARG_VALUE, text);
            return text;
        }
    }

    public static class StdType extends SimpleStdDomainHandler {
        @Override
        public boolean isFixedType() {
            return true;
        }

        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_STD_DATA_TYPE;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            return PredefinedGenericTypes.STD_DATA_TYPE_TYPE;
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object text,
                                XLangCompileTool cp) {
            if (text instanceof StdDataType)
                return text;
            StdDataType type = StdDataType.fromStdName((String) text);
            if (type == null) {
                throw new NopException(ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN).loc(loc)
                        .param(ARG_STD_DOMAIN, this.getName()).param(ARG_ALLOWED_NAMES, StdDataType.getNames())
                        .param(ARG_PROP_NAME, propName).param(ARG_VALUE, text);
            }
            return type;
        }
    }

    public static class StdDomainType extends CheckStdDomainHandler {

        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_STD_DOMAIN;
        }

        @Override
        protected boolean isValid(String text) {
            return StdDomainRegistry.instance().getStdDomainHandler(text) != null;
        }
    }

    public static class StringMapType extends SimpleStdDomainHandler {
        @Override
        public boolean isFixedType() {
            return true;
        }

        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_STRING_MAP;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            return PredefinedGenericTypes.MAP_STRING_STRING_TYPE;
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object text,
                                XLangCompileTool cp) {
            return StringHelper.parseStringMap(text.toString(), '=', ',');
        }
    }

    public static class SlotScopeType extends SimpleStdDomainHandler {
        @Override
        public boolean isFixedType() {
            return true;
        }

        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_SLOT_SCOPE;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            return PredefinedGenericTypes.MAP_STRING_STRING_TYPE;
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object text,
                                XLangCompileTool cp) {
            return StringHelper.parseSlotScope(text.toString());
        }
    }

    public static class DefTypeType extends SimpleStdDomainHandler {
        @Override
        public boolean isFixedType() {
            return true;
        }

        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_DEF_TYPE;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            return ReflectionManager.instance().buildGenericType(XDefTypeDecl.class);
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object text,
                                XLangCompileTool cp) {
            return XDslParseHelper.parseDefType(loc, propName, (String) text);
        }
    }

    public static class FieldSelectionType extends SimpleStdDomainHandler {
        @Override
        public boolean isFixedType() {
            return false;
        }

        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_FIELD_SELECTION;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            return ReflectionManager.instance().buildGenericType(FieldSelectionBean.class);
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object text,
                                XLangCompileTool cp) {
            try {
                return new FieldSelectionBeanParser().parseFromText(loc, text.toString());
            } catch (NopException e) {
                e.param(ARG_PROP_NAME, propName);
                throw e;
            }
        }
    }

    public static class BooleanOrNumberType extends SimpleStdDomainHandler {
        @Override
        public boolean isFixedType() {
            return false;
        }

        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_BOOLEAN_OR_NUMBER;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            return PredefinedGenericTypes.ANY_TYPE;
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object text,
                                XLangCompileTool cp) {
            if (text == null || text instanceof Number || text instanceof Boolean) {
                return text;
            }

            String value = text.toString();
            if ("true".equals(value))
                return true;
            if ("false".equals(value))
                return false;

            return ConvertHelper.toNumber(text,
                    err -> new NopException(ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN).loc(loc)
                            .param(ARG_STD_DOMAIN, this.getName()).param(ARG_PROP_NAME, propName)
                            .param(ARG_VALUE, text));
        }
    }

    public static class BooleanOrStringType extends SimpleStdDomainHandler {
        @Override
        public boolean isFixedType() {
            return false;
        }

        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_BOOLEAN_OR_STRING;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            return PredefinedGenericTypes.ANY_TYPE;
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object text,
                                XLangCompileTool cp) {
            if (text == null || text instanceof Boolean) {
                return text;
            }

            String value = text.toString();
            if ("true".equals(value))
                return true;
            if ("false".equals(value))
                return false;

            return value;
        }
    }

    public static class IntOrStringType extends SimpleStdDomainHandler {
        @Override
        public boolean isFixedType() {
            return false;
        }

        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_INT_OR_STRING;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            return PredefinedGenericTypes.ANY_TYPE;
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object text,
                                XLangCompileTool cp) {
            if (text == null || text instanceof Integer) {
                return text;
            }

            String value = text.toString();
            if (StringHelper.isNumber(value))
                return ConvertHelper.toInt(text,
                        err -> new NopException(ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN).loc(loc)
                                .param(ARG_STD_DOMAIN, this.getName()).param(ARG_PROP_NAME, propName)
                                .param(ARG_VALUE, text));

            return value;
        }
    }

    public static class StdSqlTypeType extends SimpleStdDomainHandler {
        @Override
        public boolean isFixedType() {
            return false;
        }

        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_STD_SQL_TYPE;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            return ReflectionManager.instance().buildGenericType(StdSqlType.class);
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object text,
                                XLangCompileTool cp) {
            if (text instanceof StdSqlType)
                return text;

            StdSqlType type = StdSqlType.fromStdName((String) text);
            if (type == null) {
                throw new NopException(ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN).loc(loc)
                        .param(ARG_STD_DOMAIN, this.getName()).param(ARG_PROP_NAME, propName).param(ARG_VALUE, text)
                        .param(ARG_ALLOWED_NAMES, StdSqlType.getNames());
            }
            return type;
        }
    }

    public static class StdSqlTypeListType extends SimpleStdDomainHandler {
        static final Type LIST_TYPE = new TypeReference<List<StdSqlType>>() {
        }.getType();

        @Override
        public boolean isFixedType() {
            return false;
        }

        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_STD_SQL_TYPE_LIST;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            return ReflectionManager.instance().buildGenericType(LIST_TYPE);
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object text,
                                XLangCompileTool cp) {

            List<String> list = ConvertHelper.toCsvList(text, NopException::new);
            List<StdSqlType> ret = new ArrayList<>(list.size());

            for (String str : list) {
                StdSqlType type = StdSqlType.fromStdName(str);
                if (type == null) {
                    throw new NopException(ERR_XDEF_ILLEGAL_PROP_VALUE_FOR_STD_DOMAIN).loc(loc)
                            .param(ARG_STD_DOMAIN, this.getName()).param(ARG_PROP_NAME, propName).param(ARG_VALUE, text)
                            .param(ARG_ALLOWED_NAMES, StdSqlType.getNames()).param(ARG_ITEM_VALUE, str);
                }
                ret.add(type);
            }
            return ret;
        }
    }

    public static class SqlOrderByType extends SimpleStdDomainHandler {
        public String getName() {
            return XDefConstants.STD_DOMAIN_SQL_ORDER_BY;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            IGenericType type = ReflectionManager.instance().buildRawType(OrderFieldBean.class);
            return GenericTypeHelper.buildListType(type);
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object value,
                                XLangCompileTool cp) {
            String str = (String) value;
            return QueryBeanHelper.parseOrderBySql(loc, str);
        }
    }

    public static class CellPosType extends SimpleStdDomainHandler {
        public String getName() {
            return XDefConstants.STD_DOMAIN_CELL_POS;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            return ReflectionManager.instance().buildRawType(CellPosition.class);
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object value,
                                XLangCompileTool cp) {
            return CellPosition.fromABString((String) value);
        }
    }

    public static class CellRangeType extends SimpleStdDomainHandler {
        public String getName() {
            return XDefConstants.STD_DOMAIN_CELL_RANGE;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            return ReflectionManager.instance().buildRawType(CellRange.class);
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object value,
                                XLangCompileTool cp) {
            return CellRange.fromABString((String) value);
        }
    }

    public static class JPathType implements IStdDomainHandler {
        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_JPATH;
        }

        @Override
        public boolean isFixedType() {
            return true;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            return ReflectionManager.instance().buildRawType(JPath.class);
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object text,
                                XLangCompileTool cp) {
            return JPath.compileWithCache((String) text);
        }

        @Override
        public void validate(SourceLocation loc, String propName, Object value, IValidationErrorCollector collector) {
            try {
                JPath.compile(value.toString());
            } catch (Exception e) {
                collector.addException(e);
            }
        }
    }

    public static class XPathType implements IStdDomainHandler {
        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_XPATH;
        }

        @Override
        public boolean isFixedType() {
            return true;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            IGenericType baseType = ReflectionManager.instance().buildGenericType(IXSelector.class);
            return GenericTypeHelper.buildParameterizedType(baseType, Arrays.asList(X_NODE_TYPE));
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object text,
                                XLangCompileTool cp) {
            return XPathProvider.instance().compileWithCache((String) text);
        }

        @Override
        public void validate(SourceLocation loc, String propName, Object value, IValidationErrorCollector collector) {
            try {
                XPathProvider.instance().compile(value.toString());
            } catch (Exception e) {
                collector.addException(e);
            }
        }
    }

    public static class XdefAttrType extends SimpleStdDomainHandler {
        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_XDEF_ATTR;
        }

        @Override
        public boolean isFixedType() {
            return true;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            return ReflectionManager.instance().buildRawType(XDefAttribute.class);
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object text,
                                XLangCompileTool cp) {
            XDefTypeDecl type = XDslParseHelper.parseDefType(loc, propName, (String) text);
            XDefAttribute attr = new XDefAttribute();
            attr.setType(type);
            attr.setName(propName);
            attr.setLocation(loc);
            return attr;
        }
    }

    public static class FormLayoutType extends SimpleStdDomainHandler {
        @Override
        public String getName() {
            return XDefConstants.STD_DOMAIN_FORM_LAYOUT;
        }

        @Override
        public boolean isFixedType() {
            return true;
        }

        @Override
        public IGenericType getGenericType(boolean mandatory, IStdDomainOptions options) {
            return ReflectionManager.instance().buildRawType(LayoutModel.class);
        }

        @Override
        public Object parseProp(IStdDomainOptions options, SourceLocation loc, String propName, Object text,
                                XLangCompileTool cp) {
            return new LayoutModelParser().parseFromText(loc, (String) text);
        }
    }
}
