/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package test.io.entropy.beans;

import io.nop.api.core.annotations.data.DataBean;
import io.nop.api.core.ioc.IBeanContainer;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.Properties;

@DataBean
public class MyBeanA extends MyBaseBean {

    private List<Object> dataList;
    private Map<String, String> strMap;
    private Properties props;
    private boolean inited = false;
    private boolean destroyed = false;

    private MyBeanB b;

    List<MyBeanC> listC;

    List<Object> objList;

    int x;

    @Inject
    @Nullable
    protected MyBeanD beanD;

    public MyBeanC[] arrayC;

    public IBeanContainer container;

    public MyBeanA() {
        System.out.println("MyBeanA：constructor.........");
    }

    public static MyBeanA newInstance() {
        MyBeanA beanA = new MyBeanA();
        beanA.setB(new MyBeanB());
        return beanA;
    }

    @Override
    public List<MyBeanC> getListC() {
        return listC;
    }

    public void setListC(List<MyBeanC> listC) {
        this.listC = listC;
    }

    public MyBeanD getBeanD() {
        return beanD;
    }

    public void setBeanD(MyBeanD beanD) {
        this.beanD = beanD;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public List<Object> getObjList() {
        return objList;
    }

    public void setObjList(List<Object> objList) {
        this.objList = objList;
    }

    /**
     * 初始化方法
     */
    public void init() {
        inited = true;
        System.out.println("MyBeanA：inited.........");
    }

    /**
     * 销毁
     */
    public void destroy() {
        dataList = null;
        strMap = null;
        props = null;
        b = null;
        destroyed = true;
        System.out.println("MyBeanA：destroyed.........");
    }

    @Override
    public boolean equals(Object obj) {
        System.out.println("equals() execute......");
        return super.equals(obj);
    }

    // ----------------------------------------

    public List<Object> getDataList() {
        return dataList;
    }

    public void setStrList(List<Object> dataList) {
        this.dataList = dataList;
    }

    public Map<String, String> getStrMap() {
        return strMap;
    }

    public void setStrMap(Map<String, String> strMap) {
        this.strMap = strMap;
    }

    public Properties getProps() {
        return props;
    }

    public void setProps(Properties props) {
        this.props = props;
    }

    public boolean isInited() {
        return inited;
    }

    public void setInited(boolean inited) {
        this.inited = inited;
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    public void setDestroyed(boolean destroyed) {
        this.destroyed = destroyed;
    }

    public MyBeanB getB() {
        return b;
    }

    public void setB(MyBeanB b) {
        this.b = b;
    }

    public Object secreteMethodA() {
        return null;
    }

    public String secreteMethodB() {
        return "aa";
    }
}
