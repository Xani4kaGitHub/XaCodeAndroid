package com.xanichka.xacode.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TermuxBridgeTest {
    @Test
    fun packageManagementCommandsAreAllowed() {
        assertFalse(TermuxBridge.isCommandBlocked("pkg update -y && pkg reinstall -y openssl nodejs"))
        assertFalse(TermuxBridge.isCommandBlocked("apt update && apt install git"))
    }

    @Test
    fun dangerousSystemCommandsRemainBlocked() {
        assertTrue(TermuxBridge.isCommandBlocked("rm -rf /"))
        assertTrue(TermuxBridge.isCommandBlocked("mkfs.ext4 /dev/block/example"))
    }

    @Test
    fun developmentPackageAllowlistIncludesCommonRuntimes() {
        assertTrue("nodejs" in TermuxBridge.SAFE_PACKAGES)
        assertTrue("python" in TermuxBridge.SAFE_PACKAGES)
        assertTrue("git" in TermuxBridge.SAFE_PACKAGES)
        assertTrue("clang" in TermuxBridge.SAFE_PACKAGES)
    }
}
