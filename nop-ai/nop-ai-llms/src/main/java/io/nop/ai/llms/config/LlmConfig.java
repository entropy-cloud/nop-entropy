/*
 *  Copyright (c) 2023-2025, Agents-Flex (fuhai999@gmail.com).
 *  <p>
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  <p>
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  <p>
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.nop.ai.llms.config;

import io.nop.api.core.annotations.data.DataBean;

import java.io.Serializable;

@DataBean
public class LlmConfig implements Serializable {

    private String model;

    private String baseUrl;

    private String apiKey;

    private boolean debug;

    private String chatUrl = "/chat/completions";

    /**
     * 限制每秒请求次数
     */
    private double rateLimit = 100;

    /**
     * 请求失败后重试次数
     */
    private int retryTimes = 3;

    private boolean logMessage = true;

    public boolean isLogMessage() {
        return logMessage;
    }

    public void setLogMessage(boolean logMessage) {
        this.logMessage = logMessage;
    }

    public double getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(double rateLimit) {
        this.rateLimit = rateLimit;
    }

    public int getRetryTimes() {
        return retryTimes;
    }

    public void setRetryTimes(int retryTimes) {
        this.retryTimes = retryTimes;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getChatUrl() {
        return chatUrl;
    }

    public void setChatUrl(String chatUrl) {
        this.chatUrl = chatUrl;
    }
}
