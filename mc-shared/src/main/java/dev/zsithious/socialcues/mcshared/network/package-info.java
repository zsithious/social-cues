/**
 * DESIGN.md §5/§6 — the Fabric adapter for the {@code socialcues:v1}
 * CustomPayload channel: type registration, and the client/server halves of
 * the P1 handshake. Contains both common-safe classes ({@link
 * dev.zsithious.socialcues.mcshared.network.SocialCuesPayload}, {@link
 * dev.zsithious.socialcues.mcshared.network.SocialCuesChannels}, {@link
 * dev.zsithious.socialcues.mcshared.network.ServerHandshake}) and a
 * client-only class ({@link
 * dev.zsithious.socialcues.mcshared.network.ClientHandshakeNetworking}) that
 * is only ever invoked from the {@code client} entrypoint, never from the
 * common one — see that class's Javadoc for why.
 */
package dev.zsithious.socialcues.mcshared.network;
