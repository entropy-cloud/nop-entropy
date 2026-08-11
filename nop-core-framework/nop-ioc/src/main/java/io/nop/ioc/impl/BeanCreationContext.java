package io.nop.ioc.impl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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

        do {
            // Drain eligible actions to a snapshot before iterating: action.run() may trigger
            // recursive bean creation (getBean -> flushInit), which would otherwise mutate the
            // same TreeMap during iteration and throw ConcurrentModificationException.
            // New actions with key <= beanIndex go into a fresh map and are picked up by the
            // loop below or by the next flushInit pass.
            List<Runnable> snapshot = new ArrayList<>();
            Iterator<Map.Entry<Integer, Runnable>> it = initActions.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, Runnable> entry = it.next();
                if (entry.getKey() <= beanIndex) {
                    it.remove();
                    snapshot.add(entry.getValue());
                } else {
                    break;
                }
            }

            for (Runnable action : snapshot) {
                action.run();
            }

            // If recursive bean creation added new init actions with key <= beanIndex during
            // the run above, flush them now (one extra pass; further additions will be handled
            // by the next flushInit call or by flushActions at the top level).
            if (initActions == null || initActions.isEmpty() || initActions.firstKey() > beanIndex)
                break;
        } while (true);
    }

    public void addInitAction(int beanIndex, Runnable action) {
        if (initActions == null)
            initActions = new TreeMap<>();
        initActions.put(beanIndex, action);
    }

    public void runInitActions() {
        if (initActions != null) {
            // Drain to a snapshot before iterating: action.run() may trigger further bean
            // creation (recursive getBean -> flushActions), which would otherwise call
            // addInitAction on the same TreeMap during iteration and throw
            // ConcurrentModificationException. New actions go into a fresh map and are
            // picked up by the next flushActions pass.
            TreeMap<Integer, Runnable> snapshot = initActions;
            initActions = null;
            for (Runnable action : snapshot.values()) {
                action.run();
            }
        }
    }

    public void addLazyPropAction(int beanIndex, Runnable action) {
        if (lazyPropActions == null)
            lazyPropActions = new TreeMap<>();
        lazyPropActions.put(beanIndex, action);
    }

    public void runLazyPropActions() {
        if (lazyPropActions != null) {
            // Same drain-then-iterate pattern as runInitActions — see comment there.
            TreeMap<Integer, Runnable> snapshot = lazyPropActions;
            lazyPropActions = null;
            for (Runnable action : snapshot.values()) {
                action.run();
            }
        }
    }

    public void addDelayAction(int beanIndex, Runnable action) {
        if (delayActions == null)
            delayActions = new TreeMap<>();
        delayActions.put(beanIndex, action);
    }

    public void runDelayActions() {
        if (delayActions != null) {
            // Same drain-then-iterate pattern as runInitActions — see comment there.
            TreeMap<Integer, Runnable> snapshot = delayActions;
            delayActions = null;
            for (Runnable action : snapshot.values())
                action.run();
        }
    }
}