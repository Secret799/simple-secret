package com.ss.encrypt.web;

import com.ss.encrypt.algorithm.AesGcmStringEncryptor;
import com.ss.encrypt.algorithm.RsaOaepStringEncryptor;
import com.ss.encrypt.core.DefaultEncryptionService;
import com.ss.encrypt.core.EncryptionMaterial;
import com.ss.encrypt.core.EncryptionService;
import com.ss.encrypt.key.PropertyEncryptionKeyProvider;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.Map;

final class ApiTestCrypto {

    private ApiTestCrypto() {
    }

    static Fixture fixture() throws Exception {
        KeyPair request = rsa();
        KeyPair response = rsa();
        Map<String, EncryptionMaterial> materials = Map.of(
                "request", EncryptionMaterial.asymmetric(
                        publicKey(request), privateKey(request)),
                "response", EncryptionMaterial.asymmetric(
                        publicKey(response), privateKey(response)));
        EncryptionService service = new DefaultEncryptionService(
                List.of(new AesGcmStringEncryptor(),
                        new RsaOaepStringEncryptor()),
                new PropertyEncryptionKeyProvider(materials));
        return new Fixture(service, materials);
    }

    private static KeyPair rsa() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String publicKey(KeyPair pair) {
        return Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
    }

    private static String privateKey(KeyPair pair) {
        return Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
    }

    record Fixture(
            EncryptionService service,
            Map<String, EncryptionMaterial> materials) {
    }
}
