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
package io.nop.commons.collections.merge;

import io.nop.commons.util.IoHelper;

import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;

public class SortedIteratorMerger<E> implements Iterator<E>, AutoCloseable {
    private final PriorityQueue<Element<E>> priorityQueue;

    public SortedIteratorMerger(Iterable<Iterator<? extends E>> iterators, Comparator<E> comparator) {
        priorityQueue = new PriorityQueue<>((c1, c2) -> comparator.compare(c1.value, c2.value));
        for (Iterator<? extends E> iterator : iterators) {
            if (iterator.hasNext()) {
                priorityQueue.add(new Element<>(iterator.next(), iterator));
            }
        }
    }

    @Override
    public void close() {
        do {
            Element<E> it = priorityQueue.poll();
            if (it != null) {
                IoHelper.safeClose(it.iterator);
            } else {
                break;
            }
        } while (true);
    }

    @Override
    public boolean hasNext() {
        return !priorityQueue.isEmpty();
    }

    @Override
    public E next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        Element<E> current = priorityQueue.poll();
        E value = current.value;
        if (current.iterator.hasNext()) {
            priorityQueue.add(new Element<>(current.iterator.next(), current.iterator));
        }
        return value;
    }

    protected static class Element<E> {
        E value;
        Iterator<? extends E> iterator;

        Element(E value, Iterator<? extends E> iterator) {
            this.value = value;
            this.iterator = iterator;
        }
    }
}