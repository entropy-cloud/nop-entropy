/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
/**
 * Shared checkpoint kernel used by BOTH driver faces (WI10 plan 2426): manifest-first restore gating ({@link io.nop.stream.runtime.checkpoint.core.RestoreGates}) and savepoint materialization ({@link io.nop.stream.runtime.checkpoint.core.SavepointMaterializer}).
 *
 * <p>This package must not depend on the coordinator or execution packages — both faces depend on it.
 */
package io.nop.stream.runtime.checkpoint.core;
