/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.js.engine;

import io.nop.api.core.config.IConfigRefreshable;
import io.nop.api.core.exceptions.NopException;
import io.nop.commons.concurrent.executor.DefaultThreadPoolExecutor;
import io.nop.commons.concurrent.executor.ThreadPoolConfig;
import io.nop.commons.functional.IAsyncFunctionService;
import io.nop.commons.service.LifeCycleSupport;
import io.nop.core.resource.IResourceTextLoader;
import io.nop.js.JsConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

import static io.nop.js.JsErrors.ERR_JS_CONTEXT_ALREADY_CLOSED;

public class JavaScriptService extends LifeCycleSupport implements IConfigRefreshable, IAsyncFunctionService {
    static final Logger LOG = LoggerFactory.getLogger(JavaScriptService.class);

    private String initScriptPath;
    private int workerCount = 5;
    private volatile JavaScriptWorker[] workers; //NOSONAR
    private DefaultThreadPoolExecutor executor;
    private IResourceTextLoader jsLibLoader;

    public void setWorkerCount(int workerCount) {
        this.workerCount = workerCount;
    }

    public void setInitScriptPath(String initScriptPath) {
        this.initScriptPath = initScriptPath;
    }

    public void setJsLibLoader(IResourceTextLoader jsLibLoader) {
        this.jsLibLoader = jsLibLoader;
    }

    /**
     * 当前 worker 数组快照（CRA 8.40 测试缝 S2，包内可见）：workers/getCurrentWorker 均 private，
     * 测试经本方法观测 worker 状态（如等待死 worker 线程终止后再驱动存活路由断言）。
     */
    JavaScriptWorker[] snapshotWorkers() {
        return this.workers;
    }

    @Override
    protected void doStart() {
        // 显式cached形态（配置重载，maxPoolSize<0=cached标记）：保留原newExecutor("javascript-engine",-1,-1)
        // 的0-core cached实际语义，同时获得池工厂契约的上界与达界熔断（上界=nop.commons.concurrent.cached-pool.maxPoolSize）。
        // 旧的三参负值传参已按工厂校验契约封死（负值不再被静默解释为cached）
        ThreadPoolConfig config = new ThreadPoolConfig();
        config.setName("javascript-engine");
        config.setCorePoolSize(0);
        config.setMaxPoolSize(-1);
        executor = DefaultThreadPoolExecutor.newExecutor(config);

        workers = new JavaScriptWorker[workerCount];
        for (int i = 0; i < workerCount; i++) {
            JavaScriptWorker worker = newWorker();
            workers[i] = worker;
            executor.execute(worker);
        }
    }

    public void reinitWorkers() {
        JavaScriptWorker[] workers = this.workers;
        for (int i = 0, n = workers.length; i < n; i++) {
            try {
                workers[i].schedule(JsConstants.FUNC_REINIT);
            } catch (Exception e) {
                // 已关闭（死亡）worker 的同步拒绝只跳过本 worker，不中止循环——
                // 否则存活 worker 漏重建
                LOG.warn("nop.js.reinit-worker-skip:index={}", i, e);
            }
        }
    }

    private JavaScriptWorker getCurrentWorker() {
        Thread currentThread = Thread.currentThread();
        JavaScriptWorker[] workers = this.workers;
        for (int i = 0; i < workers.length; i++) {
            if (workers[i].getExecuteThread() == currentThread)
                return workers[i];
        }
        return null;
    }

    public CompletableFuture<Object> invokeAsync(String funcName, Object... args) {
        if (funcName.equals(JsConstants.FUNC_REINIT_WORKERS)) {
            this.reinitWorkers();
            return CompletableFuture.completedFuture(null);
        }

        // 嵌套调用。FUNC_REINIT 只允许在空闲任务边界以排队任务形态执行——嵌套（JS 回调内）重建
        // 必关本线程 entered 的 context（safeClose 吞错致旧 context 泄漏），显式拒绝。
        JavaScriptWorker currentWorker = getCurrentWorker();
        if (currentWorker != null) {
            if (funcName.equals(JsConstants.FUNC_REINIT)) {
                CompletableFuture<Object> rejected = new CompletableFuture<>();
                rejected.completeExceptionally(new IllegalStateException(
                        "FUNC_REINIT cannot be invoked from within a running JS task"));
                return rejected;
            }
            return currentWorker.invokeFunction(funcName, args);
        }

        // 存活过滤（W-01 失效层②）：死亡 worker（init 失败或已关闭）排除出候选集，恢复可用性；
        // 候选全部关闭时同步拒绝（无可用 JS 执行环境，与服务停止同码同处置）。
        JavaScriptWorker[] workers = this.workers;
        int leastWaiting = Integer.MAX_VALUE;
        int leastIndex = -1;

        for (int i = 0, n = workers.length; i < n; i++) {
            JavaScriptWorker worker = workers[i];
            if (worker.isClosed())
                continue;
            int waiting = worker.getWaitingTasks();
            if (waiting == 0) {
                return worker.schedule(funcName, args);
            }
            if (waiting < leastWaiting) {
                leastIndex = i;
                leastWaiting = waiting;
            }
        }
        if (leastIndex < 0)
            throw new NopException(ERR_JS_CONTEXT_ALREADY_CLOSED);
        return workers[leastIndex].schedule(funcName, args);
    }

    @Override
    public void refreshConfig() {
        JavaScriptWorker[] workers = this.workers;
        int workerCount = this.workerCount;
        if (workers.length < workerCount) {
            JavaScriptWorker[] newWorkers = new JavaScriptWorker[workerCount];
            System.arraycopy(workers, 0, newWorkers, 0, workers.length);
            for (int i = workers.length; i < workerCount; i++) {
                JavaScriptWorker worker = new JavaScriptWorker();
                workers[i] = worker;
                executor.execute(worker);
            }
            this.workers = newWorkers;
        } else if (workers.length > workerCount) {
            JavaScriptWorker[] newWorkers = new JavaScriptWorker[workerCount];
            System.arraycopy(workers, 0, newWorkers, 0, workerCount);
            this.workers = newWorkers;

            for (int i = workerCount; i < workers.length; i++) {
                workers[i].shutdownGracefully();
            }
        }
    }

    private JavaScriptWorker newWorker() {
        JavaScriptWorker worker = new JavaScriptWorker();
        worker.setInitScriptPath(initScriptPath);
        worker.setJsLibLoader(jsLibLoader);
        return worker;
    }

    @Override
    public void doStop() {
        JavaScriptWorker[] workers = this.workers;
        if (workers != null) {
            for (JavaScriptWorker worker : workers) {
                if (worker != null) worker.close();
            }
        }
        if (executor != null) executor.destroy();
    }
}