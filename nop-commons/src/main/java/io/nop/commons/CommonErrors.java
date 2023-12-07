/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.commons;

import io.nop.api.core.annotations.core.Locale;
import io.nop.api.core.exceptions.ErrorCode;

import static io.nop.api.core.ApiErrors.ARG_CLASS_NAME;
import static io.nop.api.core.ApiErrors.ARG_EXPECTED_TYPE;
import static io.nop.api.core.ApiErrors.ARG_VALUE;
import static io.nop.api.core.exceptions.ErrorCode.define;

@Locale("zh-CN")
public interface CommonErrors {
    String ARG_START_LOC = "startLoc";
    String ARG_EXPECTED = "expected";
    String ARG_START = "start";
    String ARG_LIMIT = "limit";
    String ARG_LENGTH = "length";
    String ARG_V1 = "v1";
    String ARG_V2 = "v2";
    String ARG_HOST = "host";
    String ARG_PEEK_COUNT = "peekCount";
    String ARG_READER_STATE = "readerState";
    String ARG_POS = "pos";
    String ARG_CUR = "cur";
    String ARG_EOF = "eof";
    String ARG_CLASS = "class";
    String ARG_SERVICE = "service";
    String ARG_COUNT = "count";
    String ARG_HIGH_WATERMARK = "highWatermark";
    String ARG_ATTR_NAME = "attrName";
    String ARG_ACTION = "action";

    String ARG_TEXT = "text";

    String ARG_EXPECTED_COUNT = "expectedCount";

    String ARG_RESOURCE_ID = "resourceId";
    String ARG_EXPIRE_TIME = "expireTime";
    String ARG_LEASE_TIME = "leaseTime";
    String ARG_WAIT_TIME = "waitTime";
    String ARG_HOLDER_ID = "holderId";
    String ARG_RESOURCE_IDS = "resourceIds";

    String ARG_INDEX = "index";
    String ARG_NAME = "name";
    String ARG_KEY = "key";

    String ARG_MARKER = "marker";
    String ARG_PREV_MARKER = "prevMarker";

    String ARG_IP = "ip";
    String ARG_DEST = "dest";

    String ARG_STR = "str";

    String ARG_URL = "url";

    String ARG_PATH = "path";

    String ARG_CACHE_NAME = "cacheName";

    String ARG_QUERY = "query";
    String ARG_PARAM_NAME = "paramName";

    String ARG_MAX_SIZE = "maxSize";

    ErrorCode ERR_IO_UNEXPECTED_EOF = define("nop.err.commons.io.unexpected-eof", "数据流已关闭，无法读取到更多数据");

    ErrorCode ERR_IO_NOT_FIND_EXPECTED_BYTE = define("nop.err.commons.io.not-find-expected-byte",
            "数据流已结束，没有读取到期待的字节[{expected}]", ARG_EXPECTED);

    ErrorCode ERR_IO_PEEK_COUNT_EXCEED_LIMIT = define("nop.err.commons.io.peek-count-exceed-limit",
            "内部缓冲区不支持向前查看[{peekCount}]个字符", ARG_PEEK_COUNT);

    ErrorCode ERR_IO_INPUT_SIZE_EXCEED_LIMIT = define("nop.err.commons.io.input-size-exceed-limit",
            "数据流长度超过限制[{limit}]", ARG_LIMIT);

    ErrorCode ERR_IO_COPY_DEST_NOT_FILE = define("nop.err.commons.io.copy-dest-not-file", "文件拷贝目标已经存在，且不是文件，无法覆盖",
            ARG_DEST);

    ErrorCode ERR_IO_COPY_DEST_NOT_DIRECTORY = define("nop.err.commons.io.copy-dest-not-directory",
            "目录拷贝的目的地已经存在，且不是目录", ARG_DEST);

    ErrorCode ERR_IO_CREATE_FILE_FAIL = define("nop.err.commons.io.create-file-fail",
            "创建文件失败:{path}", ARG_PATH);

    ErrorCode ERR_IO_URL_NOT_RESOLVE_TO_FILE = define("nop.err.commons.io.url-not-resolve-to-file",
            "URL无法被转换为文件对象:{url}", ARG_URL);

