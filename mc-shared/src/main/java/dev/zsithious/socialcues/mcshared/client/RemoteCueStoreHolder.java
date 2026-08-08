package dev.zsithious.socialcues.mcshared.client;

import dev.zsithious.socialcues.core.client.RemoteCueStore;

/**
 * DESIGN.md §14 P4a: the one place Minecraft-side code — {@link
 * dev.zsithious.socialcues.mcshared.network.ClientHandshakeNetworking}'s
 * receiver (which feeds it {@code CueBatch}/{@code CueDrop}) and P4b's
 * render code (which reads it) — looks for the client's view of other
 * players' cues. Deliberately a separate tiny holder rather than a static
 * field on {@code RemoteCueStore} itself, matching {@link ServerPolicyState}'s
 * existing shape: the store class stays a plain, freely-instantiable object
 * that {@code core}'s own JUnit tests construct fresh per test, while
 * Minecraft-side code gets exactly one static access point.
 *
 * <p>Client-only in practice (only ever populated by client-only networking
 * code), but carries no client-only import itself, exactly like {@link
 * ServerPolicyState} — nothing prevents it from being referenced outside a
 * client environment, it simply never is.
 */
public final class RemoteCueStoreHolder {

    private static final RemoteCueStore STORE = new RemoteCueStore();

    private RemoteCueStoreHolder() {
    }

    public static RemoteCueStore get() {
        return STORE;
    }
}
