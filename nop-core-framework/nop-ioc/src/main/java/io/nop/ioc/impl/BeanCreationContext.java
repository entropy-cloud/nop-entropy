package io.nop.ioc.impl;

import java.util.ArrayList;
import java.util.Collections;
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

    /**
     * 按顺序 快照→执行 → 快照→执行 推进 init → lazy → delay 三个队列。每次快照是短临界区，
     * 执行在锁外进行，因此init执行期间新增的lazy action仍会被后续的lazy快照拾取。
     * 任何线程都不再"持有ctx等待bean"。
     */
    public void flushActions() {
        List<Runnable> initSnapshot = drainAndClearInit();
        runActions(initSnapshot);

        if (shouldRunLazy) {
            List<Runnable> lazySnapshot = drainAndClearLazyProp();
            runActions(lazySnapshot);

            List<Runnable> delaySnapshot = drainAndClearDelay();
            runActions(delaySnapshot);
        }
    }

    /**
     * 锁内按拓扑序快照并drain key &lt;= beanIndex 的init action，锁外执行。
     * 循环重查新入队（递归创建bean时新增）的key &lt;= beanIndex 的动作。
     */
    public void flushInit(int beanIndex) {
        List<Runnable> snapshot = drainInitUpTo(beanIndex);
        if (snapshot.isEmpty())
            return;
        runActions(snapshot);

        while (true) {
            snapshot = drainInitUpTo(beanIndex);
            if (snapshot.isEmpty())
                return;
            runActions(snapshot);
        }
    }

    private List<Runnable> drainAndClearInit() {
        synchronized (this) {
            return drainAndClear(initActions);
        }
    }

    private List<Runnable> drainAndClearLazyProp() {
        synchronized (this) {
            return drainAndClear(lazyPropActions);
        }
    }

    private List<Runnable> drainAndClearDelay() {
        synchronized (this) {
            return drainAndClear(delayActions);
        }
    }

    private List<Runnable> drainAndClear(TreeMap<Integer, Runnable> actions) {
        if (actions == null)
            return Collections.emptyList();
        List<Runnable> snapshot = new ArrayList<>(actions.values());
        actions.clear();
        return snapshot;
    }

    private List<Runnable> drainInitUpTo(int beanIndex) {
        synchronized (this) {
            if (initActions == null)
                return Collections.emptyList();
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
            return snapshot;
        }
    }

    private static void runActions(List<Runnable> snapshot) {
        for (Runnable action : snapshot) {
            action.run();
        }
    }

    public synchronized void addInitAction(int beanIndex, Runnable action) {
        if (initActions == null)
            initActions = new TreeMap<>();
        initActions.put(beanIndex, action);
    }

    public synchronized void addLazyPropAction(int beanIndex, Runnable action) {
        if (lazyPropActions == null)
            lazyPropActions = new TreeMap<>();
        lazyPropActions.put(beanIndex, action);
    }

    public synchronized void addDelayAction(int beanIndex, Runnable action) {
        if (delayActions == null)
            delayActions = new TreeMap<>();
        delayActions.put(beanIndex, action);
    }
}