    ErrorCode ERR_IO_STREAM_SIZE_EXCEED_LIMIT = define("nop.err.commons.io.size-exceed-limit",
            "数据流长度超过最大限制:{maxSize}", ARG_MAX_SIZE);

    ErrorCode ERR_SCAN_READ_FAIL = define("nop.err.commons.text.scan-read-fail", "读取数据失败", ARG_READER_STATE);

    ErrorCode ERR_SCAN_UNEXPECTED_CHAR = define("nop.err.commons.text.scan-unexpected-char",
            "读取到的下一个字符不是期待的字符[{expected}], 当前位置:{readerState}", ARG_READER_STATE, ARG_EXPECTED);

    ErrorCode ERR_SCAN_UNEXPECTED_STR = define("nop.err.commons.text.scan-unexpected-str",
            "读取到的后续字符串不是期待的字符串[{expected}], 当前位置:{readerState}", ARG_READER_STATE, ARG_EXPECTED);

    ErrorCode ERR_SCAN_UNEXPECTED_TOKEN = define("nop.err.commons.text.scan-unexpected-token",
            "读取到的后续字符串不是期待的标识符[{expected}], 当前位置:{readerState}", ARG_READER_STATE, ARG_EXPECTED);

    ErrorCode ERR_SCAN_INVALID_CHAR = define("nop.err.commons.text.scan-invalid-char", "非法的字符[{cur}]", ARG_CUR);

    ErrorCode ERR_SCAN_NOT_DIGIT = define("nop.err.commons.text.scan-not-digit", "不是合法的数字字符[{cur}]", ARG_CUR);

    ErrorCode ERR_SCAN_NOT_HEX_CHAR = define("nop.err.commons.text.scan-not-hex-char", "不是合法的十六进制数字字符[{cur}]", ARG_CUR);


    ErrorCode ERR_SCAN_BLANK_EXPECTED = define("nop.err.commons.text.scan-blank-expected", "当前字符不是空白分隔符", ARG_CUR);

    ErrorCode ERR_SCAN_TOKEN_END_EXPECTED = define("nop.err.commons.text.scan-token-end-expected",
            "符号之间缺少分隔符。当前位置:{readerState}", ARG_READER_STATE);

    ErrorCode ERR_SCAN_NOT_END_PROPERLY = define("nop.err.commons.text.scan-not-end-properly", "遇到非法字符，解析失败");

    ErrorCode ERR_SCAN_TOKEN_INCOMPLETE = define("nop.err.commons.text.scan-token-incomplete", "符号未正常结束");

    ErrorCode ERR_SCAN_NEXT_UNTIL_UNEXPECTED_EOF = define("nop.err.commons.text.scan-next-until-unexpected-eof",
            "没有找到匹配的字符[{expected}]", ARG_EXPECTED);

    ErrorCode ERR_SCAN_COMMENT_UNEXPECTED_EOF = define("nop.err.commons.text.scan-comment-not-end",
            "没有找到注释的结束标记[{expected}]", ARG_EXPECTED);

    ErrorCode ERR_SCAN_ILLEGAL_ESCAPE_CHAR = define("nop.err.commons.text.scan-illegal-escape-char",
            "非法的转义字符[{cur}], 当前位置:{readerState}", ARG_CUR, ARG_READER_STATE);

    ErrorCode ERR_SCAN_INVALID_ESCAPE_UNICODE = define("nop.err.commons.text.scan-invalid-escape-unicode",
            "非法的Unicode转义字符[{cur}], 当前位置:{readerState}", ARG_CUR, ARG_READER_STATE);

    ErrorCode ERR_SCAN_STRING_NOT_END = define("nop.err.commons.text.scan-string-not-end", "字符串没有正常结束");

    ErrorCode ERR_SCAN_INVALID_HEX_INT_STRING = define("nop.err.commons.text.scan-invalid-hex-int-string",
            "非法的16进制整数字符串[{value}]", ARG_VALUE);

