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
package io.nop.demo.spring.batch;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.function.Supplier;

public class MybatisFix {
    /**
     * Mybatis对于Spring事务的识别不正确。当Propagation=NEVER时实际没有打开事务，但是Mybatis仍然会注册synchronization，导致无法及时释放连接
     */
    public static <V> V runWithoutTransaction(Supplier<V> task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
            TransactionSynchronizationManager.clearSynchronization();
            try {
                return task.get();
            } finally {
                TransactionSynchronizationManager.initSynchronization();
                syncs.forEach(TransactionSynchronizationManager::registerSynchronization);
            }
        } else {
            return task.get();
        }
    }
}
