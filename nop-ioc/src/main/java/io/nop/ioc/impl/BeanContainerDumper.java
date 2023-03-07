/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.ioc.impl;

import io.nop.core.lang.xml.XNode;
import io.nop.ioc.IocConstants;
import io.nop.ioc.model.BeanModel;
import io.nop.ioc.model.BeanValue;
import io.nop.xlang.XLangConstants;
import io.nop.xlang.xdsl.XDslKeys;
import io.nop.xlang.xdsl.json.DslModelToXNodeTransformer;
import io.nop.xlang.xmeta.IObjMeta;
import io.nop.xlang.xmeta.IObjSchema;
import io.nop.xlang.xmeta.SchemaLoader;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class BeanContainerDumper {

    public XNode toConfigNode(Collection<BeanDefinition> allBeans) {
        String schema = IocConstants.XDEF_BEANS;

        IObjMeta objMeta = SchemaLoader.loadXMeta(schema);

        XNode node = XNode.make(objMeta.getXmlName());
        node.setAttr(XDslKeys.DEFAULT.SCHEMA, schema);
        node.setAttr("xmlns:x", XLangConstants.XDSL_SCHEMA_XDSL);
        node.setAttr("xmlns:ioc", "ioc");
        node.setAttr("xmlns:ext", "ext");
        node.setAttr("xmlns:util","util");

        IObjSchema beanSchema = objMeta.getDefine("BeanModel");

        for (BeanDefinition bean : allBeans) {
            if (bean.isDisabled())
                continue;

            XNode beanNode = buildBeanNode(objMeta, beanSchema, bean);
            node.appendChild(beanNode);
        }
        return node;
    }

    static class BeanTransformer extends DslModelToXNodeTransformer {
        public BeanTransformer(IObjMeta objMeta) {
            super(objMeta);
        }

        @Override
        protected boolean shouldIncludeProp(IObjSchema schema, String propName, Object bean, boolean extProp) {
            if (bean instanceof BeanModel) {
                if (propName.equals("constructorArgs") || propName.equals("properties")
                        || propName.equals("iocCondition"))
                    return false;
            }
            return true;
        }

        @Override
        protected void addToNode(IObjSchema schema, XNode node, Object map, String key, Object value) {
            if (value instanceof BeanValue) {
                XNode ref = XNode.make("ref");
                BeanValue beanValue = (BeanValue) value;
                ref.setAttr("bean", beanValue.getEmbeddedId());
                node.appendChild(ref);
            } else {
                super.addToNode(schema, node, map, key, value);
            }
        }
    }

    XNode buildBeanNode(IObjMeta objMeta, IObjSchema objSchema, BeanDefinition bean) {
        BeanValue beanModel = bean.getBeanModel();
        XNode node = new BeanTransformer(objMeta).transformObj(objSchema, beanModel);
        node.removeAttr("parent");
        node.removeAttr("ioc:config-prefix");
        node.setTagName("bean");
        node.setAttr("id", bean.getId());

        if (bean.getBeanMethod() != null) {
            node.setAttr("ioc:bean-method", bean.getBeanMethod().getName());
        }

        if (bean.getRefreshConfigMethod() != null) {
            node.setAttr("ioc:refresh-config", bean.getRefreshConfigMethod().getName());
        }

        if (!node.attrBoolean("lazy-init")) {
            node.removeAttr("lazy-init");
        }

        if ("singleton".equals(node.attrText("scope"))) {
            node.removeAttr("scope");
        }

        if (bean.getFactoryMethod() != null) {
            node.setAttr("factory-method", bean.getFactoryMethod().getName());
        }

        if (bean.getInitMethod() != null) {
            node.setAttr("init-method", bean.getInitMethod().getName());
        }

        if (bean.getDestroyMethod() != null) {
            node.setAttr("destroy-method", bean.getDestroyMethod().getName());
        }

        List<IBeanPropValueResolver> args = bean.getConstructorArgs();
        if (!args.isEmpty()) {
            for (int i = 0, n = args.size(); i < n; i++) {
                XNode argNode = XNode.make("constructor-arg");
                argNode.setAttr("index", i);
                IBeanPropValueResolver arg = args.get(i);
                String value = arg.toConfigString();
                if (value != null) {
                    argNode.setAttr("value", value);
                } else {
                    argNode.appendChild(args.get(i).toConfigNode());
                }
                if (bean.isConstructorAutowired()) {
                    argNode.setAttr("ext:autowired", true);
                }
                node.appendChild(argNode);
            }
        }

        Map<String, BeanProperty> props = bean.getProps();
        for (Map.Entry<String, BeanProperty> entry : props.entrySet()) {
            String name = entry.getKey();
            BeanProperty prop = entry.getValue();
            IBeanPropValueResolver resolver = prop.getValueResolver();

            XNode propNode = XNode.make("property");
            propNode.setLocation(prop.getLocation());
            propNode.setAttr("name", name);

            String value = resolver.toConfigString();
            if (value != null) {
                propNode.setAttr("value", value);
            } else {
                propNode.appendChild(resolver.toConfigNode());
            }
            if (prop.isAutowired()) {
                propNode.setAttr("ext:autowired", true);
            }
            node.appendChild(propNode);
        }
        return node;
    }
}