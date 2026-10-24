package com.enginesense.app.vehicle

enum class HealthCategory(val displayName: String) {
    ENGINE("Engine"),
    COOLING("Cooling"),
    FUEL_COMBUSTION("Fuel & Combustion"),
    BATTERY("Battery"),
    EMISSIONS("Emissions"),
    DIAGNOSTICS("Diagnostics")
}

enum class CategoryState {
    /** Nothing wrong found, and we have real data to back that up. */
    GOOD,
    /** Something worth keeping an eye on — not urgent. */
    WATCH,
    /** Something that should get checked soon. */
    ATTENTION,
    /** This vehicle doesn't expose the PIDs this category needs — never shown as "good". */
    UNAVAILABLE
}

data class CategoryHealth(
    val category: HealthCategory,
    val state: CategoryState,
    val note: String,
    /**
     * A 0-100 score, only for categories where a single number is meaningful (Engine,
     * Cooling, Fuel & Combustion) — null for the rest, which are shown as a Good/Watch/
     * Attention label only, and always null when [state] is [CategoryState.UNAVAILABLE]
     * (never a guessed number for data the vehicle didn't report).
     */
    val score: Int? = null
)

data class HealthScoreResult(
    /** Null when every category is UNAVAILABLE — i.e. there's no genuine evidence to score, so no number is shown rather than a misleading 100. */
    val score: Int?,
    val label: String,
    val categories: List<CategoryHealth>,
    /** The same breakdown behind the Fuel & Combustion category's score/state above, computed once so nothing can disagree with it. */
    val fuelCombustionInsight: FuelCombustionInsight
)
