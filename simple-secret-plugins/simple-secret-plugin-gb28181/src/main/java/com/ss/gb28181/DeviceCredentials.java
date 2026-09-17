package com.ss.gb28181;

import java.util.Optional;

/** Host-owned credential lookup. Return empty for unknown/disabled devices; never log passwords.
 * Implementations must be thread-safe and return promptly without network I/O.
 */
@FunctionalInterface
public interface DeviceCredentials {
    Optional<String> passwordFor(String deviceId);
}