    ErrorCode ERR_SCAN_INVALID_LONG_STRING = define("nop.err.commons.text.scan-invalid-long-string",
            "非法的整数字符串[{value}]", ARG_VALUE);

    ErrorCode ERR_SCAN_INVALID_FLOAT_STRING = define("nop.err.commons.text.scan-invalid-float-string",
            "非法的单精度浮点数[{value}]", ARG_VALUE);

    ErrorCode ERR_SCAN_INVALID_DOUBLE_STRING = define("nop.err.commons.text.scan-invalid-double-string",
            "非法的双精度浮点数[{value}]", ARG_VALUE);

    ErrorCode ERR_SCAN_INVALID_NUMBER_STRING = define("nop.err.commons.text.scan-invalid-number-string",
            "非法的数字字符串[{value}]", ARG_VALUE);

    ErrorCode ERR_SCAN_NUMBER_NOT_INT = define("nop.err.commons.text.scan-number-not-init", "解析到的数字[{value}]不是整数类型",
            ARG_VALUE);

    ErrorCode ERR_SCAN_INVALID_VAR = define("nop.err.commons.text.scan-invalid-var",
            "非法的变量名称[{value}], 当前位置:{readerState}", ARG_VALUE, ARG_READER_STATE);

    ErrorCode ERR_SCAN_INVALID_PROP_PATH = define("nop.err.commons.text.scan-invalid-prop-path",
            "非法的属性名称[{value}], 当前位置:{readerState}", ARG_VALUE, ARG_READER_STATE);

    ErrorCode ERR_SCAN_INVALID_XML_NAME = define("nop.err.commons.text.scan-invalid-xml-name", "非法的XML名称[{value}]",
            ARG_VALUE);

    ErrorCode ERR_SCAN_NOT_ALLOW_TWO_SEPARATOR_IN_XML_NAME = define(
            "nop.err.commons.text" + ".scan-not-allow-two-separator-in-xml-name", "Nop的XML名称中不允许连续的.:-分隔符[{value}]",
            ARG_VALUE);

    ErrorCode ERR_TEXT_BUF_START_EXCEED_LIMIT = define("nop.err.commons.text.buf-start-exceed-limit",
            "参数start的值不能超过limit[{limit}]", ARG_START, ARG_LIMIT);

    ErrorCode ERR_TEXT_BUF_LIMIT_EXCEED_LENGTH = define("nop.err.commons.text.buf-limit-exceed-length",
            "参数limit[{limit}]的值不能超过字符串数组的长度[{length}]", ARG_LIMIT, ARG_LENGTH);

    ErrorCode ERR_TEXT_ILLEGAL_HEX_STRING = define("nop.err.commons.text.illegal-hex-string", "不是合法的十六进制字符串");

    ErrorCode ERR_TEXT_NUMBER_STARTS_WITH_ZERO = define("nop.err.commons.text.number-starts-with-zero",
            "第一个字符为0的数字表示，例如03");

    ErrorCode ERR_TEXT_TRIE_KEY_ALREADY_EXISTS = define("nop.err.commons.text.trie-key-already-exists", "Trie树的key不能重复",
            ARG_KEY);

    ErrorCode ERR_TEXT_MARKER_POS_CONFLICT = define("nop.err.commons.text.marker-pos-conflict", "标记对象的位置不能重叠",
            ARG_MARKER, ARG_PREV_MARKER);

    ErrorCode ERR_TEXT_MAKER_COUNT_MISMATCH = define("nop.err.commons.text.marker-count-mismatch", "标记对象的个数不符合预期",
            ARG_COUNT);

    ErrorCode ERR_TEXT_INVALID_MARKER_RANGE = define("nop.err.commons.text.invalid-marker-range", "标记对象的位置超出了文本范围",
            ARG_MARKER, ARG_LENGTH);

    ErrorCode ERR_TEXT_INVALID_UNICODE = define("nop.err.commons.text.invalid-unicode", "非法的unicode字符编码", ARG_STR);

    ErrorCode ERR_TEXT_INVALID_UUID_RANGE = define("nop.err.commons.text.invalid-uuid-range", "uuid的长度必须是1到64字节之间",
            ARG_LENGTH);

