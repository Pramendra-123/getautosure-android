package com.enginesense.app.vehicle.ai

/**
 * General automotive knowledge, answerable with NO scan at all — the offline-safe piece of
 * "answer general automotive questions even when no OBD scan has been performed" that doesn't
 * need a real LLM backend. Sourced from the same PDF content already used for the Warning
 * Light Guide and the video-script series this session (rights confirmed), not invented.
 * Deliberately a curated subset, not all 100 entries — the highest-value, most-asked symptoms.
 *
 * This is still rule-based keyword matching, not general NLU — see RuleBasedAiMechanicChatService
 * for how it's used, and the class doc there for why it's honestly labeled as such.
 */
data class GeneralTopic(
    val keywords: List<String>,
    val topic: String,
    val symptom: String,
    val cause: String,
    val advice: String,
    /** True for things an OBD scan genuinely cannot verify (compression, pad thickness, tyre tread...) — triggers an explicit scope disclaimer. */
    val beyondObdScope: Boolean = false,
    /** CRITICAL/MODERATE/MINOR — how urgently this symptom needs attention. Safety-critical topics (overheating, brake failure) are CRITICAL and skip the clarifying question below to give guidance immediately. */
    val urgency: String = "MODERATE",
    /**
     * Asked once, before the full answer, when the symptom is genuinely ambiguous and a
     * detail changes the advice (e.g. "overheats at idle" vs "overheats on the highway").
     * Null for topics where delaying the answer to ask a question wouldn't help, or — for
     * safety-critical topics — where giving guidance immediately matters more than precision.
     */
    val clarifyingQuestion: String? = null
)

object GeneralAutomotiveKnowledge {

