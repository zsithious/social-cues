/**
 * DESIGN.md §6 "Konuşma" / §14 P8 — the optional Simple Voice Chat
 * integration, in its own source directory for the same reason
 * {@code integrations/configui/} is: it is the only code here that compiles
 * against a third-party mod's API.
 *
 * <p>Unlike {@code configui}, this directory is compiled on <b>all twelve</b>
 * rows unconditionally, and needs no {@code versions.json} columns. That is a
 * measured property, not an assumption: the published {@code voicechat-api}
 * jar contains no {@code net/minecraft} reference anywhere — it is pure
 * interfaces over Simple Voice Chat's own abstractions ({@code Position},
 * {@code Entity}, {@code Player}) — so one artifact is correct for every
 * Minecraft version. Cloth and ModMenu had to be pinned per row precisely
 * because they are not.
 *
 * <p>The version pinned is the <b>lowest</b> that carries what is needed
 * ({@code 2.6.0}), not the newest. {@code VoicechatClientApi#isTalking()} was
 * introduced there — verified by reading the published API jars from 2.4.0
 * through 2.6.20, not inferred from changelogs — and compiling against the
 * floor is what makes the widest range of installed Simple Voice Chat builds
 * work at runtime. Every 1.21.x row has 2.6.x builds available, so no row is
 * left without the feature.
 *
 * <p>The dependency is a soft one in both directions: {@code compileOnly}, so
 * it is absent at runtime and absent from every shipped jar, and reached only
 * through the {@code voicechat} entrypoint, which nothing but Simple Voice
 * Chat itself ever reads.
 */
package dev.zsithious.socialcues.voicechat;
