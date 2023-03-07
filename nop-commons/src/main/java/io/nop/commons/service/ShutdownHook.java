/*
 *  Copyright 1999-2019 Seata.io Group.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.nop.commons.service;

import io.nop.commons.collections.SafeOrderedComparator;
import io.nop.commons.lang.IDestroyable;
import io.nop.commons.util.CollectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ensure the shutdownHook is singleton
 *
 * @author 563868273@qq.com
 */
public class ShutdownHook extends Thread {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShutdownHook.class);

    private static final ShutdownHook SHUTDOWN_HOOK = new ShutdownHook("NopShutdownHook");

    private Set<IDestroyable> disposables = new TreeSet<>(SafeOrderedComparator.DEFAULT);

    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    /**
     * default 10. Lower values have higher priority
     */
//    private static final int DEFAULT_PRIORITY = 10;

    static {
        Runtime.getRuntime().addShutdownHook(SHUTDOWN_HOOK);
    }

    private ShutdownHook(String name) {
        super(name);
    }

    public static ShutdownHook getInstance() {
        return SHUTDOWN_HOOK;
    }

    public void addDisposable(IDestroyable disposable) {
        disposables.add(disposable);
    }

    @Override
    public void run() {
        destroyAll();
    }

    public void destroyAll() {
        LOGGER.debug("nop.shutdown-hook.destroying-start");

        if (!destroyed.compareAndSet(false, true) && CollectionHelper.isEmpty(disposables)) {
            return;
        }
        for (IDestroyable disposable : disposables) {
            disposable.destroy();
        }
    }

    /**
     * for spring context
     */
    public static void removeRuntimeShutdownHook() {
        Runtime.getRuntime().removeShutdownHook(SHUTDOWN_HOOK);
    }

}