    ErrorCode ERR_MATH_NOT_COMPARABLE = define("nop.err.commons.math.not-comparable",
            "v1[{v1}]或者v2[{v2}]非数字类型，它们之间无法比较", ARG_V1, ARG_V2);

    ErrorCode ERR_NET_UNKNOWN_HOST = define("nop.err.commons.net.unknown-host", "解析域名[{host}]失败", ARG_HOST);

    ErrorCode ERR_COLLECTIONS_NOT_SUPPORT_STREAM = define("nop.err.commons.collections.not-support-stream",
            "不支持转化为Stream接口", ARG_VALUE, ARG_CLASS);

    ErrorCode ERR_COLLECTIONS_CAN_NOT_TRANSFORM_TO_ITERATOR = define(
            "nop.err.commons.collections.can-not-transform-to-iterator", "不支持转化为Iterator接口", ARG_VALUE, ARG_CLASS);

    ErrorCode ERR_COLLECTIONS_NOT_LIST = define("nop.err.commons.collections.not-list", "类型为[{class}]的对象不能被转换为集合类型",
            ARG_CLASS);

    ErrorCode ERR_COLLECTIONS_ITERATOR_EOF = define("nop.err.commons.collections.iterator-eof", "迭代器已经到达尾部，不能继续移动");

    ErrorCode ERR_CONCURRENT_STOP_POOLED_THREAD = define("nop.err.commons.concurrent.stop-pooled-thread", "停止线程池中的线程");

    ErrorCode ERR_CONCURRENT_CYCLE_ALREADY_BEGIN = define("nop.err.concurrent.cycle-already-begin",
            "正在处理过程中，不能再次启动一个处理过程");

    ErrorCode ERR_SERVICE_NOT_ALLOW_START_AFTER_STOP = define("nop.err.commons.service.not-allow-start-after-stopped",
            "服务[{service}]停止后不允许再次启动", ARG_SERVICE);

    ErrorCode ERR_SERVICE_NOT_ACTIVE = define("nop.err.commons.service.not-active",
            "服务[{service}]现在不处于active状态，无法对外提供服务", ARG_SERVICE);

    ErrorCode ERR_QUEUE_FULL = define("nop.err.commons.concurrent.queue-full", "队列已满");

    ErrorCode ERR_RATE_LIMIT_ACQUIRE_COUNT_EXCEED_LIMIT = define(
            "nop.err.commons.concurrent.rate-limiter-acquire-count-must-be-less-than-high-watermark",
            "尝试获取的资源数[{count}]必须比高水位值[{highWatermark}]小", ARG_COUNT, ARG_HIGH_WATERMARK);

    ErrorCode ERR_PARTIAL_FUNCTION_NOT_DEFINED = define(
            "nop.err.commons.functional.partition-function-is-not-defined-at", "没有找到针对当前参数[{class}]的函数定义", ARG_CLASS);

    ErrorCode ERR_UNDEFINED_HANDLER_FOR_REQUEST_TYPE = define(
            "nop.err.commons.functional.undefined-handler-for-request-type", "没有找到针对请求类型[{class}]的函数定义", ARG_CLASS);

    ErrorCode ERR_UNDEFINED_HANDLER_FOR_REQUEST_ACTION = define(
            "nop.err.commons.functional.undefined-handler-for-request-action", "没有找到针对请求[{action}]的函数定义", ARG_ACTION);

    ErrorCode ERR_NULL_ATTRIBUTE_VALUE = define("nop.err.commons.lang.null-attribute-value", "属性[{attrName}]的值为空",
            ARG_ATTR_NAME);

    ErrorCode ERR_LOCK_ACQUIRE_FAIL = define("nop.err.commons.lock.acquire-fail", "获取锁[{resourceId}]失败",
            ARG_RESOURCE_ID);

    ErrorCode ERR_LOCK_INVALID_EXPIRE_TIME = define("nop.err.commons.lock.invalid-expire-time", "超时时间必须为正数",
            ARG_EXPIRE_TIME);

