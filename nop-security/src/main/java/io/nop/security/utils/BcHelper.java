package io.nop.security.utils;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;

public class BcHelper {
    static {
        register();
    }

    private static void register() {
        //注册Bouncy Castle作为安全提供者
        Security.addProvider(new BouncyCastleProvider());
    }
}
