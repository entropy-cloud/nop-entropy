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
package io.nop.commons.io.stream;

// 从esProc项目拷贝的代码

//package com.scudata.dm;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 可以改变输出位置的输出流
 *
 * @author WangXiaoJun
 */
public abstract class RandomOutputStream extends OutputStream {
    /**
     * 设置输出位置
     *
     * @param newPosition
     * @throws IOException
     */
    public abstract void position(long newPosition) throws IOException;

    /**
     * 返回当前输出位置
     *
     * @return
     * @throws IOException
     */
    public abstract long position() throws IOException;

    /**
     * 如果锁定成功返回true
     *
     * @return
     * @throws IOException
     */
    public abstract boolean tryLock() throws IOException;

    /**
     * 等待锁，直到锁成功
     *
     * @return boolean
     * @throws IOException
     */
    public boolean lock() throws IOException {
        return true;
    }

    /**
     * 取从指定位置开始的输入流
     *
     * @param pos 位置
     * @return InputStream
     * @throws IOException
     */
    public InputStream getInputStream(long pos) throws IOException {
        return null;
    }
}