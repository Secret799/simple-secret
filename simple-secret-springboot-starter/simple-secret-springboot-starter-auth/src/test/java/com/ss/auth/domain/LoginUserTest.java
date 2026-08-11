package com.ss.auth.domain;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginUserTest {

    @Test
    void shouldDefensivelyCopyAndExposeImmutableAuthorizationData() {
        Set<String> permissions = new HashSet<>(Set.of("orders:read"));
        Set<String> roles = new HashSet<>(Set.of("operator"));
        Map<String, java.io.Serializable> attributes = new HashMap<>();
        attributes.put("tenant", "t1");

        LoginUser user = new LoginUser(7L, "alice", permissions, roles, attributes);
        permissions.add("orders:delete");
        roles.clear();
        attributes.put("tenant", "changed");

        assertThat(user.permissions()).containsExactly("orders:read");
        assertThat(user.roles()).containsExactly("operator");
        assertThat(user.attributes()).containsEntry("tenant", "t1");
        assertThatThrownBy(() -> user.permissions().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> user.roles().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> user.attributes().put("key", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectMissingLoginId() {
        assertThatThrownBy(() -> new LoginUser(null, null, Set.of(), Set.of(), Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("loginId");
    }

    @Test
    void shouldAllowMissingUsername() {
        LoginUser user = new LoginUser(7L, null, Set.of(), Set.of(), Map.of());

        assertThat(user.username()).isNull();
    }

    @Test
    void shouldRemainEqualAfterJavaSerializationRoundTrip() throws Exception {
        LoginUser user = new LoginUser(7L, "alice", Set.of("orders:read"), Set.of("operator"),
                Map.of("tenant", "t1"));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(user);
        }

        LoginUser restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (LoginUser) input.readObject();
        }
        assertThat(restored).isEqualTo(user);
    }

    @Test
    void shouldRoundTripAllBaseLoginBodyFields() {
        BaseLoginBody body = new BaseLoginBody();
        body.setTenantId("tenant-a");
        body.setGrantType("password");
        body.setClientId("client-a");
        body.setCode("1234");
        body.setUuid("uuid-a");

        assertThat(body.getTenantId()).isEqualTo("tenant-a");
        assertThat(body.getGrantType()).isEqualTo("password");
        assertThat(body.getClientId()).isEqualTo("client-a");
        assertThat(body.getCode()).isEqualTo("1234");
        assertThat(body.getUuid()).isEqualTo("uuid-a");
    }

    @Test
    void shouldRoundTripAllBaseClientDomainFields() {
        BaseClientDomain client = new BaseClientDomain();
        client.setClientId("client-a");
        client.setClientKey("client-key");
        client.setClientSecret("client-secret");
        client.setGrantTypeList(List.of("password", "sms"));
        client.setDeviceType("web");
        client.setActiveTimeout(900L);
        client.setTimeout(3600L);
        client.setStatus(ClientStatus.NORMAL);

        assertThat(client.getClientId()).isEqualTo("client-a");
        assertThat(client.getClientKey()).isEqualTo("client-key");
        assertThat(client.getClientSecret()).isEqualTo("client-secret");
        assertThat(client.getGrantTypeList()).containsExactly("password", "sms");
        assertThat(client.getDeviceType()).isEqualTo("web");
        assertThat(client.getActiveTimeout()).isEqualTo(900L);
        assertThat(client.getTimeout()).isEqualTo(3600L);
        assertThat(client.getStatus()).isEqualTo(ClientStatus.NORMAL);
    }
}
