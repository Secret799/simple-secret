package com.ss.encrypt.config;

import com.ss.encrypt.core.CipherEncoding;
import com.ss.encrypt.core.EncryptionAlgorithm;
import com.ss.encrypt.core.EncryptionMaterial;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Encrypt starter 的总开关、密钥和条件能力配置。 */
@ConfigurationProperties("simple-secret.encrypt")
public class EncryptProperties {

    private boolean enabled;
    private Map<String, Key> keys = new LinkedHashMap<>();
    private Api api = new Api();
    private Mybatis mybatis = new Mybatis();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, Key> getKeys() {
        return keys;
    }

    public void setKeys(Map<String, Key> keys) {
        this.keys = keys == null ? new LinkedHashMap<>() : keys;
    }

    public Api getApi() {
        return api;
    }

    public void setApi(Api api) {
        this.api = api == null ? new Api() : api;
    }

    public Mybatis getMybatis() {
        return mybatis;
    }

    public void setMybatis(Mybatis mybatis) {
        this.mybatis = mybatis == null ? new Mybatis() : mybatis;
    }

    /** 将配置绑定结果转换为不可变密钥材料。 */
    public Map<String, EncryptionMaterial> materials() {
        Map<String, EncryptionMaterial> result = new LinkedHashMap<>();
        keys.forEach((keyId, key) -> result.put(keyId, key.material(keyId)));
        return Map.copyOf(result);
    }

    /** 单个 key id 的外部配置。 */
    public static class Key {

        private String secretKey;
        private String publicKey;
        private String privateKey;

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getPublicKey() {
            return publicKey;
        }

        public void setPublicKey(String publicKey) {
            this.publicKey = publicKey;
        }

        public String getPrivateKey() {
            return privateKey;
        }

        public void setPrivateKey(String privateKey) {
            this.privateKey = privateKey;
        }

        private EncryptionMaterial material(String keyId) {
            boolean symmetric = hasText(secretKey);
            boolean asymmetric = hasText(publicKey) || hasText(privateKey);
            if (symmetric && asymmetric) {
                throw new IllegalArgumentException("Encryption key id '" + keyId
                        + "' cannot mix symmetric and asymmetric material");
            }
            if (symmetric) {
                try {
                    return EncryptionMaterial.symmetric(
                            Base64.getDecoder().decode(secretKey.trim()));
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException("Symmetric material for key id '"
                            + keyId + "' is not valid Base64", exception);
                }
            }
            if (asymmetric) {
                return EncryptionMaterial.asymmetric(publicKey, privateKey);
            }
            throw new IllegalArgumentException(
                    "Encryption key id '" + keyId + "' has no material");
        }
    }

    /** Servlet API 密文传输配置。 */
    public static class Api {

        private boolean enabled;
        private String headerName = "X-Encrypt-Key";
        private String requestKeyId;
        private String responseKeyId;
        private DataSize maxRequestSize = DataSize.ofMegabytes(1);
        private DataSize maxResponseSize = DataSize.ofMegabytes(1);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHeaderName() {
            return headerName;
        }

        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }

        public String getRequestKeyId() {
            return requestKeyId;
        }

        public void setRequestKeyId(String requestKeyId) {
            this.requestKeyId = requestKeyId;
        }

        public String getResponseKeyId() {
            return responseKeyId;
        }

        public void setResponseKeyId(String responseKeyId) {
            this.responseKeyId = responseKeyId;
        }

        public DataSize getMaxRequestSize() {
            return maxRequestSize;
        }

        public void setMaxRequestSize(DataSize maxRequestSize) {
            this.maxRequestSize = maxRequestSize;
        }

        public DataSize getMaxResponseSize() {
            return maxResponseSize;
        }

        public void setMaxResponseSize(DataSize maxResponseSize) {
            this.maxResponseSize = maxResponseSize;
        }
    }

    /** MyBatis 字段加密默认配置。 */
    public static class Mybatis {

        private boolean enabled;
        private EncryptionAlgorithm algorithm = EncryptionAlgorithm.AES_GCM;
        private CipherEncoding encoding = CipherEncoding.BASE64;
        private String keyId = "default";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public EncryptionAlgorithm getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(EncryptionAlgorithm algorithm) {
            this.algorithm = algorithm;
        }

        public CipherEncoding getEncoding() {
            return encoding;
        }

        public void setEncoding(CipherEncoding encoding) {
            this.encoding = encoding;
        }

        public String getKeyId() {
            return keyId;
        }

        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