    ErrorCode ERR_LOCK_INVALID_WAIT_TIME = define("nop.err.commons.lock.invalid-wait-time", "超时时间必须为正数", ARG_WAIT_TIME);

    ErrorCode ERR_LOCK_INVALID_LEASE_TIME = define("nop.err.commons.lock.invalid-lease-time", "租期必须为正数",
            ARG_LEASE_TIME);

    ErrorCode ERR_LOCK_NOT_ALLOW_REENTRANT = define("nop.err.commons.lock.not-allow-reentrant", "锁不允许重入",
            ARG_RESOURCE_IDS);

    ErrorCode ERR_TYPE_INVALID_TYPE_INDEX = define("nop.err.commons.type.invalid-type-index", "非法的类型索引[{index}]",
            ARG_INDEX);

    ErrorCode ERR_TYPE_UNKNOWN_TYPE_NAME = define("nop.err.commons.type.unknown-type-name", "未知的数据类型:{name}", ARG_NAME);

    ErrorCode ERR_REFLECT_NEW_INSTANCE_FAIL = define("nop.err.commons.reflect.new-instance-fail",
            "创建对象实例失败：class={class}", ARG_CLASS);

    ErrorCode ERR_NET_INVALID_IP_STRING = define("nop.err.commons.net.invalid-ip-string", "非法的ip地址:{ip}", ARG_IP);

    ErrorCode ERR_LIST_NOT_ALLOW_NULL_ELEMENT = define("nop.err.commons.list-not-allow-null-element", "列表元素不允许为null");

    ErrorCode ERR_LIST_NOT_ALLOW__DUPLICATE_KEY = define("nop.err.commons.list-not-allow-duplicate-key", "列表元素的唯一键不允许重复");

    ErrorCode ERR_UTILS_INVALID_URL = define("nop.err.commons.utils.invalid-url", "URL格式不合法:{url}", ARG_URL);

    ErrorCode ERR_UTILS_INVALID_QUOTED_STRING = define("nop.err.commons.utils.invalid-quoted-string",
            "转义字符串格式不合法:{str}。字符串的起始和结尾字符应该是引用字符", ARG_STR);

    ErrorCode ERR_UTILS_DUPLICATE_PARAM_NAME_IS_NOT_ALLOWED_IN_SIMPLE_QUERY = define(
            "nop.err.commons.utils.duplicate-param-name-is-not-allowed-in-simple-query",
            "简单Query编码不允许重复的参数名:[{paramName}]", ARG_QUERY, ARG_PARAM_NAME);

    ErrorCode ERR_UTILS_URL_OPEN_STREAM_FAIL = define("nop.err.commons.utils.url-open-stream-fail", "URL打开流失败:{url}",
            ARG_URL);

    ErrorCode ERR_BYTES_CONVERT_TO_BYTE_STRING_FAIL = define("nop.err.commons.bytes.convert-to-byte-string-fail",
            "将类型[{srcType}]的值转换到ByteString类型失败");

    ErrorCode ERR_FILE_WRITE_CONFLICT = define("nop.err.commons.utils.file-write-conflict", "文件并发写入冲突:{path}",
            ARG_PATH);

    ErrorCode ERR_CACHE_DUPLICATE_REGISTRATION = define("nop.err.commons.cache.duplicate-registration",
            "注册cache失败，同名的cache已存在:{cacheName}", ARG_CACHE_NAME);

    ErrorCode ERR_FILE_ACQUIRE_LOCK_FAIL = define("nop.err.commons.file.acquire-lock-fail", "获取文件锁失败:{path}", ARG_PATH);

    ErrorCode ERR_FILE_ACQUIRE_LOCK_TIMEOUT = define("nop.err.commons.file.acquire-lock-timeout", "获取文件锁超时:{path}",
            ARG_PATH);

    ErrorCode ERR_LOAD_CLASS_NOT_EXPECTED_TYPE = define("nop.err.commons.load-class-not-expected-type",
            "加载的类[{className}]不是期望的类型:{expectedType}", ARG_CLASS_NAME, ARG_EXPECTED_TYPE);
}