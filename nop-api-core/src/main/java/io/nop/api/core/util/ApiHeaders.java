/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.api.core.util;

import io.nop.api.core.ApiConstants;
import io.nop.api.core.ApiErrors;
import io.nop.api.core.beans.ApiMessage;
import io.nop.api.core.convert.ConvertHelper;
import io.nop.api.core.exceptions.NopException;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.nop.api.core.ApiErrors.ARG_HEADER;
import static io.nop.api.core.ApiErrors.ARG_LOCALE;
import static io.nop.api.core.ApiErrors.ERR_API_INVALID_LOCALE_HEADER;
import static io.nop.api.core.util.ApiStringHelper.onlyChars;

/**
 * 定义公共的消息头字段
 */
public class ApiHeaders {

    public static void setHeader(Map<String, Object> headers, String name, Object value) {
        if (ApiStringHelper.isEmptyObject(value)) {
            headers.remove(name);
        } else {
            headers.put(name, value);
        }
    }

    public static Map<String, Object> getHeaders(Map<String, Object> headers, Collection<String> headerNames) {
        if (headerNames == null || headerNames.isEmpty())
            return null;
        if (headers == null || headers.isEmpty())
            return null;

        Map<String, Object> ret = new HashMap<>();
        for (String headerName : headerNames) {
            Object value = headers.get(headerName);
            if (value != null)
                ret.put(headerName, value);
        }
        return ret;
    }

    public static Object getHeader(Map<String, Object> headers, String name) {
        if (headers == null)
            return null;
        Object value = headers.get(name);
        if (value instanceof List) {
            List<Object> list = (List<Object>) value;
            if (list.isEmpty())
                return null;
            return list.get(0);
        }
        return value;
    }

    public static List<Object> getListHeader(Map<String, Object> headers, String name) {
        if (headers == null)
            return null;
        Object value = headers.get(name);
        if (value == null)
            return null;
        if (value instanceof List)
            return (List) value;
        return Collections.singletonList(value);
    }

    public static String getStringHeader(Map<String, Object> headers, String name) {
        Object value = getHeader(headers, name);
        return ConvertHelper.toString(value);
    }

    public static int getIntHeader(Map<String, Object> headers, String name, int defaultValue) {
        Integer value =
                ConvertHelper.toInt(getHeader(headers, name),
                        code -> new NopException(ApiErrors.ERR_MESSAGE_HEADER_INVALID_INTEGER)
                                .param(ARG_HEADER, name));
        if (value == null)
            return defaultValue;
        return value;
    }

    public static String getVersion(ApiMessage message) {
        return getStringHeader(message.getHeaders(), ApiConstants.HEADER_VERSION);
    }

    public static void setVersion(ApiMessage message, String version) {
        message.setHeader(ApiConstants.HEADER_VERSION, version);
    }


    public static String getLocale(ApiMessage message) {
        if (message == null)
            return null;
        return getStringHeader(message.getHeaders(), ApiConstants.HEADER_LOCALE);
    }

    public static void setLocale(ApiMessage message, String locale) {
        message.setHeader(ApiConstants.HEADER_LOCALE, locale);
    }

    public static String getTimeZone(ApiMessage message) {
        return getStringHeader(message.getHeaders(), ApiConstants.HEADER_TIMEZONE);
    }

    public static void setTimeZone(ApiMessage message, String value) {
        message.setHeader(ApiConstants.HEADER_TIMEZONE, value);
    }

    public static String getTenant(ApiMessage message) {
        return getStringHeader(message.getHeaders(), ApiConstants.HEADER_TENANT);
    }

    public static void setTenant(ApiMessage message, String value) {
        message.setHeader(ApiConstants.HEADER_TENANT, value);
    }

    public static String getShard(ApiMessage message) {
        return getStringHeader(message.getHeaders(), ApiConstants.HEADER_SHARD);
    }

    public static void setShard(ApiMessage message, String value) {
        message.setHeader(ApiConstants.HEADER_SHARD, value);
    }

    public static String getAuthToken(ApiMessage message) {
        return getStringHeader(message.getHeaders(), ApiConstants.HEADER_ACCESS_TOKEN);
    }

    public static void setAuthToken(ApiMessage message, String value) {
        message.setHeader(ApiConstants.HEADER_ACCESS_TOKEN, value);
    }

    public static String getCookie(ApiMessage message) {
        return getStringHeader(message.getHeaders(), ApiConstants.HEADER_COOKIE);
    }

    public static void setCookie(ApiMessage message, String value) {
        message.setHeader(ApiConstants.HEADER_COOKIE, value);
    }

