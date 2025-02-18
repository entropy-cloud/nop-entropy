/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.exceptions;

import io.nop.api.core.ApiConfigs;
import io.nop.api.core.beans.ErrorBean;
import io.nop.api.core.util.ApiStringHelper;
import io.nop.api.core.util.ISourceLocationGetter;
import io.nop.api.core.util.SourceLocation;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 平台定义的所有异常类都统一继承此类
 */
@SuppressWarnings("java:S1165")
public class NopException extends RuntimeException implements IException {
    private static final long serialVersionUID = 618317480866467022L;

    static final AtomicLong s_seq = new AtomicLong();

    private final long seq = s_seq.incrementAndGet();

    private boolean bizFatal;
    private boolean notRollback;

    private int status;
    private final Map<String, Object> params = new HashMap<>();
    private String description;

    private SourceLocation loc;
    private List<String> xplStack;
    private boolean wrapException;

    private Map<String, ErrorBean> details;

    private boolean alreadyTraced;

    public NopException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getErrorCode(), cause);
        this.description(errorCode.getDescription());
        this.status(errorCode.getStatus());
    }

    public NopException(ErrorCode errorCode) {
        super(errorCode.getErrorCode());
        this.description(errorCode.getDescription());
        this.status(errorCode.getStatus());
    }

    public NopException(String errorCode, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(errorCode, cause, enableSuppression, writableStackTrace);
    }

    /**
     * 事务中发生此异常时是否需要回滚。ORM也会检查异常，如果需要回滚会自动清空OrmSession
     */
    public static boolean shouldRollback(Throwable e) {
        if(e == null)
            return false;

        if (e instanceof NopException)
            return !((NopException) e).isNotRollback();
        return true;
    }

    public boolean isNotRollback() {
        return notRollback;
    }

    public void setNotRollback(boolean notRollback) {
        this.notRollback = notRollback;
    }

    public NopException notRollback(boolean notRollback) {
        this.notRollback = notRollback;
        return this;
    }

    public boolean isAlreadyTraced() {
        return alreadyTraced;
    }

    public void setAlreadyTraced(boolean alreadyTraced) {
        this.alreadyTraced = alreadyTraced;
    }

    public static void logIfNotTraced(Logger logger, String message, Throwable e) {
        if (e instanceof NopException) {
            NopException ne = (NopException) e;
            if (ne.isAlreadyTraced())
                return;
            ne.setAlreadyTraced(true);
        }
        logger.error(message, e);
    }

    /**
     * Throws a particular {@code Throwable} only if it belongs to a set of "fatal" error varieties. These
     * varieties are as follows:
     * <ul>
     * <li>{@code VirtualMachineError}</li>
     * <li>{@code ThreadDeath}</li>
     * <li>{@code LinkageError}</li>
     * </ul>
     * This can be useful if you are writing an operator that calls user-supplied code, and you want to
     * notify subscribers of errors encountered in that code by calling their {@code onError} methods, but only
     * if the errors are not so catastrophic that such a call would be futile, in which case you simply want to
     * rethrow the error.
     *
     * @param t the {@code Throwable} to test and perhaps throw
     * @see <a href="https://github.com/ReactiveX/RxJava/issues/748#issuecomment-32471495">RxJava: StackOverflowError is swallowed (Issue #748)</a>
     */
    public static void throwIfFatal(Throwable t) {
        // values here derived from https://github.com/ReactiveX/RxJava/issues/748#issuecomment-32471495
        if (t instanceof VirtualMachineError) {
            throw (VirtualMachineError) t;
        } else if (t instanceof ThreadDeath) {
            throw (ThreadDeath) t;
        } else if (t instanceof LinkageError) {
            throw (LinkageError) t;
        }
    }


    public Map<String, ErrorBean> getDetails() {
        return details;
    }

    public void addDetail(String name, ErrorBean error) {
        if (details == null)
            details = new HashMap<>();
        this.details.put(name, error);
    }

    public static void addXplStack(Throwable e, Object obj) {
        if (e instanceof Error)
            throw (Error) e;
        if (e instanceof NopException) {
            NopException ne = (NopException) e;
            ne.addXplStack(obj);
            throw ne;
        } else {
            NopWrapException we = new NopWrapException(e);
            we.addXplStack(obj);
            throw we;
        }
    }

    public static RuntimeException adapt(Throwable e) {
        if (e instanceof Error)
            throw (Error) e;

        if (e instanceof RuntimeException) {
            return (RuntimeException) e;
        }
        return new NopWrapException(e);
    }

    public static NopException wrap(Throwable e) {
        if (e instanceof NopException) {
            return (NopException) e;
        }
        return new NopWrapException(e);
    }

    public NopException cause(Throwable cause) {
        if (cause != null && cause != this) {
            this.initCause(cause);
        }
        return this;
    }

    public boolean isBizFatal() {
        return bizFatal;
    }

    public NopException bizFatal(boolean bizFatal) {
        this.bizFatal = bizFatal;
        return this;
    }

    public long getSeq() {
        return seq;
    }

    public boolean isWrapException() {
        return wrapException;
    }

    public NopException forWrap() {
        wrapException = true;
        return this;
    }

    public SourceLocation getErrorLocation() {
        return loc;
    }

    public NopException source(ISourceLocationGetter source) {
        if (source != null)
            this.loc = source.getLocation();
        return this;
    }

    public NopException loc(SourceLocation loc) {
        this.loc = loc;
        return this;
    }

    /**
     * standardized error code
     */
    public String getErrorCode() {
        return super.getMessage();
    }

    /**
     * attach description info to Exception object, which may be returned to caller
     */
    public NopException description(String description) {
        this.description = description;
        return this;
    }

    public NopException errorDescription(String description) {
        return description(description);
    }

    public NopException errorStatus(int errorStatus) {
        return status(errorStatus);
    }

    /**
     * get description info for Exception
     */
    public String getDescription() {
        return description;
    }

    public String getParamsString() {
        return params.toString();
    }

    public Map<String, Object> getParams() {
        return params;
    }

    protected void appendInfo(StringBuilder sb) {

    }

    public int getStatus() {
        return status;
    }

    public NopException status(int status) {
        this.status = status;
        return this;
    }

    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName()).append("[seq=").append(seq);
        if (status != 0)
            sb.append(",status=").append(this.status);
        sb.append(",errorCode=").append(this.getErrorCode());
        sb.append(",params=").append(this.getParamsString());

        try {
            if (description != null)
                sb.append(",desc=").append(ApiStringHelper.renderTemplate(description, this::getParam));

            appendInfo(sb);
            sb.append("]");
            if (loc != null)
                sb.append("@_loc=").append(loc);

            if (xplStack != null) {
                for (String frame : xplStack) {
                    sb.append("\n  @@").append(frame);
                }
            }
        } catch (Exception e) {
            sb.append("<").append(e.getClass()).append(">");
        }

        return sb.toString();
    }

    public NopException addXplStack(Object obj) {
        if (obj == null)
            return this;

        if (this.loc != null && obj instanceof ISourceLocationGetter) {
            ISourceLocationGetter source = (ISourceLocationGetter) obj;
            if (Objects.equals(source.getLocation(), loc))
                return this;
        }

        List<String> stack = xplStack;
        if (stack == null) {
            stack = new ArrayList<>(5);
            this.xplStack = stack;
        }

        if (stack.size() < ApiConfigs.CFG_EXCEPTION_MAX_XPL_STACK_SIZE.get()) {
            String str = obj.toString();
            if (stack.isEmpty() || !Objects.equals(stack.get(stack.size() - 1), str))
                stack.add(str);
        }
        return this;
    }

    public List<String> getXplStack() {
        return xplStack;
    }

    public Object getParam(String name) {
        return params.get(name);
    }

    /**
     * add extra param for exception
     *
     * @param name  参数名
     * @param value 参数值
     * @return 当前异常对象
     */
    public NopException param(String name, Object value) {
        if (this.loc == null && value instanceof ISourceLocationGetter) {
            this.loc = ((ISourceLocationGetter) value).getLocation();
        }
        params.put(name, normalizeValue(value));
        return this;
    }

    public NopException when(boolean b, Consumer<NopException> consumer) {
        if (b) {
            consumer.accept(this);
        }
        return this;
    }

    Object normalizeValue(Object value) {
        if (value instanceof Boolean || value instanceof Number || value instanceof Character)
            return value;
        return String.valueOf(value);
    }

    public NopException params(Map<String, Object> values) {
        if (values != null) {
            for (Map.Entry<String, Object> entry : values.entrySet()) {
                param(entry.getKey(), entry.getValue());
            }
        }
        return this;
    }

    public NopException args(Object[] args) {
        params.put("args", Arrays.asList(args));
        return this;
    }
}