package com.example.stagtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.floor

class WeakPRNGTest {
    @Test
    fun testSuccessiveRNGDrawsLookRandom() {
        val rng = WeakPRNG(0)
        assertEquals(12359, floor(100_000 * rng.random()).toInt())
        assertEquals(31134, floor(100_000 * rng.random()).toInt())
        assertEquals(88118, floor(100_000 * rng.random()).toInt())
        assertEquals(59012, floor(100_000 * rng.random()).toInt())
    }

    @Test
    fun testRNGDrawsWithSuccessiveSeedsLookRandom() {
        assertEquals(12359, floor(100_000 * WeakPRNG(0).random()).toInt())
        assertEquals(43107, floor(100_000 * WeakPRNG(1).random()).toInt())
        assertEquals(11105, floor(100_000 * WeakPRNG(2).random()).toInt())
        assertEquals(77808, floor(100_000 * WeakPRNG(3).random()).toInt())
        assertEquals(93534, floor(100_000 * WeakPRNG(4).random()).toInt())
        assertEquals(87700, floor(100_000 * WeakPRNG(5).random()).toInt())
    }

    @Test
    fun testSuccessivePoissonDrawsLookRandom() {
        val rng = WeakPRNG(0)
        assertEquals(102, rng.poisson(100.0))
        assertEquals(99, rng.poisson(100.0))
        assertEquals(105, rng.poisson(100.0))
        assertEquals(104, rng.poisson(100.0))
        assertEquals(110, rng.poisson(100.0))
    }

    @Test
    fun testPoissonDrawsWithSuccessiveSeedsLookRandom() {
        assertEquals(102, WeakPRNG(0).poisson(100.0))
        assertEquals(92, WeakPRNG(1).poisson(100.0))
        assertEquals(94, WeakPRNG(2).poisson(100.0))
        assertEquals(113, WeakPRNG(3).poisson(100.0))
        assertEquals(107, WeakPRNG(4).poisson(100.0))
        assertEquals(110, WeakPRNG(5).poisson(100.0))
    }

    @Test
    fun testPoissonAvgIsRight() {
        val rng = WeakPRNG(0)
        val draws = (0..1000).map { rng.poisson(5.0) }
        val avg = draws.average()
        assertEquals(5.0, avg, 0.1)
    }
}