    public static boolean isOneWay(ApiMessage message) {
        return "1".equals(getStringHeader(message.getHeaders(), ApiConstants.HEADER_ONE_WAY));
    }

    public static void setOneWay(ApiMessage message) {
        message.setHeader(ApiConstants.HEADER_ONE_WAY, "1");
    }

    public static String getIdFromHeaders(Map<String, Object> headers) {
        return getStringHeader(headers, ApiConstants.HEADER_ID);
    }

    public static String getId(ApiMessage message) {
        return getStringHeader(message.getHeaders(), ApiConstants.HEADER_ID);
    }

    public static void setId(ApiMessage message, String value) {
        message.setHeader(ApiConstants.HEADER_ID, value);
    }

    public static String getIdempotentFromHeaders(Map<String, Object> headers) {
        return getStringHeader(headers, ApiConstants.HEADER_IDEMPOTENT);
    }

    public static String getIdempotent(ApiMessage message) {
        return getStringHeader(message.getHeaders(), ApiConstants.HEADER_IDEMPOTENT);
    }

    public static void setIdempotent(ApiMessage message, String value) {
        message.setHeader(ApiConstants.HEADER_IDEMPOTENT, value);
    }

    public static String getRelId(ApiMessage message) {
        return getStringHeader(message.getHeaders(), ApiConstants.HEADER_REL_ID);
    }

    public static void setRelId(ApiMessage message, String value) {
        message.setHeader(ApiConstants.HEADER_REL_ID, value);
    }

    public static String getAppId(ApiMessage message) {
        return getStringHeader(message.getHeaders(), ApiConstants.HEADER_APP_ID);
    }

    public static void setAppId(ApiMessage message, String value) {
        message.setHeader(ApiConstants.HEADER_APP_ID, value);
    }


    public static String getTags(ApiMessage message) {
        return getStringHeader(message.getHeaders(), ApiConstants.HEADER_TAGS);
    }

    public static void setTags(ApiMessage message, String value) {
        message.setHeader(ApiConstants.HEADER_TAGS, value);
    }

    public static String getUserId(ApiMessage message) {
        return getStringHeader(message.getHeaders(), ApiConstants.HEADER_USER_ID);
    }

    public static void setUserId(ApiMessage message, String value) {
        message.setHeader(ApiConstants.HEADER_USER_ID, value);
    }

    public static void setTrace(ApiMessage message, String value) {
        message.setHeader(ApiConstants.HEADER_TRACE, value);
    }

    public static String getTrace(ApiMessage message, String value) {
        return getStringHeader(message.getHeaders(), ApiConstants.HEADER_TRACE);
    }

    public static String getHost(ApiMessage message) {
        return getStringHeader(message.getHeaders(), ApiConstants.HEADER_HOST);
    }

    public static void setHost(ApiMessage message, String value) {
        message.setHeader(ApiConstants.HEADER_HOST, value);
    }

    public static String getClientAddr(ApiMessage message) {
        return getStringHeader(message.getHeaders(), ApiConstants.HEADER_CLIENT_ADDR);
    }

    public static void setClientAddr(ApiMessage message, String value) {
        message.setHeader(ApiConstants.HEADER_CLIENT_ADDR, value);
    }

    public static String getClientIp(ApiMessage message) {
        String addr = getClientAddr(message);
        if (addr == null)
            return null;
        int pos = addr.indexOf(':');
        if (pos < 0)
            return null;
        return addr.substring(0, pos);
    }

    public static String getTaskId(ApiMessage message) {
        return getStringHeader(message.getHeaders(), ApiConstants.HEADER_TASK_ID);
    }

    public static void setTaskId(ApiMessage message, String value) {
        message.setHeader(ApiConstants.HEADER_TASK_ID, value);
    }

    public static String getTxnGroup(ApiMessage message) {
        return getStringHeader(message.getHeaders(), ApiConstants.HEADER_TXN_GROUP);
    }

    public static void setTxnGroup(ApiMessage message, String value) {
        message.setHeader(ApiConstants.HEADER_TXN_GROUP, value);
    }

    public static String getTxnId(ApiMessage message) {
        return getStringHeader(message.getHeaders(), ApiConstants.HEADER_TXN_ID);
    }

    public static void setTxnId(ApiMessage message, String value) {
        message.setHeader(ApiConstants.HEADER_TXN_ID, value);
    }

    public static String getTxnBranchId(ApiMessage message) {
        return getStringHeader(message.getHeaders(), ApiConstants.HEADER_TXN_BRANCH_ID);
    }

    public static void setTxnBranchId(ApiMessage message, String value) {
        message.setHeader(ApiConstants.HEADER_TXN_BRANCH_ID, value);
    }

