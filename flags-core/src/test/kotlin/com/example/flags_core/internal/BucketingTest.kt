package com.example.flags_core.internal

import com.example.flags_core.internal.Bucketing

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test

class BucketingTest {

    @Test
    fun bucketIsWithinRange() {
        val bucket = Bucketing.bucket("random_feature", "user_123")
        assertTrue(bucket in 0..99)
    }

    @Test
    fun bucketIsStable() {
        assertEquals(87, Bucketing.bucket("recruiter_look_at_me", "candidate_123"))
    }

}
