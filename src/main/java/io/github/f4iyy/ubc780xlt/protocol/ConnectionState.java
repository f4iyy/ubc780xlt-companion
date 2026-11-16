package io.github.f4iyy.ubc780xlt.protocol;

/** Lifecycle of the link to the scanner. */
public enum ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    /** The link broke; a reconnection will be attempted. */
    LOST,
    RECONNECTING
}