    public static int getTxnBranchNo(ApiMessage message, int defaultValue) {
        return getIntHeader(message.getHeaders(), ApiConstants.HEADER_TXN_BRANCH_NO, defaultValue);
    }

    public static void setTxnBranchNo(ApiMessage message, Integer branchNo) {
        message.setHeader(ApiConstants.HEADER_TXN_BRANCH_NO, branchNo);
    }


    public static String getActor(ApiMessage message) {
        return getStringHeader(message.getHeaders(), ApiConstants.HEADER_ACTOR);
    }

    public static void setActor(ApiMessage message, String value) {
        message.setHeader(ApiConstants.HEADER_ACTOR, value);
    }


    public static String getAppZone(ApiMessage message) {
        return getStringHeader(message.getHeaders(), ApiConstants.HEADER_APP_ZONE);
    }

    public static void setAppZone(ApiMessage message, String value) {
        message.setHeader(ApiConstants.HEADER_APP_ZONE, value);
    }

    public static String getSvcName(ApiMessage message) {
        return getStringHeader(message.getHeaders(), ApiConstants.HEADER_SVC_NAME);
    }

    public static void setSvcName(ApiMessage message, String value) {
        message.setHeader(ApiConstants.HEADER_SVC_NAME, value);
    }

    public static String getSvcAction(ApiMessage message) {
        return getStringHeader(message.getHeaders(), ApiConstants.HEADER_SVC_ACTION);
    }

    public static void setSvcAction(ApiMessage message, String value) {
        message.setHeader(ApiConstants.HEADER_SVC_ACTION, value);
    }

    public static Map<String, String> getSvcRoute(ApiMessage message) {
        String route = getStringHeader(message.getHeaders(), ApiConstants.HEADER_SVC_ROUTE);
        if (route == null)
            return null;
        return ApiStringHelper.parseStringMap(route, ':', ',');
    }

    public static Map<String, String> parseRoute(String route) {
        return ApiStringHelper.parseStringMap(route, ':', ',');
    }

    public static void setSvcRoute(ApiMessage message, Map<String, String> route) {
        String str = ApiStringHelper.encodeStringMap(route, ':', ',');
        message.setHeader(ApiConstants.HEADER_SVC_ROUTE, str);
    }

    public static long getTimeout(ApiMessage message, long defaultValue) {
        Long value =
                ConvertHelper.toLong(getHeader(message.getHeaders(), ApiConstants.HEADER_TIMEOUT),
                        code -> new NopException(ApiErrors.ERR_MESSAGE_HEADER_INVALID_TIMEOUT_HEADER));
        if (value == null)
            return defaultValue;
        return value;
    }

    public static void setTimeout(ApiMessage message, Long timeout) {
        message.setHeader(ApiConstants.HEADER_TIMEOUT, timeout);
    }

    public static Set<String> getSvcTags(ApiMessage message) {
        String tags = getStringHeader(message.getHeaders(), ApiConstants.HEADER_SVC_TAGS);
        return ConvertHelper.toCsvSet(tags);
    }

    public static void setSvcTags(ApiMessage message, Set<String> tags) {
        message.setHeader(ApiConstants.HEADER_SVC_TAGS, ApiStringHelper.join(tags, ","));
    }


    public static String getBizKey(ApiMessage message) {
        return getStringHeader(message.getHeaders(), ApiConstants.HEADER_BIZ_KEY);
    }

    public static void setBizKey(ApiMessage message, String value) {
        message.setHeader(ApiConstants.HEADER_BIZ_KEY, value);
    }

//    public static boolean isBizFatal(ApiMessage message) {
//        return "1".equals(getStringHeader(message.getHeaders(), ApiConstants.HEADER_BIZ_FATAL));
//    }
//
//    public static void setBizFatal(ApiMessage message, boolean fatal) {
//        message.setHeader(ApiConstants.HEADER_BIZ_FATAL, fatal ? "1" : null);
//    }

    public static boolean isBizFail(ApiMessage message) {
        return "1".equals(getStringHeader(message.getHeaders(), ApiConstants.HEADER_BIZ_FAIL));
    }

    public static void setBizFail(ApiMessage message, boolean fail) {
        message.setHeader(ApiConstants.HEADER_BIZ_FAIL, fail ? "1" : null);
    }

    public static void checkLocaleFormat(String locale) {
        if (!onlyChars(locale, true, false, "-_"))
            throw new NopException(ERR_API_INVALID_LOCALE_HEADER)
                    .param(ARG_LOCALE, locale);
    }
}