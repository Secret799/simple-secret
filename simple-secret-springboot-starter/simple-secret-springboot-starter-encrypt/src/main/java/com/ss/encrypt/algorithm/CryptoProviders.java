package com.ss.encrypt.algorithm;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Provider;

final class CryptoProviders {

    static final Provider BOUNCY_CASTLE = new BouncyCastleProvider();

    private CryptoProviders() {
    }
}
