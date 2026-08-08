package dev.zsithious.socialcues.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import dev.zsithious.socialcues.core.policy.PolicyBits;

class SharePrefsSourceTest {

    @Test
    void allEnabledReturnsEveryBit() {
        assertEquals(PolicyBits.ALL, SharePrefsSource.allEnabled().prefBits());
    }

    @Test
    void isAFunctionalInterfaceUsableAsALambda() {
        SharePrefsSource fixed = () -> PolicyBits.TYPING;
        assertEquals(PolicyBits.TYPING, fixed.prefBits());
    }
}
