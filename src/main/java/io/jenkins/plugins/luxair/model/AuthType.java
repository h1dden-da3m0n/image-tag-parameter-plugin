package io.jenkins.plugins.luxair.model;

public enum AuthType {
    BASIC("Basic"),
    BEARER("Bearer"),
    UNKNOWN("Unknown");

    public final String value;

    AuthType(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return this.value;
    }
}
