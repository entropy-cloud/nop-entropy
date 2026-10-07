/*
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 */
package io.nop.stream.format.avro;

/**
 * WI18 (plan 2341): module-level exception for the Avro format layer (typed error
 * face: registry unreachable, invalid wire format, schema evolution failure).
 */
public class StreamAvroException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public StreamAvroException(String message) {
        super(message);
    }

    public StreamAvroException(String message, Throwable cause) {
        super(message, cause);
    }
}
