package com.enginesense.app.vehicle.ai

data class ChatMessage(val fromUser: Boolean, val text: String)

/** A DTC shape: one letter (P/B/C/U) followed by 4 hex digits — e.g. P0301. */
private val DTC_PATTERN = Regex("""[PBCU][0-9A-F]{4}""")

/** Short pronouns that signal "this question refers back to whatever we were just discussing". */
private val FOLLOWUP_WORDS = setOf("it", "that", "this", "them", "those")

/** "mil" as a whole word only — a plain `"mil" in q` substring check would false-match "mileage", "similar", "familiar". */
private val MIL_WORD = Regex("""\bmil\b""")

/**
 * Common English function words excluded from [titleMatchesQuestion] below. A real-device test
 * asked "can you help with nearby mechanic store?" and got matched to a signal named "Distance
 * Driven With Warning Light On" — purely because "with" (length 4, so it passed the old
 * length-only check) is a literal word in both the signal's name and almost any sentence. Not
 * exhaustive, just the realistic collision risks for finding/signal titles in this app.
 */
private val COMMON_STOPWORDS = setOf(
    "with", "that", "this", "from", "have", "your", "about", "there", "into", "over",
    "under", "after", "before", "during", "while", "when", "where", "what", "which",
    "does", "will", "been", "were", "each", "more", "some", "such", "very", "just",
    "than", "then", "also", "only", "even", "both", "same", "most", "many", "much",
    "less", "long", "good", "well", "make", "take", "know", "want", "need", "like",
    "time", "here", "them", "they", "their", "these", "those", "would", "could", "should"
)

/**
 * A finding/signal title "matches" a question only via a word that's both long enough and
 * distinctive enough to actually indicate the question is about that thing — see
 * [COMMON_STOPWORDS]'s doc for the real false-match this replaces a bare length check with.
 */
private fun titleMatchesQuestion(title: String, q: String): Boolean =
    title.lowercase().split(" ").any { it.length > 3 && it !in COMMON_STOPWORDS && it in q }

/**
 * Conversational layer over an [AiVehicleContext]. Same ground rule as the report
 * service: answers must be traceable to the context's signals/findings. A question
 * about something not in the context should be answered with "I don't have data on
 * that" rather than a guess.
 */
interface AiMechanicChatService {
    suspend fun ask(context: AiVehicleContext, history: List<ChatMessage>, question: String): ChatMessage
}

/**
 * Whatever topic the conversation was last about — a current-scan finding, a common DTC
 * looked up from general knowledge, or a general symptom topic. Lets a short pronoun
 * follow-up ("is it urgent?") resolve to the thing actually being discussed instead of
 * silently falling through to "I don't have data on that".
 */
private data class Referent(
    val title: String,
    val severity: String? = null,
    val recommendedAction: String? = null,
    val beyondObdScope: Boolean = false
)

/**
 * Default, production-safe implementation: keyword-matches the question against the
 * context's findings/signals and answers only from those — no LLM call, so it can't
 * invent anything by construction. Still rule-based, not general NLU — the history-aware
 * follow-up handling and [GeneralAutomotiveKnowledge] fallback below widen what it can
 * answer, but none of this is a real language model; see [RemoteAiMechanicChatService]
 * for where actual general AI capability would come from.
 *
 * @param genericDtcExplainer Optional lookup for standard codes beyond [CommonDtcGlossary]'s
 * curated subset (production wires in [com.enginesense.app.obd.DtcRepositoryGenericExplainer]
 * over the full vendored database). Null in plain-Kotlin unit tests, which fall back to the
 * same "don't recognize" honesty as before — never silently loses the fallback behavior.
 */
