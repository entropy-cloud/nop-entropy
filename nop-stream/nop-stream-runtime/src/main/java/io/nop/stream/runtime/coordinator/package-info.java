/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
/**
 * Distributed driver face: {@link io.nop.stream.runtime.coordinator.JobCoordinator} (assignment, periodic checkpoint triggering, failure detection / global recovery, fencing epochs) and its {@link io.nop.stream.runtime.coordinator.AssignmentPlanner}. Depends on the checkpoint kernel, not on TM internals.
 */
package io.nop.stream.runtime.coordinator;
