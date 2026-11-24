package com.lin.music.core.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Test

class MultiTapCoordinatorTest {

    private class FakeScheduler {
        private var nextId = 0
        private val pending = linkedMapOf<Int, () -> Unit>()

        val schedule: (Long, () -> Unit) -> () -> Unit = { _, block ->
            val id = nextId++
            pending[id] = block
            val cancelFn: () -> Unit = { pending.remove(id) }
            cancelFn
        }

        fun fireLatest() {
            val keys = pending.keys.toList()
            for (k in keys) {
                val block = pending.remove(k)
                block?.invoke()
            }
        }

        val pendingCount: Int get() = pending.size
    }

    @Test
    fun `single click only fires immediately without scheduling`() {
        val scheduler = FakeScheduler()
        val coordinator = MultiTapCoordinator(schedule = scheduler.schedule)
        var clicks = 0

        coordinator.onTap(
            rowKey = "row1",
            onSingleClick = { clicks++ }
        )

        assertEquals(1, clicks)
        assertEquals(0, scheduler.pendingCount)
    }

    @Test
    fun `three tap mode disambiguates single, double, and triple clicks`() {
        val scheduler = FakeScheduler()
        val coordinator = MultiTapCoordinator(schedule = scheduler.schedule)
        var single = 0
        var dbl = 0
        var triple = 0

        // 1 tap -> waits -> fires single
        coordinator.onTap("row1", { single++ }, { dbl++ }, { triple++ })
        assertEquals(0, single)
        assertEquals(1, scheduler.pendingCount)
        scheduler.fireLatest()
        assertEquals(1, single)
        assertEquals(0, dbl)
        assertEquals(0, triple)

        // 2 taps -> cancels 1st timer, waits -> fires double
        coordinator.onTap("row1", { single++ }, { dbl++ }, { triple++ })
        coordinator.onTap("row1", { single++ }, { dbl++ }, { triple++ })
        assertEquals(1, single)
        assertEquals(0, dbl)
        assertEquals(1, scheduler.pendingCount)
        scheduler.fireLatest()
        assertEquals(1, single)
        assertEquals(1, dbl)
        assertEquals(0, triple)

        // 3 taps -> fires triple immediately without firing single or double
        coordinator.onTap("row1", { single++ }, { dbl++ }, { triple++ })
        coordinator.onTap("row1", { single++ }, { dbl++ }, { triple++ })
        coordinator.onTap("row1", { single++ }, { dbl++ }, { triple++ })
        assertEquals(1, single)
        assertEquals(1, dbl)
        assertEquals(1, triple)
        assertEquals(0, scheduler.pendingCount)
    }

    @Test
    fun `two tap only mode fires double click immediately on second tap`() {
        val scheduler = FakeScheduler()
        val coordinator = MultiTapCoordinator(schedule = scheduler.schedule)
        var single = 0
        var dbl = 0

        coordinator.onTap("row1", { single++ }, { dbl++ }, onTripleClick = null)
        coordinator.onTap("row1", { single++ }, { dbl++ }, onTripleClick = null)

        assertEquals(0, single)
        assertEquals(1, dbl)
        assertEquals(0, scheduler.pendingCount)
    }

    @Test
    fun `tapping another row cancels pending timer on previous row`() {
        val scheduler = FakeScheduler()
        val coordinator = MultiTapCoordinator(schedule = scheduler.schedule)
        var row1Single = 0
        var row2Single = 0

        coordinator.onTap("row1", { row1Single++ }, { }, { })
        coordinator.onTap("row2", { row2Single++ }, { }, { })
        scheduler.fireLatest()

        assertEquals(0, row1Single)
        assertEquals(1, row2Single)
    }

    @Test
    fun `cancel with row key only cancels matching active row`() {
        val scheduler = FakeScheduler()
        val coordinator = MultiTapCoordinator(schedule = scheduler.schedule)
        var row1Single = 0

        coordinator.onTap("row1", { row1Single++ }, { }, { })
        coordinator.cancel("row2")
        assertEquals(1, scheduler.pendingCount)

        coordinator.cancel("row1")
        scheduler.fireLatest()
        assertEquals(0, row1Single)
        assertEquals(0, scheduler.pendingCount)

        coordinator.onTap("row1", { row1Single++ }, { }, { })
        coordinator.cancel()
        scheduler.fireLatest()
        assertEquals(0, row1Single)
    }
}
