package com.ss.encrypt.mybatis;

import com.ss.encrypt.algorithm.AesGcmStringEncryptor;
import com.ss.encrypt.algorithm.Base64StringEncryptor;
import com.ss.encrypt.annotation.EncryptField;
import com.ss.encrypt.config.EncryptProperties;
import com.ss.encrypt.core.DefaultEncryptionService;
import com.ss.encrypt.core.EncryptionMaterial;
import com.ss.encrypt.key.PropertyEncryptionKeyProvider;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptedObjectProcessorTest {

    private final EncryptedObjectProcessor processor = processor();

    @Test
    void shouldEncryptContainersAndInheritedFieldsThenRestoreOriginalValues() {
        Customer first = new Customer("tenant-a", "13800138000", "memo-a");
        Customer second = new Customer("tenant-b", "13900139000", "memo-b");
        Map<String, Object> root = new HashMap<>();
        root.put("list", new ArrayList<>(List.of(first)));
        root.put("array", new Customer[] {second});
        root.put("self", root);

        EncryptedObjectProcessor.RestorationScope scope = processor.encrypt(root);

        assertThat(first.tenant).isNotEqualTo("tenant-a");
        assertThat(first.phone).isNotEqualTo("13800138000");
        assertThat(first.memo).isEqualTo("memo-a");
        assertThat(second.phone).isNotEqualTo("13900139000");

        scope.close();

        assertThat(first.tenant).isEqualTo("tenant-a");
        assertThat(first.phone).isEqualTo("13800138000");
        assertThat(second.phone).isEqualTo("13900139000");
    }

    @Test
    void shouldDecryptAnnotatedFieldsAndLeaveNullUntouched() {
        Customer customer = new Customer(null, "phone", "memo");
        EncryptedObjectProcessor.RestorationScope scope = processor.encrypt(customer);
        String encryptedPhone = customer.phone;
        scope.close();
        customer.phone = encryptedPhone;

        processor.decrypt(customer);

        assertThat(customer.tenant).isNull();
        assertThat(customer.phone).isEqualTo("phone");
        assertThat(customer.memo).isEqualTo("memo");
    }

    @Test
    void shouldRejectAnnotatedNonStringField() {
        assertThatThrownBy(() -> processor.encrypt(new InvalidEntity()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("count", "String");
    }

    @Test
    void shouldRejectStaticOrFinalAnnotatedFields() {
        assertThatThrownBy(() -> processor.encrypt(new FinalFieldEntity()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("immutable", "final");
        assertThatThrownBy(() -> processor.encrypt(new StaticFieldEntity()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("shared", "static");
    }

    private static EncryptedObjectProcessor processor() {
        EncryptProperties.Mybatis defaults = new EncryptProperties.Mybatis();
        return new EncryptedObjectProcessor(
                new DefaultEncryptionService(
                        List.of(new Base64StringEncryptor(),
                                new AesGcmStringEncryptor()),
                        new PropertyEncryptionKeyProvider(Map.of(
                                "default", EncryptionMaterial.symmetric(new byte[32])))),
                defaults);
    }

    static class TenantEntity {
        @EncryptField(algorithm = com.ss.encrypt.core.EncryptionAlgorithm.BASE64)
        String tenant;

        TenantEntity(String tenant) {
            this.tenant = tenant;
        }
    }

    static final class Customer extends TenantEntity {
        @EncryptField
        String phone;
        String memo;

        Customer(String tenant, String phone, String memo) {
            super(tenant);
            this.phone = phone;
            this.memo = memo;
        }
    }

    static final class InvalidEntity {
        @EncryptField
        int count = 1;
    }

    static final class FinalFieldEntity {
        @EncryptField
        final String immutable = "value";
    }

    static final class StaticFieldEntity {
        @EncryptField
        static String shared = "value";
    }
}
