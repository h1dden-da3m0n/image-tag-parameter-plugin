package io.jenkins.plugins.luxair.model;

import java.util.Optional;

/**
 * A generic error container class to capsule a value and any optional error
 *
 * @param <V> The type of the to capsule value if there is no error
 */
public class ErrorContainer<V> {
    private String errorMsg = null;
    private V value;

    public ErrorContainer(V value) {
        this.value = value;
    }

    public Optional<String> getErrorMsg() {
        return Optional.ofNullable(errorMsg);
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public V getValue() {
        return value;
    }

    public void setValue(V value) {
        this.value = value;
    }
}
