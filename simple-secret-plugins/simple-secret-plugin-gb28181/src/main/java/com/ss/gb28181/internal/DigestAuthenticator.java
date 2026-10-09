package com.ss.gb28181.internal;

import com.ss.gb28181.DeviceCredentials;
import javax.sip.header.AuthorizationHeader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;

/** Bounded, expiring Digest challenges bound to a device and its observed signaling endpoint. */
public final class DigestAuthenticator {
    private static final long NONCE_TTL_MILLIS = 60_000;
    private final String realm;
    private final int capacity;
    private final DeviceCredentials credentials;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Challenge> challenges = new HashMap<>();

    public DigestAuthenticator(String realm, int capacity, DeviceCredentials credentials) {
        this.realm = realm; this.capacity = capacity; this.credentials = Objects.requireNonNull(credentials);
    }
    public synchronized boolean requiresChallenge(AuthorizationHeader auth) {
        expire();
        return auth == null || !challenges.containsKey(auth.getNonce());
    }
    public synchronized String issue(String device, String endpoint) {
        expire();
        if (challenges.size() >= capacity) throw new IllegalStateException("Digest challenge capacity exceeded");
        byte[] nonce = new byte[24]; random.nextBytes(nonce);
        String value = HexFormat.of().formatHex(nonce);
        challenges.put(value, new Challenge(device, endpoint, System.currentTimeMillis() + NONCE_TTL_MILLIS));
        return value;
    }
    public synchronized boolean authenticate(String device, String endpoint, String method, String uri,
                                             AuthorizationHeader auth) {
        expire();
        if (auth == null || !"Digest".equalsIgnoreCase(auth.getScheme())
                || !device.equals(auth.getUsername()) || !realm.equals(auth.getRealm())
                || auth.getURI() == null || !uri.equals(auth.getURI().toString())
                || (auth.getAlgorithm() != null && !"MD5".equalsIgnoreCase(auth.getAlgorithm()))) return false;
        Challenge challenge = challenges.get(auth.getNonce());
        if (challenge == null || !device.equals(challenge.device) || !endpoint.equals(challenge.endpoint)) return false;
        String qop = auth.getQop();
        String nc = auth.getParameter("nc");
        long nonceCount = 0;
        if (qop != null) {
            if (!"auth".equals(qop) || auth.getCNonce() == null || auth.getCNonce().isEmpty()
                    || auth.getCNonce().length() > 128 || nc == null || !nc.matches("[0-9a-fA-F]{8}")) return false;
            nonceCount = Long.parseLong(nc, 16);
            if (nonceCount == 0 || nonceCount <= challenge.lastCount || challenge.legacyUsed) return false;
        } else if (challenge.legacyUsed || challenge.lastCount > 0) return false;
        Optional<String> secret = credentials.passwordFor(device);
        if (secret == null || secret.isEmpty() || secret.get().isEmpty()) return false;
        String ha1 = md5(device + ":" + realm + ":" + secret.get());
        String ha2 = md5(method + ":" + uri);
        String expected = md5(ha1 + ":" + auth.getNonce() + ":"
                + (qop == null ? "" : nc + ":" + auth.getCNonce() + ":auth:") + ha2);
        String actual = auth.getResponse();
        if (actual == null || !actual.matches("[0-9a-fA-F]{32}") || !MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII), actual.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII))) return false;
        challenge.lastCount = nonceCount;
        challenge.legacyUsed = qop == null;
        return true;
    }
    public synchronized void expire() {
        long now = System.currentTimeMillis();
        challenges.values().removeIf(challenge -> challenge.expires <= now);
    }
    public synchronized void clear() { challenges.clear(); }
    private static String md5(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("MD5 required by SIP Digest is unavailable", e); }
    }
    private static final class Challenge {
        final String device;
        final String endpoint;
        final long expires;
        long lastCount;
        boolean legacyUsed;
        Challenge(String device, String endpoint, long expires) {
            this.device = device; this.endpoint = endpoint; this.expires = expires;
        }
    }
}
