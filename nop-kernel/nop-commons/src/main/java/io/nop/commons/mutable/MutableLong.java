/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.nop.commons.mutable;

// modify from commons-lang3 implementation

/**
 * A mutable <code>long</code> wrapper.
 * <p>
 * Note that as MutableLong does not extend Long, it is not treated by String.format as a Long parameter.
 *
 * @see Long
 * @since 2.1
 */
public class MutableLong extends Number implements Comparable<MutableLong>, IMutableValue<Number> {

    /**
     * Required for serialization support.
     *
     * @see java.io.Serializable
     */
    private static final long serialVersionUID = 62986528375L;

    /**
     * The mutable value.
     */
    private long value;

    /**
     * Constructs a new MutableLong with the default value of zero.
     */
    public MutableLong() {
        super();
    }

    /**
     * Constructs a new MutableLong with the specified value.
     *
     * @param value the initial value to store
     */
    public MutableLong(final long value) {
        super();
        this.value = value;
    }

    /**
     * Constructs a new MutableLong with the specified value.
     *
     * @param value the initial value to store, not null
     * @throws NullPointerException if the object is null
     */
    public MutableLong(final Number value) {
        super();
        this.value = value.longValue();
    }

    /**
     * Constructs a new MutableLong parsing the given string.
     *
     * @param value the string to parse, not null
     * @throws NumberFormatException if the string cannot be parsed into a long
     * @since 2.5
     */
    public MutableLong(final String value) throws NumberFormatException {
        super();
        this.value = Long.parseLong(value);
    }

    // -----------------------------------------------------------------------

    /**
     * Gets the value as a Long instance.
     *
     * @return the value as a Long, never null
     */
    @Override
    public Long getValue() {
        return this.value;
    }

    /**
     * Sets the value from any Number instance.
     *
     * @param value the value to set, not null
     * @throws NullPointerException if the object is null
     */
    @Override
    public void setValue(final Number value) {
        this.value = value == null ? 0 : value.longValue();
    }

    // -----------------------------------------------------------------------

    /**
     * Gets the current value.
     *
     * @return the current value
     */
    public final long get() {
        return value;
    }

    /**
     * Sets to the given value.
     *
     * @param newValue the new value
     */
    public final void set(long newValue) {
        value = newValue;
    }

    /**
     * sets to the given value and returns the old value.
     *
     * @param newValue the new value
     * @return the previous value
     */
    public final long getAndSet(long newValue) {
        long oldValue = this.value;
        this.value = newValue;
        return oldValue;
    }

    /**
     * sets the value to the given updated value if the current value {@code ==} the expected value.
     *
     * @param expect the expected value
     * @param update the new value
     * @return {@code true} if successful. False return indicates that the actual value was not equal to the expected
     * value.
     */
    public final boolean compareAndSet(long expect, long update) {
        if (this.value == expect) {
            this.value = update;
            return true;
        }
        return false;
    }

    /**
     * increments by one the current value.
     *
     * @return the previous value
     */
    public final long getAndIncrement() {
        return this.value++;
    }

    /**
     * decrements by one the current value.
     *
     * @return the previous value
     */
    public final long getAndDecrement() {
        return this.value--;
    }

    /**
     * adds the given value to the current value.
     *
     * @param delta the value to add
     * @return the previous value
     */
    public final long getAndAdd(long delta) {
        long value = this.value;
        this.value += delta;
        return value;
    }

    /**
     * increments by one the current value.
     *
     * @return the updated value
     */
    public final long incrementAndGet() {
        return ++value;
    }

    /**
     * Atomically decrements by one the current value.
     *
     * @return the updated value
     */
    public final long decrementAndGet() {
        return --value;
    }

    /**
     * Atomically adds the given value to the current value.
     *
     * @param delta the value to add
     * @return the updated value
     */
    public final long addAndGet(long delta) {
        return value += delta;
    }

    // -----------------------------------------------------------------------
    // shortValue and byteValue rely on Number implementation

    /**
     * Returns the value of this MutableInt as an int.
     *
     * @return the numeric value represented by this object after conversion to type int.
     */
    @Override
    public int intValue() {
        return (int) value;
    }

    /**
     * Returns the value of this MutableInt as a long.
     *
     * @return the numeric value represented by this object after conversion to type long.
     */
    @Override
    public long longValue() {
        return value;
    }

    /**
     * Returns the value of this MutableInt as a float.
     *
     * @return the numeric value represented by this object after conversion to type float.
     */
    @Override
    public float floatValue() {
        return value;
    }

    /**
     * Returns the value of this MutableInt as a double.
     *
     * @return the numeric value represented by this object after conversion to type double.
     */
    @Override
    public double doubleValue() {
        return value;
    }

    // -----------------------------------------------------------------------

    /**
     * Gets this mutable as an instance of Integer.
     *
     * @return a Integer instance containing the value from this mutable, never null
     */
    public Integer toInteger() {
        return intValue();
    }

    public Long toLong() {
        return value;
    }

    // -----------------------------------------------------------------------

    /**
     * Compares this object to the specified object. The result is <code>true</code> if and only if the argument is not
     * <code>null</code> and is a <code>MutableInt</code> object that contains the same <code>int</code> value as this
     * object.
     *
     * @param obj the object to compare with, null returns false
     * @return <code>true</code> if the objects are the same; <code>false</code> otherwise.
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this)
            return true;

        if (obj instanceof MutableLong) {
            return value == ((MutableLong) obj).longValue();
        }
        return false;
    }

    /**
     * Returns a suitable hash code for this mutable.
     *
     * @return a suitable hash code
     */
    @Override
    public int hashCode() {
        return (int) (value ^ (value >>> 32));
    }

    // -----------------------------------------------------------------------

    /**
     * Compares this mutable to another in ascending order.
     *
     * @param other the other mutable to compare to, not null
     * @return negative if this is less, zero if equal, positive if greater
     */
    @Override
    public int compareTo(final MutableLong other) {
        final long anotherVal = other.value;
        return Long.compare(value, anotherVal);
    }

    // -----------------------------------------------------------------------

    /**
     * Returns the String value of this mutable.
     *
     * @return the mutable value as a string
     */
    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