class RuleBasedAiMechanicChatService(
    private val genericDtcExplainer: GenericDtcExplainer? = null
) : AiMechanicChatService {

    override suspend fun ask(context: AiVehicleContext, history: List<ChatMessage>, question: String): ChatMessage {
        val q = question.lowercase()
        val referent = if (looksLikeFollowUp(q)) findReferent(context, history) else null

        // A handful of common, specific questions get a direct deterministic answer
        // derived from the context's findings (or, for a pronoun follow-up, the referent).
        intentAnswer(context, q, referent)?.let { return ChatMessage(fromUser = false, text = it) }

        // Resolve a pending clarifying question before anything else: if the AI's last message
        // was a known topic's clarifying question, this message is the answer to it, not a new
        // question on its own — give the full structured answer now, folding the clarification
        // in as evidence, rather than re-matching the answer text against topic keywords (which
        // usually won't contain them — "only in traffic" doesn't mention "overheat").
        pendingClarification(history)?.let { topic ->
            return ChatMessage(fromUser = false, text = structuredSymptomAnswer(topic, clarification = question))
        }

        val matchedFinding = context.findings.firstOrNull { finding ->
            titleMatchesQuestion(finding.title, q) || finding.relatedDtcCodes.any { it.lowercase() in q }
        }
        if (matchedFinding != null) {
            return ChatMessage(
                fromUser = false,
                text = "${matchedFinding.summary}\n\n${matchedFinding.recommendedAction}"
            )
        }

        val matchedSignal = context.signals.firstOrNull { signal -> titleMatchesQuestion(signal.name, q) }
        if (matchedSignal != null) {
            return ChatMessage(
                fromUser = false,
                text = when (matchedSignal.status) {
                    "VERIFIED" -> "${matchedSignal.name} is ${matchedSignal.value} ${matchedSignal.unit}."
                    "UNSUPPORTED" -> "${matchedSignal.name} isn't supported on this vehicle, so I have no reading for it."
                    "SUPPORTED_NO_DATA" -> "${matchedSignal.name} is supported but didn't return data on this scan — worth trying again."
                    else -> "I don't have a reliable reading for ${matchedSignal.name} right now."
                }
            )
        }

        // The question names a well-known generic code (e.g. "what does P0301 mean?") that
        // isn't among THIS car's current findings — answer from general knowledge, clearly
        // labeled as such, rather than falling through to "I don't have data on that" for a
        // code that's actually well understood. See CommonDtcGlossary's doc for why this is
        // separate from DtcRepository (which explains codes this car actually reported).
        CommonDtcGlossary.findMentioned(question)?.let { info ->
            return ChatMessage(fromUser = false, text = structuredDtcAnswer(info))
        }

        // A code CommonDtcGlossary's curated ~20 entries don't cover, but the full vendored
        // database (when wired in) does — e.g. a less-common code than the glossary's subset.
        DTC_PATTERN.find(question.uppercase())?.let { match ->
            genericDtcExplainer?.explain(match.value, context.vehicleBrand)?.let { explained ->
                return ChatMessage(
                    fromUser = false,
                    text = "Your current scan didn't flag ${explained.code}, but in general: ${explained.plainLanguage}\n\n" +
                        "Urgency: ${urgencyLabel(explained.severity)}\n\n" +
                        "This is general knowledge about the code, not based on your specific scan — " +
                        "a mechanic can confirm it against your actual car."
                )
            }
        }

        // General, non-code symptom knowledge — works even with no scan at all. Clearly
        // labeled as general guidance, and flagged when it's something OBD can never verify.
        // A topic with a clarifying question asks it first (with safety guidance immediately
        // if urgent) rather than guessing at a cause with too little information; a topic
        // without one answers in full immediately, same as before.
        GeneralAutomotiveKnowledge.findTopic(question)?.let { topic ->
            if (topic.clarifyingQuestion != null) {
                return ChatMessage(
                    fromUser = false,
                    text = "${topic.advice}\n\nTo help narrow down the cause: ${topic.clarifyingQuestion}"
                )
            }
            // A MINOR topic (poor mileage, AC not cooling...) is a simple question that reads
            // better as a short, natural answer; CRITICAL/MODERATE topics get the full
            // 7-point structure, since the stakes justify spelling out every section.
            val text = if (topic.urgency == "MINOR") conciseSymptomAnswer(topic) else structuredSymptomAnswer(topic, clarification = null)
            return ChatMessage(fromUser = false, text = text)
        }

        // Looks like a DTC (a letter followed by 4 digits) but matched nothing above —
        // say so honestly rather than giving the generic "no data" fallback, per the
        // explicit requirement to distinguish "unknown" from "nothing flagged".
        DTC_PATTERN.find(question.uppercase())?.let { match ->
            return ChatMessage(
                fromUser = false,
                text = "I don't recognize ${match.value} — it's not in this car's current scan, and it's not one of the common codes I have general knowledge of. " +
                    "A full code lookup needs a proper DTC database, which only runs against codes your car actually reports."
            )
        }

        // Nothing matched directly, but the question looked like a pronoun follow-up and we
        // found what it's probably referring to earlier in the conversation.
        if (referent != null) {
            return ChatMessage(
                fromUser = false,
                text = "On ${referent.title.lowercase()}: ${referent.recommendedAction ?: "I don't have more specific guidance on that."}"
            )
        }

        // Short and ambiguous with nothing to go on — ask rather than dead-end.
        if (question.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size <= 2 && context.findings.isEmpty()) {
            return ChatMessage(
                fromUser = false,
                text = "Can you tell me a bit more — a symptom, a sound, a warning light, or a code like P0301?"
            )
        }

        if (!context.hasScan) {
            return ChatMessage(
                fromUser = false,
                text = "I haven't scanned your car yet, so I don't have specific data on that. I can still help with general " +
                    "questions — ask about a symptom (like \"why is my car overheating\") or run a scan for a tailored answer."
            )
        }

        if (context.findings.isEmpty()) {
            return ChatMessage(
                fromUser = false,
                text = "I don't have data on that specifically. Nothing was flagged in this scan, so I can't connect it to a known issue."
            )
        }

        return ChatMessage(
            fromUser = false,
            text = "I don't have data on that. Here's what I do know from this scan: " +
                context.findings.joinToString(", ") { it.title } + "."
        )
    }

    private fun looksLikeFollowUp(q: String): Boolean {
        val words = q.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return words.size <= 6 && FOLLOWUP_WORDS.any { it in words }
    }

    /** Walks the chat history backwards looking for the last user message that resolved to a known topic. */
    private fun findReferent(context: AiVehicleContext, history: List<ChatMessage>): Referent? {
        for (msg in history.asReversed()) {
            if (!msg.fromUser) continue
            val text = msg.text
            val tq = text.lowercase()

            val finding = context.findings.firstOrNull { f ->
                titleMatchesQuestion(f.title, tq) || f.relatedDtcCodes.any { it.lowercase() in tq }
            }
            if (finding != null) return Referent(finding.title, finding.severity, finding.recommendedAction)

            CommonDtcGlossary.findMentioned(text)?.let {
                return Referent(it.code, recommendedAction = it.drivingGuidance)
            }

            GeneralAutomotiveKnowledge.findTopic(text)?.let {
                return Referent(it.topic, recommendedAction = it.advice, beyondObdScope = it.beyondObdScope)
            }
        }
        return null
    }

    private fun intentAnswer(context: AiVehicleContext, q: String, referent: Referent?): String? {
        val worst = context.findings.minByOrNull { severityOrder(it.severity) }

        return when {
            "safe to drive" in q -> when {
                worst != null -> when (worst.severity) {
                    "CRITICAL" -> "No — ${worst.title.lowercase()} is flagged critical. Best to stop driving and get it looked at before continuing."
                    "MODERATE" -> "Probably okay for short trips, but don't put it off: ${worst.title.lowercase()} should get checked soon."
                    "MINOR" -> "Yes — the only thing flagged (${worst.title.lowercase()}) is minor."
                    else -> "Yes, with the usual caution — nothing serious was flagged."
                }
                context.hasScan -> "Yes — nothing was flagged in this scan."
                referent?.beyondObdScope == true -> "I can't tell from OBD data alone — ${referent.title.lowercase()} isn't something a scan can check. " +
                    (referent.recommendedAction ?: "Worth a physical inspection if you're unsure.")
                referent != null -> "I don't have a scan to confirm, but on ${referent.title.lowercase()} in general: " +
                    (referent.recommendedAction ?: "get it checked if you're unsure.")
                else -> "I don't have a scan to check — want to run one? I can't tell you if your car's safe to drive without reading its data first."
            }

            ("how urgent" in q || "urgent" in q) -> when {
                worst != null -> when (worst.severity) {
                    "CRITICAL" -> "Very urgent — ${worst.title}. ${worst.recommendedAction}"
                    "MODERATE" -> "Moderately urgent — ${worst.title}. ${worst.recommendedAction}"
                    "MINOR" -> "Low urgency — ${worst.title}. ${worst.recommendedAction}"
                    else -> "Nothing urgent was flagged in this scan."
                }
                referent != null -> "I don't have a scan verdict on this, but in general, ${referent.title.lowercase()}: " +
                    (referent.recommendedAction ?: "worth getting checked if you're unsure.")
                context.hasScan -> "Nothing urgent was flagged in this scan."
                else -> null
            }

            "ask the mechanic" in q || "ask my mechanic" in q || ("what should i ask" in q) -> {
                if (context.findings.isEmpty()) {
                    if (!context.hasScan) {
                        "I haven't scanned your car yet, so there's nothing scan-specific to ask about — run a scan first for a tailored list."
                    } else {
                        "Nothing was flagged, so there's nothing specific to ask about from this scan."
                    }
                } else {
                    "Worth asking about: " + context.findings.joinToString(" ") {
                        "\n• ${it.title} — is this safe to keep driving with, and what would it cost to fix?"
                    }
                }
            }

            "cost" in q || "how much" in q -> costAnswer(q, worst?.title ?: referent?.title)

            "vin" in q || "ecu" in q -> when {
                context.vin != null -> "Your VIN is ${context.vin}." + (context.ecuName?.let { " ECU: $it." } ?: "")
                context.ecuName != null -> "ECU: ${context.ecuName}. I don't have a VIN from this scan."
                !context.hasScan -> "I haven't scanned your car yet, so I don't have VIN or ECU info."
                else -> "This scan didn't read VIN/ECU info — not every adapter or vehicle answers Mode 09."
            }

            "check engine" in q || "malfunction indicator" in q || MIL_WORD.containsMatchIn(q) -> when (context.milOn) {
                true -> "Yes — the check-engine light (MIL) is on, per this scan."
                false -> "No — the check-engine light (MIL) was off on this scan."
                null -> if (context.hasScan) "This scan didn't read MIL status." else "I haven't scanned your car yet, so I don't know the check-engine light status."
            }

            "emissions" in q || "puc" in q || "pollution" in q -> when (context.readyForEmissionsTest) {
                true -> "Every emissions monitor this car has was ready as of this scan — that's a prerequisite for passing a PUC/emissions test, not a guarantee of passing one."
                false -> "At least one emissions monitor wasn't ready as of this scan — it may not pass a PUC/emissions check until it is. This isn't a prediction of pass/fail, just the readiness status."
                null -> if (context.hasScan) "This scan didn't read emissions readiness." else "I haven't scanned your car yet, so I don't have emissions readiness data."
            }

            else -> null
        }
    }

    private fun severityOrder(severity: String): Int = when (severity) {
        "CRITICAL" -> 0
        "MODERATE" -> 1
        "MINOR" -> 2
        else -> 3
    }

    /** If the AI's last message was a known topic's clarifying question, this reply is the answer to it. */
    private fun pendingClarification(history: List<ChatMessage>): GeneralTopic? {
        val last = history.lastOrNull() ?: return null
        if (last.fromUser) return null
        return GeneralAutomotiveKnowledge.topicsWithClarifyingQuestion()
            .firstOrNull { topic -> topic.clarifyingQuestion != null && last.text.endsWith(topic.clarifyingQuestion) }
    }

    /**
     * The required 7-point structure (what it means / evidence / possible causes / urgency /
     * safe next steps / what a mechanic should check / what would help), built from
     * [GeneralTopic]'s existing fields rather than adding new data for each point — "evidence"
     * and "what a mechanic should check" are the two genuinely derived from context. Used to
     * include a "questions to ask the mechanic" line too; dropped by request as not useful.
     */
    private fun structuredSymptomAnswer(topic: GeneralTopic, clarification: String?): String = buildString {
        append("${topic.topic}\n\n")
        append("What it means: ${topic.symptom}\n")
        append(
            if (clarification != null)
                "Evidence available: you mentioned \"$clarification\" — based on what you've told me, not a scan reading.\n"
            else
                "Evidence available: none from a scan — this is general guidance, not based on your specific car.\n"
        )
        append("Possible causes: ${topic.cause}\n")
        append("Urgency: ${urgencyLabel(topic.urgency)}\n")
        append("Safe next steps: ${topic.advice}\n")
        append("What a mechanic should check: ${topic.cause}\n")
        append(
            if (topic.beyondObdScope)
                "What would help: a physical inspection — an OBD scan can't check this at all.\n\n"
            else
                "What would help: running an OBD scan, which can confirm or rule out several of these causes.\n\n"
        )
        append("This is general automotive knowledge, not based on your specific scan.")
    }

    /**
     * A short, natural-language answer for MINOR-urgency topics — poor mileage, AC not
     * cooling, that kind of thing — where spelling out all 8 structured points would read as
     * bureaucratic overkill for a simple question. Still honest about being general knowledge,
     * not a scan-based diagnosis, and still only states possible causes, never a confirmed one.
     */
    private fun conciseSymptomAnswer(topic: GeneralTopic): String {
        val scopeNote = if (topic.beyondObdScope) " This is also something an OBD scan can't check at all." else ""
        return "${topic.symptom} Likely cause: ${topic.cause} ${topic.advice}$scopeNote\n\n" +
            "This is general automotive knowledge, not based on your specific scan."
    }

    /** Same 7-point structure, for a known standard code this car's current scan didn't flag. */
    private fun structuredDtcAnswer(info: CommonDtcInfo): String = buildString {
        append("Your current scan didn't flag ${info.code}, but here's what it generally means:\n\n")
        append("What it means: ${info.meaning}.\n")
        append("Evidence available: not flagged in your current scan — this is general knowledge about ${info.code}, not confirmed on your car.\n")
        append("Possible causes: ${info.possibleCauses}\n")
        append("Urgency: ${urgencyLabel(info.urgency)}\n")
        append("Safe next steps: ${info.drivingGuidance}\n")
        append("What a mechanic should check: ${info.diagnosticSteps}\n")
        append("What would help: confirming this code is actually present on your car via a real scan.")
    }

    /**
     * [RepairCostReference] is deliberately tentative/approximate, not real sourced pricing —
     * see its own doc for why that's allowed here specifically. Tries the current finding or
     * referent's title first (a specific thing this conversation is actually about), then the
     * raw question text (covers a direct "how much for a clutch job?" with no scan context at
     * all). Falls back to the original honest refusal when nothing in the table matches —
     * this never guesses a number for a category it doesn't have.
     */
    private fun costAnswer(q: String, subjectTitle: String?): String {
        val match = subjectTitle?.let { RepairCostReference.findByTitle(it) } ?: RepairCostReference.findByQuestion(q)
        return if (match != null) {
            "Tentative range for ${match.category.lowercase()}: ₹${"%,d".format(match.minInr)}–₹${"%,d".format(match.maxInr)}. " +
                "${match.note}. This is a rough estimate, not a real quote — it varies by city, workshop, and your car's segment. " +
                "Always confirm the actual price with your mechanic before agreeing."
        } else {
            "I don't have real repair-cost data for that specific issue — that needs a priced quote " +
                "from a workshop or a cost-estimation service, which isn't connected in this build. " +
                "I won't guess a number; ask a mechanic for a quote on: " +
                (subjectTitle ?: "your scan results") + "."
        }
    }

    private fun urgencyLabel(severity: String): String = when (severity) {
        "CRITICAL" -> "High — stop-driving or get-it-checked-promptly territory; see the safe-next-steps below."
        "MODERATE" -> "Moderate — worth getting checked soon, not necessarily today."
        "MINOR" -> "Low — safe to keep driving, address when convenient."
        else -> "Informational only."
    }
}

// RemoteAiMechanicChatService and FallbackAiMechanicChatService now live in their own files
// (RemoteAiMechanicChatService.kt, FallbackAiMechanicChatService.kt) — they grew well past
// "placeholder" size once actually implemented.
