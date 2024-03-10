/**
 * Copyright (c) 2017-2024 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-entropy
 * Github: https://github.com/entropy-cloud/nop-entropy
 */
package io.nop.auth.core.password;

import io.nop.api.core.exceptions.NopException;
import io.nop.commons.util.StringHelper;

import static io.nop.auth.core.AuthCoreErrors.ARG_LENGTH;
import static io.nop.auth.core.AuthCoreErrors.ARG_MIN_COUNT;
import static io.nop.auth.core.AuthCoreErrors.ARG_MIN_LENGTH;
import static io.nop.auth.core.AuthCoreErrors.ERR_PASSWORD_LENGTH_TOO_SHORT;
import static io.nop.auth.core.AuthCoreErrors.ERR_PASSWORD_MUST_NOT_BE_SAME_AS_USER_NAME;
import static io.nop.auth.core.AuthCoreErrors.ERR_PASSWORD_TOO_FEW_DIGITS;
import static io.nop.auth.core.AuthCoreErrors.ERR_PASSWORD_TOO_FEW_LOWER_CASE;
import static io.nop.auth.core.AuthCoreErrors.ERR_PASSWORD_TOO_FEW_SPECIAL_CHAR;
import static io.nop.auth.core.AuthCoreErrors.ERR_PASSWORD_TOO_FEW_UPPER_CASE;

public class DefaultPasswordPolicy implements IPasswordPolicy {

    private int minLength;
    /**
     * 最少包含多少个大写字母
     */
    private int upperCaseCount;
    private int lowerCaseCount;
    private int specialCharCount;
    private int digitCount;
    private boolean notUserName;

    public int getMinLength() {
        return minLength;
    }

    public void setMinLength(int minLength) {
        this.minLength = minLength;
    }

    public int getUpperCaseCount() {
        return upperCaseCount;
    }

    public void setUpperCaseCount(int upperCaseCount) {
        this.upperCaseCount = upperCaseCount;
    }

    public int getLowerCaseCount() {
        return lowerCaseCount;
    }

    public void setLowerCaseCount(int lowerCaseCount) {
        this.lowerCaseCount = lowerCaseCount;
    }

    public int getSpecialCharCount() {
        return specialCharCount;
    }

    public void setSpecialCharCount(int specialCharCount) {
        this.specialCharCount = specialCharCount;
    }

    public int getDigitCount() {
        return digitCount;
    }

    public void setDigitCount(int digitCount) {
        this.digitCount = digitCount;
    }

    public boolean isNotUserName() {
        return notUserName;
    }

    public void setNotUserName(boolean notUserName) {
        this.notUserName = notUserName;
    }

    @Override
    public void checkAllowedPassword(String userName, String password) {
        if (password == null)
            password = "";

        if (password.length() < minLength)
            throw new NopException(ERR_PASSWORD_LENGTH_TOO_SHORT)
                    .param(ARG_MIN_LENGTH, minLength)
                    .param(ARG_LENGTH, password.length());

        if (upperCaseCount > 0) {
            if (StringHelper.countContains(password, StringHelper.UPPER_CASE) < upperCaseCount)
                throw new NopException(ERR_PASSWORD_TOO_FEW_UPPER_CASE).param(ARG_MIN_COUNT, upperCaseCount);
        }

        if (lowerCaseCount > 0) {
            if (StringHelper.countContains(password, StringHelper.LOWER_CASE) < lowerCaseCount)
                throw new NopException(ERR_PASSWORD_TOO_FEW_LOWER_CASE).param(ARG_MIN_COUNT, lowerCaseCount);
        }

        if (specialCharCount > 0) {
            if (StringHelper.countContains(password, StringHelper.SPECIAL_CHARS) < specialCharCount)
                throw new NopException(ERR_PASSWORD_TOO_FEW_SPECIAL_CHAR).param(ARG_MIN_COUNT, specialCharCount);
        }

        if (digitCount > 0) {
            if (StringHelper.countContains(password, StringHelper.DIGITS) < digitCount)
                throw new NopException(ERR_PASSWORD_TOO_FEW_DIGITS).param(ARG_MIN_COUNT, digitCount);
        }

        if (notUserName) {
            if (password.equals(userName))
                throw new NopException(ERR_PASSWORD_MUST_NOT_BE_SAME_AS_USER_NAME);
        }
    }
}