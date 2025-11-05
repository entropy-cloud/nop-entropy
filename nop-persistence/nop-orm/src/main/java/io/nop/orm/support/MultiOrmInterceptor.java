package io.nop.orm.support;

import io.nop.api.core.util.ProcessResult;
import io.nop.orm.IOrmEntity;
import io.nop.orm.IOrmInterceptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MultiOrmInterceptor implements IOrmInterceptor {
    private final List<IOrmInterceptor> interceptors;

    public MultiOrmInterceptor(List<IOrmInterceptor> interceptors) {
        this.interceptors = new ArrayList<>(interceptors);
    }

    public MultiOrmInterceptor() {
        this.interceptors = new ArrayList<>();
    }

    public static MultiOrmInterceptor of(IOrmInterceptor... interceptor) {
        return new MultiOrmInterceptor(Arrays.asList(interceptor));
    }

    public void addInterceptor(IOrmInterceptor interceptor) {
        interceptors.add(interceptor);
    }

    public void removeInterceptor(IOrmInterceptor interceptor) {
        interceptors.remove(interceptor);
    }

    @Override
    public ProcessResult preSave(IOrmEntity entity) {
        for (IOrmInterceptor interceptor : interceptors) {
            ProcessResult result = interceptor.preSave(entity);
            if (result != ProcessResult.CONTINUE) {
                return result;
            }
        }
        return ProcessResult.CONTINUE;
    }

    @Override
    public ProcessResult preUpdate(IOrmEntity entity) {
        for (IOrmInterceptor interceptor : interceptors) {
            ProcessResult result = interceptor.preUpdate(entity);
            if (result != ProcessResult.CONTINUE) {
                return result;
            }
        }
        return ProcessResult.CONTINUE;
    }

    @Override
    public ProcessResult preDelete(IOrmEntity entity) {
        for (IOrmInterceptor interceptor : interceptors) {
            ProcessResult result = interceptor.preDelete(entity);
            if (result != ProcessResult.CONTINUE) {
                return result;
            }
        }
        return ProcessResult.CONTINUE;
    }

    @Override
    public void postReset(IOrmEntity entity) {
        for (IOrmInterceptor interceptor : interceptors) {
            interceptor.postReset(entity);
        }
    }

    @Override
    public void postSave(IOrmEntity entity) {
        for (IOrmInterceptor interceptor : interceptors) {
            interceptor.postSave(entity);
        }
    }

    @Override
    public void postUpdate(IOrmEntity entity) {
        for (IOrmInterceptor interceptor : interceptors) {
            interceptor.postUpdate(entity);
        }
    }

    @Override
    public void postDelete(IOrmEntity entity) {
        for (IOrmInterceptor interceptor : interceptors) {
            interceptor.postDelete(entity);
        }
    }

    @Override
    public void postLoad(IOrmEntity entity) {
        for (IOrmInterceptor interceptor : interceptors) {
            interceptor.postLoad(entity);
        }
    }

    @Override
    public void preFlush() {
        for (IOrmInterceptor interceptor : interceptors) {
            interceptor.preFlush();
        }
    }

    @Override
    public void postFlush(Throwable exception) {
        for (IOrmInterceptor interceptor : interceptors) {
            interceptor.postFlush(exception);
        }
    }


    public boolean isEmpty() {
        return interceptors.isEmpty();
    }

    public List<IOrmInterceptor> getInterceptors() {
        return Collections.unmodifiableList(interceptors);
    }
}