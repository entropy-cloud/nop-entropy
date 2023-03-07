/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nop.cluster.discovery;

import io.nop.api.core.util.FutureHelper;
import io.nop.api.core.util.IOrdered;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Represents read operations commonly available to discovery services such as Netflix Eureka or consul.io.
 *
 * @author Spencer Gibb
 * @author Olga Maciaszek-Sharma
 */
public interface IDiscoveryClient extends IOrdered {
    /**
     * Gets all ServiceInstances associated with a particular serviceId.
     *
     * @param serviceName The serviceId to query.
     * @return A List of ServiceInstance. 返回的列表可以被修改
     */
    List<ServiceInstance> getInstances(String serviceName);

    default CompletionStage<List<ServiceInstance>> getInstancesAsync(String serviceName) {
        return FutureHelper.futureCall(() -> getInstances(serviceName));
    }

    /**
     * @return All known service IDs.
     */
    List<String> getServices();
}