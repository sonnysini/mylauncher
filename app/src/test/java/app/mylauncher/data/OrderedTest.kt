package app.mylauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class OrderedTest {
    @Test
    fun savedOrderFirstThenNewAppsInArrivalOrder() {
        val apps = listOf("a", "b", "c", "d")
        assertEquals(listOf("c", "a", "b", "d"), apps.ordered(listOf("c", "x", "a")) { it })
    }

    @Test
    fun emptyOrderKeepsInput() {
        assertEquals(listOf("a", "b"), listOf("a", "b").ordered(emptyList()) { it })
    }
}
