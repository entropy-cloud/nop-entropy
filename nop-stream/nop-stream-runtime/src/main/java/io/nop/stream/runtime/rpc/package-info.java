/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
/**
 * Control-plane RPC: coordinator / task service interfaces and the RPC proxy/server wiring over the message bus. Wire DTOs live in the zero-out-edge {@code protocol} leaf — this package must not re-declare transport types (WI6 cycle break).
 */
package io.nop.stream.runtime.rpc;
