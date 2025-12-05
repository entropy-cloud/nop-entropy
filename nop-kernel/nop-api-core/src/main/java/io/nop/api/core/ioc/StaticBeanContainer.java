package io.nop.api.core.ioc;

import jakarta.annotation.Nonnull;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StaticBeanContainer implements IBeanContainer {
    private final Map<String, Object> beans = new ConcurrentHashMap<>();

    public void registerBean(String beanId, Object bean) {
        beans.put(beanId, bean);
    }


    @Override
    public String getId() {
        return "";
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public void restart() {

    }

    @Override
    public boolean containsBean(String name) {
        return beans.containsKey(name);
    }

    @Override
    public boolean isRunning() {
        return true;
    }

    @Nonnull
    @Override
    public Object getBean(String name) {
        return beans.get(name);
    }

    @Override
    public boolean containsBeanType(Class<?> clazz) {
        return tryGetBeanByType(clazz) != null;
    }

    @Nonnull
    @Override
    public <T> T getBeanByType(Class<T> clazz) {
        Object bean = tryGetBeanByType(clazz);
        if (bean == null)
            throw new IllegalArgumentException("invalid bean");
        return (T) bean;
    }

    @Override
    public <T> T tryGetBeanByType(Class<T> clazz) {
        for (Object bean : beans.values()) {
            if (clazz.isInstance(bean))
                return (T) bean;
        }
        return null;
    }

    @Override
    public <T> Map<String, T> getBeansOfType(Class<T> clazz) {
        Map<String, T> ret = new HashMap<>();
        beans.forEach((name, bean) -> {
            if (clazz.isInstance(bean)) {
                ret.put(name, (T) bean);
            }
        });
        return ret;
    }

    @Override
    public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annClass) {
        Map<String, Object> ret = new HashMap<>();
        beans.forEach((name, bean) -> {
            if (bean.getClass().isAnnotationPresent(annClass))
                ret.put(name, bean);
        });
        return ret;
    }

    @Override
    public String getBeanScope(String name) {
        return "singleton";
    }

    @Override
    public Class<?> getBeanClass(String name) {
        Object bean = beans.get(name);
        return bean == null ? null : bean.getClass();
    }

    @Override
    public String findAutowireCandidate(Class<?> beanType) {
        return null;
    }
}
