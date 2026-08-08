package dev.zsithious.socialcues.mcshared.client;

/**
 * DESIGN.md §7 P4b — the one seam a render-capable bucket uses to register
 * its Layer 1 feature renderer(s) (e.g. via Fabric API's
 * {@code LivingEntityFeatureRendererRegistrationCallback}) without
 * {@code mc-shared} ever importing a single {@code adapter.bucket*} class.
 *
 * <p><b>Why this exists at all:</b> {@code mc-shared} is compiled once per
 * {@code :mc:<version>} project, paired with exactly one bucket's source
 * directory at a time (see {@code mc/mc.gradle.kts}). If
 * {@code SocialCuesClientInitializer} imported
 * {@code adapter.bucketd.render.BucketDFeatureRendererBootstrap} directly,
 * every other bucket's build (today just an empty placeholder package, but a
 * real one from P7 onward) would fail to compile the moment it tried to
 * resolve that import against its own, bucket-D-less classpath. Mixin config
 * registration sidesteps this exact problem already (DESIGN.md §3.1: same
 * fixed filename, resolved per-bucket via the resources classpath); this
 * interface does the equivalent for a plain Java API call that isn't a
 * mixin and therefore can't self-activate on class load.
 *
 * <p><b>How a bucket plugs in:</b> implement this interface with a
 * no-Minecraft-import-required registration call, then declare it as a
 * {@link java.util.ServiceLoader} provider — a
 * {@code META-INF/services/dev.zsithious.socialcues.mcshared.client.FeatureRendererBootstrap}
 * file under the bucket's own {@code adapters/&lt;bucket&gt;/src/main/resources}
 * (already on the resources classpath per {@code mc.gradle.kts}), containing
 * the implementation's fully-qualified name. {@link SocialCuesClientInitializer}
 * discovers and calls every provider found this way; a bucket that supplies
 * none (every bucket except D, today) simply contributes nothing, with no
 * missing-class error at any point.
 */
public interface FeatureRendererBootstrap {

    /** Called once, during {@code onInitializeClient}, after the client config is loaded. */
    void register();
}
