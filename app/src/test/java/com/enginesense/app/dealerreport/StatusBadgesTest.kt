package com.enginesense.app.dealerreport

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusBadgesTest {

    private fun option(id: String, available: Boolean, group: BadgeGroup = BadgeGroup.SCAN) =
        StatusBadgeOption(id, group, SaleBadge(id, "", true), available)

    private val options = listOf(
        option(StatusBadges.HEALTH, true),
        option("no_faults", false),
        option("no_red_flags", true),
        option("papers", true, BadgeGroup.RTO),
        option("tick:Accident-free", true, BadgeGroup.DEALER),
        option("tick:Spare key", false, BadgeGroup.DEALER)
    )

    @Test
    fun `defaults are health then dealer ticks, RTO and scan items that are true for the car`() {
        assertEquals(listOf("health", "tick:Accident-free", "papers", "no_red_flags"), StatusBadges.defaults(options))
    }

    @Test
    fun `defaults take at most two dealer ticks`() {
        val many = options + option("tick:New tyres", true, BadgeGroup.DEALER) + option("tick:Recently serviced", true, BadgeGroup.DEALER)
        assertEquals(listOf("health", "tick:Accident-free", "tick:New tyres", "papers"), StatusBadges.defaults(many))
    }

    @Test
    fun `defaults fill with scan items when the dealer gave no ticks`() {
        val scanOnly = options.filter { it.group == BadgeGroup.SCAN }
        assertEquals(listOf("health", "no_red_flags"), StatusBadges.defaults(scanOnly))
    }

    @Test
    fun `health always comes first and unavailable picks are dropped`() {
        val drawn = StatusBadges.chosen(options, listOf("tick:Accident-free", "no_faults", "tick:Spare key", "papers"))
        assertEquals(listOf("health", "tick:Accident-free", "papers"), drawn.map { it.title })
    }

    @Test
    fun `never more than four`() {
        val drawn = StatusBadges.chosen(options, listOf("no_red_flags", "papers", "tick:Accident-free", "health"))
        assertEquals(4, drawn.size)
        assertEquals("health", drawn.first().title)
    }
}
