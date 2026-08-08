package dev.zsithious.socialcues.core.protocol;

/**
 * Thrown for any malformed, truncated, oversized, or otherwise untrusted
 * wire input. Callers (Fabric network handler, Bukkit PluginMessageListener)
 * are expected to catch this, drop the packet, and optionally count it
 * towards a rate-limit/kick threshold — never to propagate it as a crash.
 */
public final class ProtocolDecodeException extends RuntimeException {

    public ProtocolDecodeException(String message) {
        super(message);
    }
}
