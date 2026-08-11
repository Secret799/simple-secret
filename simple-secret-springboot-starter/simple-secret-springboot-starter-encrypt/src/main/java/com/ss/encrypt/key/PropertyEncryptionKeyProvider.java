package com.ss.encrypt.key;

import com.ss.encrypt.core.EncryptionAlgorithm;
import com.ss.encrypt.core.EncryptionException;
import com.ss.encrypt.core.EncryptionMaterial;

import java.util.LinkedHashMap;
import java.util.Map;

/** 使用配置绑定结果的不可变快照提供密钥材料。 */
public final class PropertyEncryptionKeyProvider implements EncryptionKeyProvider {

    private final Map<String, EncryptionMaterial> materials;

    public PropertyEncryptionKeyProvider(Map<String, EncryptionMaterial> materials) {
        if (materials == null) {
            throw new IllegalArgumentException("materials must not be null");
        }
        Map<String, EncryptionMaterial> snapshot = new LinkedHashMap<>();
        materials.forEach((keyId, material) -> {
            String normalized = normalizeKeyId(keyId);
            if (material == null) {
                throw new IllegalArgumentException(
                        "Encryption material must not be null for key id " + normalized);
            }
            if (snapshot.putIfAbsent(normalized, material) != null) {
                throw new IllegalArgumentException(
                        "Duplicate encryption key id after trimming: " + normalized);
            }
        });
        this.materials = Map.copyOf(snapshot);
    }

    @Override
    public EncryptionMaterial resolve(
            String keyId, EncryptionAlgorithm algorithm) {
        String normalized = normalizeKeyId(keyId);
        EncryptionMaterial material = materials.get(normalized);
        if (material == null) {
            throw new EncryptionException("Encryption key id '" + normalized
                    + "' is unavailable for " + algorithm);
        }
        return material;
    }

    private static String normalizeKeyId(String keyId) {
        if (keyId == null || keyId.trim().isEmpty()) {
            throw new IllegalArgumentException("keyId must not be blank");
        }
        return keyId.trim();
    }
}
