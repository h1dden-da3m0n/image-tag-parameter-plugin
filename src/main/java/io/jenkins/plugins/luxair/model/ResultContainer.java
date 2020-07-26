package io.jenkins.plugins.luxair.model;

import java.util.Optional;

/**
 * A generic error container class to capsule a value and any optional error
 *
 * @param <V> The type of the to capsule value if there is no error
 */
public class ResultContainer<V> {
    private String errorMsg = null;
    private V value;

    public ResultContainer(V defaultValue) {
        this.value = defaultValue;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public Optional<String> getErrorMsg() {
        return Optional.ofNullable(errorMsg);
    }

    public void setValue(V value) {
        this.value = value;
    }

    public V getValue() {
        return value;
    }
}
