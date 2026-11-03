package com.enginesense.app.vehicle

import com.enginesense.app.vehicle.ai.RepairCostEntry

/** One thing a buyer can see on the car's body, tyres or cabin, with a tentative repair cost per piece. */
data class ExteriorItem(val id: String, val label: String, val unit: String, val minInr: Int, val maxInr: Int)

/**
 * What an OBD scan can't see: the body, glass, tyres and cabin. The buyer counts what they spot
 * and the Used Car Check adds a tentative repair cost for it, the same "approximate, never a
 * quote" kind of range as [com.enginesense.app.vehicle.ai.RepairCostReference].
 */
object ExteriorDamage {

    val items = listOf(
        ExteriorItem("scratch", "Scratches", "panel", 1_500, 4_000),
        ExteriorItem("dent", "Dents", "panel", 2_000, 6_000),
        ExteriorItem("repaint", "Faded or mismatched paint", "panel", 3_000, 8_000),
        ExteriorItem("bumper", "Cracked or broken bumper", "bumper", 3_000, 12_000),
        ExteriorItem("glass", "Cracked windshield or window", "glass", 4_000, 15_000),
        ExteriorItem("light", "Broken headlight or taillight", "light", 2_000, 10_000),
        ExteriorItem("mirror", "Damaged side mirror", "mirror", 1_500, 6_000),
        ExteriorItem("rust", "Rust spots", "spot", 2_000, 8_000),
        ExteriorItem("tyre", "Worn-out tyres", "tyre", 3_000, 9_000),
        ExteriorItem("wheel", "Scuffed or bent wheels", "wheel", 1_000, 3_000),
        ExteriorItem("interior", "Torn seats or damaged trim", "area", 3_000, 15_000)
    )

    /** The buyer's counts as report lines, each priced per piece; items they didn't mark are left out. */
    fun lines(counts: Map<String, Int>): List<FaultCostLine> = items.mapNotNull { item ->
        val n = counts[item.id]?.takeIf { it > 0 } ?: return@mapNotNull null
        FaultCostLine(
            code = null,
            title = if (n == 1) item.label else "${item.label} (×$n)",
            kind = FaultKind.EXTERIOR,
            severity = null,
            cost = RepairCostEntry(item.label, item.minInr * n, item.maxInr * n, "Body and cabin", emptyList()),
            countedInTotal = true
        )
    }
}
