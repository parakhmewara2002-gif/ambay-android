package com.ambay.billing

import com.ambay.billing.data.remote.GitHubUpdateClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionTest {

    @Test
    fun `version comparison and SHA-256 extraction work`() {
        assertEquals(42, GitHubUpdateClient.parseVersionCodeFromTag("v1.0.42"))
        assertTrue(GitHubUpdateClient.isUpdateAvailable("v1.0.42", 41))
        assertFalse(GitHubUpdateClient.isUpdateAvailable("v1.0.42", 42))
        assertFalse(GitHubUpdateClient.isUpdateAvailable("v1.0.10", 42))

        val notes = "Automated release.\nSHA-256: `a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2`"
        assertEquals(
            "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2",
            GitHubUpdateClient.extractSha256(notes)
        )
    }
}
