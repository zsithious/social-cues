package dev.zsithious.socialcues.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.zsithious.socialcues.core.state.Activity;

/**
 * DESIGN.md §9 P4a: "her Activity için kısa etiket" — mirrors
 * {@code CueIconAtlasTest}'s shape, checking the translation-key table
 * {@code assets/socialcues/lang/*.json} must stay in lockstep with.
 */
class CueLangKeysTest {

    @Test
    void everyActivityHasANonBlankKey() {
        for (Activity activity : Activity.values()) {
            String key = CueLangKeys.keyFor(activity);
            assertTrue(key != null && !key.isBlank(), "missing lang key for " + activity);
        }
    }

    @Test
    void everyActivityKeyIsDistinct() {
        Set<String> keys = new HashSet<>();
        for (Activity activity : Activity.values()) {
            assertTrue(keys.add(CueLangKeys.keyFor(activity)), "duplicate lang key for " + activity);
        }
        assertEquals(Activity.values().length, keys.size());
    }

    @Test
    void everyActivityKeySharesTheActivityNamespacePrefix() {
        for (Activity activity : Activity.values()) {
            assertTrue(CueLangKeys.keyFor(activity).startsWith("socialcues.activity."));
        }
    }

    @Test
    void sleepyFlagKeyIsDistinctFromEveryActivityKey() {
        for (Activity activity : Activity.values()) {
            assertFalse(CueLangKeys.SLEEPY_FLAG_KEY.equals(CueLangKeys.keyFor(activity)));
        }
        assertTrue(CueLangKeys.SLEEPY_FLAG_KEY.startsWith("socialcues."));
    }

    @Test
    void nullActivityRejected() {
        assertThrows(NullPointerException.class, () -> CueLangKeys.keyFor(null));
    }
}
