package io.nop.ioc.impl;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public class BeanCreationContext {
    private final BeanDefinition rootBeanDef;
    private final boolean shouldRunLazy;

    private TreeMap<Integer, Runnable> initActions;

    private TreeMap<Integer, Runnable> lazyPropActions;

    private TreeMap<Integer, Runnable> delayActions;

    public BeanCreationContext(BeanDefinition rootBeanDef) {
        this(rootBeanDef, true);
    }

    public BeanCreationContext(BeanDefinition rootBeanDef, boolean shouldRunLazy) {
        this.rootBeanDef = rootBeanDef;
        this.shouldRunLazy = shouldRunLazy;
    }

    public BeanDefinition getRootBeanDef() {
        return rootBeanDef;
    }

    public boolean shouldRunLazy() {
        return shouldRunLazy;
    }

    public synchronized void flushActions() {
        runInitActions();
        if (shouldRunLazy) {
            runLazyPropActions();
            runDelayActions();
        }
    }

    public synchronized void flushInit(int beanIndex) {
        if (initActions == null)
            return;

        Iterator<Map.Entry<Integer, Runnable>> it = initActions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Runnable> entry = it.next();
            if (entry.getKey() <= beanIndex) {
                it.remove();
                entry.getValue().run();
                ;
            } else {
                break;
            }
        }
    }

    public void addInitAction(int beanIndex, Runnable action) {
        if (initActions == null)
            initActions = new TreeMap<>();
        initActions.put(beanIndex, action);
    }

    public void runInitActions() {
        if (initActions != null) {
            for (Runnable action : initActions.values()) {
                action.run();
            }
            initActions = null;
        }
    }

    public void addLazyPropAction(int beanIndex, Runnable action) {
        if (lazyPropActions == null)
            lazyPropActions = new TreeMap<>();
        lazyPropActions.put(beanIndex, action);
    }

    public void runLazyPropActions() {
        if (lazyPropActions != null) {
            for (Runnable action : lazyPropActions.values()) {
                action.run();
            }
            lazyPropActions = null;
        }
    }

    public void addDelayAction(int beanIndex, Runnable action) {
        if (delayActions == null)
            delayActions = new TreeMap<>();
        delayActions.put(beanIndex, action);
    }

    public void runDelayActions() {
        if (delayActions != null) {
            for (Runnable action : delayActions.values())
                action.run();
            delayActions = null;
        }
    }
}