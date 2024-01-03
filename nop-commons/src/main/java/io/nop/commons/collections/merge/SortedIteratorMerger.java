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

import io.nop.commons.collections.iterator.IPeekingIterator;
import io.nop.commons.collections.iterator.PeekingIterator;
import io.nop.commons.util.IoHelper;

import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;

// Copied from Apache Kylin

/**
 * a merger that utilizes the sorted nature of input iterators
 */
public class SortedIteratorMerger<E> {

    private Iterator<? extends Iterator<E>> shardSubsets;
    private Comparator<E> comparator;

    public SortedIteratorMerger(Iterator<? extends Iterator<E>> shardSubsets, Comparator<E> comparator) {
        this.shardSubsets = shardSubsets;
        this.comparator = comparator;
    }

    public Iterator<E> getIterator() {
        final PriorityQueue<IPeekingIterator<E>> heap = new PriorityQueue<>(11,
                (o1, o2) -> comparator.compare(o1.peek(), o2.peek()));

        while (shardSubsets.hasNext()) {
            Iterator<E> iterator = shardSubsets.next();
            IPeekingIterator<E> peekingIterator = PeekingIterator.forPeeking(iterator);
            if (peekingIterator.hasNext()) {
                heap.offer(peekingIterator);
            }
        }

        return getIteratorInternal(heap);
    }

    protected Iterator<E> getIteratorInternal(PriorityQueue<IPeekingIterator<E>> heap) {
        return new MergedIterator<>(heap);
    }

    private static class MergedIterator<E> implements Iterator<E>, AutoCloseable {

        private final PriorityQueue<IPeekingIterator<E>> heap;
        // private final Comparator<E> comparator;

        MergedIterator(PriorityQueue<IPeekingIterator<E>> heap) {
            this.heap = heap;
            // this.comparator = comparator;
        }

        public void close() {
            do {
                IPeekingIterator<E> it = heap.poll();
                if (it != null) {
                    IoHelper.safeClose(it);
                } else {
                    break;
                }
            } while (true);
        }

        @Override
        public boolean hasNext() {
            return !heap.isEmpty();
        }

        @Override
        public E next() {
            if(!hasNext())
                throw new NoSuchElementException();

            IPeekingIterator<E> poll = heap.poll();
            E current = poll.next();
            if (poll.hasNext()) {

                // Guard.assertTrue(comparator.compare(current, poll.peek()) <
                // 0,
                // "Not sorted! current: " + current + " Next: " + poll.peek());

                heap.offer(poll);
            }
            return current;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

    }

}
