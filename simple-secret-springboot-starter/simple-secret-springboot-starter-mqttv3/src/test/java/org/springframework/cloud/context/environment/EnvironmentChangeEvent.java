package org.springframework.cloud.context.environment;

import java.util.Collection;

public final class EnvironmentChangeEvent {
    private final Collection<String> keys;

    public EnvironmentChangeEvent(Collection<String> keys) {
        this.keys = keys;
    }

    public Collection<String> getKeys() {
        return keys;
    }
}