    private val topics = listOf(
        GeneralTopic(
            listOf("won't start", "wont start", "crank", "doesn't start", "doesn't turn over"),
            "Engine won't start",
            "Key turns but nothing happens, or the engine cranks but doesn't catch.",
            "Usually one of three things: a weak/dead battery, a fuel delivery problem, or no spark. Dashboard lights dim or off points to the battery; cranking normally but not starting points to fuel or spark.",
            "Check battery terminals for corrosion first — it's the most common and cheapest cause. If the battery's fine, this needs a mechanic to isolate fuel vs. spark."
        ),
        GeneralTopic(
            listOf("knock", "knocking", "tak-tak", "pinging"),
            "Engine knocking sound",
            "A metallic tapping or knocking sound, usually worse under acceleration.",
            "Low-octane fuel or carbon buildup causes light knocking. A deep knock audible even at idle can mean worn bearings — a more serious mechanical issue.",
            "Try better-quality fuel first for light knocking. If it's a deep knock at idle, stop driving and get it inspected — this can cause real engine damage if ignored."
        ),
        GeneralTopic(
            listOf("overheat", "overheating", "temperature", "steam", "red zone"),
            "Engine overheating",
            "Temperature gauge in the red zone, possibly steam from the bonnet.",
            "Low coolant, a failed thermostat, a blocked radiator, or a cooling fan that isn't running.",
            "Pull over and switch off the engine. Let it cool fully before checking coolant — never open a hot radiator cap. This is not safe to keep driving through.",
            urgency = "CRITICAL",
            clarifyingQuestion = "Does it overheat while idling, while driving, or both?"
        ),
        GeneralTopic(
            listOf("smoke", "white smoke", "blue smoke", "black smoke", "exhaust smoke"),
            "Smoke from the exhaust",
            "Visible smoke from the tailpipe — the colour matters.",
            "White smoke usually means coolant is leaking into the engine (head gasket). Blue smoke means the engine is burning oil. Black smoke means the fuel mixture is too rich.",
            "White or blue smoke both need a mechanic's diagnosis before you keep driving much further — both point to real internal engine issues, not something to guess at.",
            urgency = "CRITICAL"
        ),
        GeneralTopic(
            listOf("mileage", "fuel economy", "fuel consumption", "kmpl"),
            "Poor fuel mileage",
            "Noticeably fewer kilometres per litre than before.",
            "Most often a dirty air filter, worn spark plugs, under-inflated tyres, or aggressive driving habits.",
            "Check tyre pressure and the air filter first — both are quick, cheap checks. If mileage dropped suddenly rather than gradually, a scan is worth running to rule out a sensor issue.",
            urgency = "MINOR"
        ),
        GeneralTopic(
            listOf("squeal", "grinding", "squeaking"),
            "Brake noise",
            "Squealing or grinding when braking.",
            "Squealing is usually a worn-pad wear indicator doing its job. Grinding means the pads are fully worn and metal is contacting the disc.",
            "Squealing: get pads checked soon. Grinding: this is safety-critical — stop driving and get it looked at immediately, continuing can damage the disc and compromise braking.",
            urgency = "CRITICAL"
        ),
        GeneralTopic(
            listOf("clutch", "slipping", "clutch slip"),
            "Clutch slipping",
            "Engine RPM rises but the car doesn't accelerate to match.",
            "The clutch plate is worn out — a normal wear-and-tear part, accelerated by a lot of half-clutch driving.",
            "This needs a clutch replacement — not dangerous to keep driving short-term, but it will get worse and eventually leave you unable to move.",
            urgency = "MINOR"
        ),
        GeneralTopic(
            listOf("ac", "air conditioning", "not cooling", "ac not cold"),
            "AC not cooling",
            "AC is on but the cabin isn't getting cold.",
            "Most often a refrigerant leak. Also possible: a failed compressor clutch or a blocked condenser.",
            "Refrigerant handling needs a certified AC technician — it's a regulated substance, not a DIY top-up job.",
            urgency = "MINOR"
        ),
        GeneralTopic(
            listOf("battery", "wont start", "dead battery", "jump start"),
            "Battery keeps dying",
            "Car won't start after sitting for a day or two.",
            "A parasitic drain (something left on), a failing alternator not recharging it while driving, or the battery simply reaching end of life (~3 years typical).",
            "Get the alternator's charging output tested before replacing the battery — a bad alternator will just kill the new one too."
        ),
        GeneralTopic(
            listOf("vibration", "shaking", "steering vibrate"),
            "Steering or car vibration",
            "Vibration felt through the steering wheel, usually speed-dependent.",
            "Most often wheel balancing or alignment. Can also be a tyre defect or a warped brake disc if it's worse under braking.",
            "Wheel balancing/alignment is a quick, affordable first check before assuming anything more serious."
        ),
        GeneralTopic(
            listOf("suspension", "bump", "thak-thak", "clunk"),
            "Suspension noise over bumps",
            "A knocking or clunking sound over speed bumps or potholes.",
            "Worn shock absorbers, bushings, or ball joints.",
            "Not usually urgent, but worth inspecting soon — worn suspension components affect handling and tyre wear over time.",
            urgency = "MINOR"
        ),
        GeneralTopic(
            listOf("transmission", "gear", "jerk", "shifting hard"),
            "Transmission or gear shifting issue",
            "Hard/rough gear shifts, or jerking during automatic shifts.",
            "Manual: often clutch or transmission fluid. Automatic: often low or old ATF, or a solenoid issue.",
            "Check transmission fluid level and condition first — a burnt smell or dark colour means it needs changing. Persistent jerking should be scanned."
        ),
        GeneralTopic(
            listOf("tyre", "tire", "puncture", "tread", "tyre pressure"),
            "Tyre condition and pressure",
            "Low pressure, uneven wear, or a bulge on the sidewall.",
            "Pressure loss can be a slow leak or valve issue. Uneven wear usually means alignment is off. A sidewall bulge means internal structural damage.",
            "A tyre with a sidewall bulge needs replacing immediately — it can burst without warning. This is something an OBD scan cannot check at all.",
            beyondObdScope = true,
            urgency = "CRITICAL"
        ),
        GeneralTopic(
            listOf("compression", "cylinder compression", "compression test"),
            "Engine compression",
            "Concern about low compression or engine power loss.",
            "Low compression can be worn piston rings, valve issues, or a head gasket problem.",
            "A compression test needs a mechanic with the right tooling — this is a physical test an OBD scanner cannot perform at all.",
            beyondObdScope = true
        ),
        GeneralTopic(
            listOf("rust", "body", "paint", "dent"),
            "Body, rust or paint condition",
            "Visible rust, paint damage, or dents.",
            "Usually moisture reaching bare metal through a paint chip, especially after monsoon season.",
            "Address small rust spots early (sand, prime, paint) before they spread. This is a visual inspection item, not something OBD reports on.",
            beyondObdScope = true,
            urgency = "MINOR"
        ),
        GeneralTopic(
            listOf("oil pressure", "oil light", "oil warning"),
            "Oil pressure warning light",
            "The oil pressure/oil can warning light comes on, often with a beep.",
            "Low oil level, a failing oil pump, or a worn engine losing oil pressure — the engine's moving parts can be running without enough lubrication.",
            "Stop the engine as soon as it's safe to do so — do not keep driving. Running an engine with low oil pressure for even a short distance can cause permanent, expensive damage. Check the oil level once stopped; if it's not obviously just low oil, this needs a tow, not a drive to the mechanic.",
            urgency = "CRITICAL"
        ),
        GeneralTopic(
            listOf("brake fail", "brakes fail", "no brakes", "brake pedal", "pedal to the floor", "pedal goes to the floor", "lost brakes", "brakes not working"),
            "Brakes failing or pedal going soft",
            "The brake pedal feels soft, sinks to the floor, or braking performance suddenly drops.",
            "Most often a brake fluid leak or air in the brake lines; less commonly a failing master cylinder.",
            "This is a stop-driving situation. Pull over using engine braking and the handbrake if needed, and do not continue driving the vehicle. This needs a tow to a mechanic, not a drive there — brake failure can get worse without warning.",
            urgency = "CRITICAL"
        )
    )

    /** Finds the best-matching topic for free text, or null if nothing matches well enough to answer confidently. */
    fun findTopic(text: String): GeneralTopic? {
        val q = text.lowercase()
        return topics.firstOrNull { topic -> topic.keywords.any { keyword -> matchesKeyword(q, keyword) } }
    }

    /**
     * A real-device test asked "what is the best place to sell the car", and this matched the
     * AC topic — its "ac" keyword is a substring of "pl**ac**e". Only SHORT single-word keywords
     * (<=3 chars) need a whole-word match for that; longer ones deliberately keep plain substring
     * matching, since that's what lets e.g. "squeal" match "squealing" or "overheat" match
     * "overheating" — word-form variations this app relies on, not a bug. A multi-word phrase
     * keyword like "air conditioning" has essentially no collision risk either way.
     */
    private fun matchesKeyword(q: String, keyword: String): Boolean = when {
        " " in keyword -> keyword in q
        keyword.length <= 3 -> Regex("""\b${Regex.escape(keyword)}\b""").containsMatchIn(q)
        else -> keyword in q
    }

    /** The subset of topics that ask a clarifying question before answering in full — see [RuleBasedAiMechanicChatService]. */
    fun topicsWithClarifyingQuestion(): List<GeneralTopic> = topics.filter { it.clarifyingQuestion != null }
}
