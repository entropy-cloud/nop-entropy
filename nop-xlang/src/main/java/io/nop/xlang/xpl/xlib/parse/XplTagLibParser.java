/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
// package io.nop.xlang.xpl.xlib.parse;
//
// import io.nop.api.core.exceptions.NopException;
// import io.nop.commons.collections.KeyedList;
// import io.nop.commons.util.StringHelper;
// import io.nop.core.lang.xml.XNode;
// import io.nop.core.type.IGenericType;
// import io.nop.xlang.XLangConstants;
// import io.nop.xlang.ast.XLangOutputMode;
// import io.nop.xlang.xdsl.AbstractDslParser;
// import io.nop.xlang.xpl.XplSlotType;
// import io.nop.xlang.xpl.xlib.XlibConstants;
// import io.nop.xlang.xpl.xlib.XplLibHelper;
// import io.nop.xlang.xpl.xlib.XplLibTagCompiler;
// import io.nop.xlang.xpl.xlib.XplTag;
// import io.nop.xlang.xpl.xlib.XplTagAttribute;
// import io.nop.xlang.xpl.xlib.XplTagLib;
// import io.nop.xlang.xpl.xlib.XplTagReturn;
// import io.nop.xlang.xpl.xlib.XplTagSlot;
// import io.nop.xlang.xpl.xlib.XplTagSlotArg;
//
// import java.util.List;
// import java.util.Map;
//
// import static io.nop.xlang.XLangErrors.ARG_NODE;
// import static io.nop.xlang.XLangErrors.ERR_XLIB_NODE_SLOT_NOT_SUPPORT_ARG;
// import static io.nop.xlang.XLangErrors.ERR_XLIB_NOT_ALLOW_NAMED_SLOT_IF_DEFAULT_IS_USED;
// import static io.nop.xlang.xdsl.XDslParseHelper.parseAttrBoolean;
// import static io.nop.xlang.xdsl.XDslParseHelper.parseAttrEnumValue;
// import static io.nop.xlang.xdsl.XDslParseHelper.parseAttrGenericType;
// import static io.nop.xlang.xdsl.XDslParseHelper.parseAttrGenericTypes;
// import static io.nop.xlang.xdsl.XDslParseHelper.parseAttrPropName;
// import static io.nop.xlang.xdsl.XDslParseHelper.parseAttrVPath;
// import static io.nop.xlang.xdsl.XDslParseHelper.parseAttrXmlName;
// import static io.nop.xlang.xdsl.XDslParseHelper.parseChildrenAsMap;
// import static io.nop.xlang.xdsl.XDslParseHelper.parseSelectedChildrenAsList;
//
// public class XplTagLibParser extends AbstractDslParser<XplTagLib> {
//
// @Override
// protected XplTagLib doParseNode(XNode node) {
// XplTagLib lib = new XplTagLib();
// lib.setLocation(node.getLocation());
// lib.setImportExprs(getImportExprs());
//
// XLangOutputMode defaultOutputMode = parseAttrEnumValue(
// node, XlibConstants.DEFAULT_OUTPUT_MODE_NAME,
// XLangOutputMode.class, XLangOutputMode::fromText);
//
// String displayName = node.attrText(XlibConstants.DISPLAY_NAME_NAME);
// List<IGenericType> interfaces = parseAttrGenericTypes(node,
// XlibConstants.INTERFACES_NAME, getRawTypeResolver());
//
// String ns = XplLibHelper.getNamespaceFromLibPath(node.resourcePath());
// lib.setDefaultNamespace(ns);
// lib.setDisplayName(displayName);
// lib.setDefaultOutputMode(defaultOutputMode);
// lib.setInterfaces(interfaces);
//
// XNode tagsNode = node.childByTag(XlibConstants.TAGS_NAME);
// if (tagsNode != null) {
// Map<String, XplTag> tags = parseChildrenAsMap(tagsNode, child -> parseTag(lib.resourcePath(), child,
// defaultOutputMode));
// lib.setTags(tags);
//
//
// for (XplTag tag : tags.values()) {
// tag.setTagCompiler(new XplLibTagCompiler(lib, tag));
// }
// }
// return lib;
// }
//
// public XplTag parseTag(String libPath, XNode node, XLangOutputMode defaultOutputMode) {
// XplTag tag = new XplTag();
// tag.setLocation(node.getLocation());
// String tagName = node.getTagName();
// String displayName = node.attrText(XlibConstants.DISPLAY_NAME_NAME);
// String description = node.elementText(XlibConstants.DESCRIPTION_NAME);
// boolean deprecated = parseAttrBoolean(node, XlibConstants.DEPRECATED_NAME, false);
// boolean macro = parseAttrBoolean(node, XlibConstants.MACRO_NAME, false);
// boolean internal = parseAttrBoolean(node, XlibConstants.INTERNAL_NAME, false);
// String varUnknownAttrs = parseAttrPropName(node, XlibConstants.UNKNOWN_ATTRS_VAR_NAME);
// String varAttrs = parseAttrPropName(node, XlibConstants.ATTRS_VAR_NAME);
//
// XLangOutputMode outputMode = parseAttrEnumValue(node, XlibConstants.OUTPUT_MODE_NAME, XLangOutputMode.class,
// XLangOutputMode::fromText);
// if (outputMode == null)
// outputMode = defaultOutputMode;
// if (outputMode == null)
// outputMode = XLangOutputMode.none;
//
// String transformer = parseAttrVPath(node, XlibConstants.TRANSFORMER_NAME);
// String schema = parseAttrVPath(node, XlibConstants.SCHEMA_NAME);
//
// KeyedList<XplTagAttribute> attrs = parseSelectedChildrenAsList(node, XlibConstants.ATTR_NAME,
// this::parseTagAttr);
// KeyedList<XplTagSlot> slots = parseSelectedChildrenAsList(node, XlibConstants.SLOT_NAME,
// child -> this.parseTagSlot(libPath, tagName, child));
//
//
// XplTagReturn tagReturn = parseTagReturn(node.childByTag(XlibConstants.RETURN_NAME));
//
// XNode source = node.childByTag(XlibConstants.SOURCE_NAME);
// if (source != null) {
// source = source.cloneInstance();
// }
//
// tag.setTagFuncName('<' + libPath + '#' + tagName + '>');
// tag.setTagName(tagName);
// tag.setDisplayName(displayName);
// tag.setDeprecated(deprecated);
// tag.setDescription(description);
// tag.setInternal(internal);
// tag.setMacro(macro);
// tag.setOutputMode(outputMode);
// tag.setTransformer(transformer);
// tag.setAttrsVar(varAttrs);
// tag.setUnknownAttrsVar(varUnknownAttrs);
// tag.setAttrs(attrs);
// tag.setSlots(slots);
// tag.setTagReturn(tagReturn);
// tag.setSource(source);
// tag.setSchema(schema);
//
// tag.init();
// return tag;
// }
//
// private XplTagAttribute parseTagAttr(XNode node) {
// XplTagAttribute ret = new XplTagAttribute();
// ret.setLocation(node.getLocation());
//
// String name = parseAttrXmlName(node, XlibConstants.NAME_NAME);
// String displayName = node.attrText(XlibConstants.DISPLAY_NAME_NAME);
// String description = node.elementText(XlibConstants.DESCRIPTION_NAME);
// IGenericType type = parseAttrGenericType(node, XlibConstants.TYPE_NAME, getRawTypeResolver());
// String domain = parseAttrXmlName(node, XlibConstants.DOMAIN_NAME);
// boolean implicit = parseAttrBoolean(node, XlibConstants.IMPLICIT_NAME, false);
// boolean internal = parseAttrBoolean(node, XlibConstants.INTERNAL_NAME, false);
// boolean deprecated = parseAttrBoolean(node, XlibConstants.DEPRECATED_NAME, false);
// boolean mandatory = parseAttrBoolean(node, XlibConstants.MACRO_NAME, false);
// boolean optional = parseAttrBoolean(node, XlibConstants.OPTIONAL_NAME, false);
//
// String varName = parseAttrPropName(node, XlibConstants.VAR_NAME_NAME);
// if (varName == null)
// varName = StringHelper.xmlNameToVarName(name);
//
// ret.setName(name);
// ret.setDisplayName(displayName);
// ret.setDescription(description);
// ret.setType(type);
// ret.setDomain(domain);
// ret.setImplicit(implicit);
// ret.setInternal(internal);
// ret.setDeprecated(deprecated);
// ret.setMandatory(mandatory);
// ret.setOptional(optional);
// ret.setVarName(varName);
// return ret;
// }
//
// private XplTagSlot parseTagSlot(String libPath, String tagName, XNode node) {
// XplTagSlot ret = new XplTagSlot();
// ret.setLocation(node.getLocation());
// ret.setSlotFuncName("<" + libPath + '#' + tagName + '/' + node.getTagName() + '[' +
// node.attrText(XlibConstants.ARG_NAME) + "]>");
//
// XplSlotType slotType = parseAttrEnumValue(node, XlibConstants.SLOT_TYPE_NAME,
// XplSlotType.renderer,
// XplSlotType.class, XplSlotType::fromText);
// XLangOutputMode outputMode = parseAttrEnumValue(node, XlibConstants.OUTPUT_MODE_NAME,
// XLangOutputMode.class, XLangOutputMode::fromText);
//
// String displayName = node.attrText(XlibConstants.DISPLAY_NAME_NAME);
// String description = node.elementText(XlibConstants.DESCRIPTION_NAME);
//
// String name = parseAttrPropName(node, XlibConstants.NAME_NAME);
// String varName = parseAttrPropName(node, XlibConstants.VAR_NAME_NAME);
// if (varName == null) {
// varName = XlibConstants.SLOT_VAR_PREFIX + name;
// }
//
// if (slotType == XplSlotType.node) {
// if (node.hasChild(XlibConstants.ATTR_NAME))
// throw new NopException(ERR_XLIB_NODE_SLOT_NOT_SUPPORT_ARG)
// .param(ARG_NODE, node);
// }
//
// KeyedList<XplTagSlotArg> args = parseSelectedChildrenAsList(node, XlibConstants.ARG_NAME,
// this::parseSlotArg);
// ret.setSlotType(slotType);
// ret.setName(name);
// ret.setVarName(varName);
// ret.setArgs(args);
// ret.setOutputMode(outputMode);
// ret.setDisplayName(displayName);
// ret.setDescription(description);
// return ret;
// }
//
// private XplTagSlotArg parseSlotArg(XNode node) {
// XplTagSlotArg ret = new XplTagSlotArg();
// ret.setLocation(node.getLocation());
//
// String name = parseAttrPropName(node, XlibConstants.NAME_NAME);
// String displayName = node.attrText(XlibConstants.DISPLAY_NAME_NAME);
// String description = node.elementText(XlibConstants.DESCRIPTION_NAME);
// IGenericType type = parseAttrGenericType(node, XlibConstants.TYPE_NAME, getRawTypeResolver());
// String domain = parseAttrXmlName(node, XlibConstants.DOMAIN_NAME);
//
// ret.setName(name);
// ret.setDisplayName(displayName);
// ret.setDescription(description);
// ret.setType(type);
// ret.setDomain(domain);
// return ret;
// }
//
// private XplTagReturn parseTagReturn(XNode node) {
// if (node == null)
// return null;
//
// XplTagReturn ret = new XplTagReturn();
// ret.setLocation(node.getLocation());
//
// IGenericType type = parseAttrGenericType(node, XlibConstants.TYPE_NAME, getRawTypeResolver());
// String domain = parseAttrXmlName(node, XlibConstants.DOMAIN_NAME);
// String description = node.elementText(XlibConstants.DESCRIPTION_NAME);
//
// ret.setType(type);
// ret.setDomain(domain);
// ret.setDescription(description);
// return ret;
// }
// }