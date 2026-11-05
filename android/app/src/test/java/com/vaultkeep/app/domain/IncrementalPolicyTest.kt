package com.vaultkeep.app.domain

import com.vaultkeep.app.domain.model.BackupStatus
import com.vaultkeep.app.domain.usecase.IncrementalPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncrementalPolicyTest {

    @Test
    fun `unknown file is never skipped`() {
        val result = IncrementalPolicy.shouldSkip(
            known = null,
            candidate = IncrementalPolicy.CandidateFile(sizeBytes = 100, modifiedAt = 1000),
        )
        assertFalse(result)
    }

    @Test
    fun `unchanged verified file is skipped`() {
        val known = IncrementalPolicy.KnownFile(
            sizeBytes = 100, modifiedAt = 1000, sha256 = "abc",
            recordStatusForDestination = BackupStatus.VERIFIED,
        )
        val candidate = IncrementalPolicy.CandidateFile(sizeBytes = 100, modifiedAt = 1000)
        assertTrue(IncrementalPolicy.shouldSkip(known, candidate))
    }

    @Test
    fun `changed size forces re-copy even if previously verified`() {
        val known = IncrementalPolicy.KnownFile(
            sizeBytes = 100, modifiedAt = 1000, sha256 = "abc",
            recordStatusForDestination = BackupStatus.VERIFIED,
        )
        val candidate = IncrementalPolicy.CandidateFile(sizeBytes = 200, modifiedAt = 1000)
        assertFalse(IncrementalPolicy.shouldSkip(known, candidate))
    }

    @Test
    fun `previously failed record is never skipped even if metadata matches`() {
        val known = IncrementalPolicy.KnownFile(
            sizeBytes = 100, modifiedAt = 1000, sha256 = "abc",
            recordStatusForDestination = BackupStatus.FAILED,
        )
        val candidate = IncrementalPolicy.CandidateFile(sizeBytes = 100, modifiedAt = 1000)
        assertFalse(IncrementalPolicy.shouldSkip(known, candidate))
    }

    @Test
    fun `pending record is never skipped`() {
        val known = IncrementalPolicy.KnownFile(
            sizeBytes = 100, modifiedAt = 1000, sha256 = "abc",
            recordStatusForDestination = BackupStatus.PENDING,
        )
        val candidate = IncrementalPolicy.CandidateFile(sizeBytes = 100, modifiedAt = 1000)
        assertFalse(IncrementalPolicy.shouldSkip(known, candidate))
    }

    @Test
    fun `isVerified requires both hash and size to match`() {
        assertTrue(IncrementalPolicy.isVerified("abc", 100, "abc", 100))
        assertFalse(IncrementalPolicy.isVerified("abc", 100, "abc", 99))
        assertFalse(IncrementalPolicy.isVerified("abc", 100, "xyz", 100))
    }
}
