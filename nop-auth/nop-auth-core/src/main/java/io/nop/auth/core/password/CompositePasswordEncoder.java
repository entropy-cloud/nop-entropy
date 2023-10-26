/**
 * Copyright (c) 2017-2023 Nop Platform. All rights reserved.
 * Author: canonical_entropy@163.com
 * Blog:   https://www.zhihu.com/people/canonical-entropy
 * Gitee:  https://gitee.com/canonical-entropy/nop-chaos
 * Github: https://github.com/entropy-cloud/nop-chaos
 */
package io.nop.auth.core.password;

public class CompositePasswordEncoder implements IPasswordEncoder {
    private IPasswordEncoder firstEncoder;
    private IPasswordEncoder secondEncoder;
    private boolean useSecondSalt;

    public boolean isUseSecondSalt() {
        return useSecondSalt;
    }

    public void setUseSecondSalt(boolean useSecondSalt) {
        this.useSecondSalt = useSecondSalt;
    }

    public IPasswordEncoder getFirstEncoder() {
        return firstEncoder;
    }

    public void setFirstEncoder(IPasswordEncoder firstEncoder) {
        this.firstEncoder = firstEncoder;
    }

    public IPasswordEncoder getSecondEncoder() {
        return secondEncoder;
    }

    public void setSecondEncoder(IPasswordEncoder secondEncoder) {
        this.secondEncoder = secondEncoder;
    }

    @Override
    public String generateSalt() {
        if (useSecondSalt)
            return secondEncoder.generateSalt();
        return firstEncoder.generateSalt();
    }

    @Override
    public String encodePassword(String type, String salt, String password) {
        String encoded = firstEncoder.encodePassword(type, salt, password);
        return secondEncoder.encodePassword(type, salt, encoded);
    }

    @Override
    public boolean passwordMatches(String type, String salt, String password, String encodedPassword) {
        String encoded = firstEncoder.encodePassword(type, salt, password);
        return secondEncoder.passwordMatches(type, salt, encoded, encodedPassword);
    }
}
