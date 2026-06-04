/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.nop.stream.cep.nfa.sharedbuffer;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implements locking logic for incoming event and {@link SharedBufferNode} using a lock reference
 * counter.
 *
 * <p><b>Thread Safety:</b> The {@code refCounter} is backed by an {@link AtomicInteger}.
 * {@link #lock()} and {@link #release()} are thread-safe for concurrent access.
 * Note: compound check-then-act sequences (e.g., lock-then-check) may still require
 * external synchronization if atomicity across multiple operations is needed.
 */
public final class Lockable<T> {

    private final AtomicInteger refCounter;

    private final T element;

    public Lockable(T element, int refCounter) {
        this.refCounter = new AtomicInteger(refCounter);
        this.element = element;
    }

    public void lock() {
        refCounter.incrementAndGet();
    }

    /**
     * Releases lock on this object. If no more locks are acquired on it, this method will return
     * true.
     *
     * @return true if no more locks are acquired
     */
    boolean release() {
        int old;
        do {
            old = refCounter.get();
            if (old <= 0) {
                refCounter.set(0);
                throw new IllegalStateException("Lockable over-release: refCounter went negative");
            }
        } while (!refCounter.compareAndSet(old, old - 1));
        return old == 1;
    }

    public T getElement() {
        return element;
    }

    int getRefCounter() {
        return refCounter.get();
    }

    @Override
    public String toString() {
        return "Lock{" + "refCounter=" + refCounter.get() + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Lockable<?> lockable = (Lockable<?>) o;
        return refCounter.get() == lockable.refCounter.get() && Objects.equals(element, lockable.element);
    }

    @Override
    public int hashCode() {
        return Objects.hash(refCounter.get(), element);
    }

}
