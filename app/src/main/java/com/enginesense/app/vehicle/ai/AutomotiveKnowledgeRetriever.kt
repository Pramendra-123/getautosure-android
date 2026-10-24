package com.enginesense.app.vehicle.ai

/**
 * Curated automotive knowledge relevant to a question, for grounding a remote LLM's answer —
 * added without changing [AiMechanicChatService]'s contract at all. [RemoteAiMechanicChatService]
 * takes one as an optional constructor parameter and folds its results into the outgoing
 * request as `knowledgeHints`; [RuleBasedAiMechanicChatService] doesn't need one; it already
 * consults [CommonDtcGlossary]/[GeneralAutomotiveKnowledge] directly.
 */
interface AutomotiveKnowledgeRetriever {
    fun relevantEntries(question: String, limit: Int = 3): List<String>
}

/**
 * Backed entirely by the existing curated tables — no new dataset. The point isn't new
 * knowledge, it's handing a remote model the same grounding the local rule-based service
 * already uses, so it has less reason to invent an answer instead of saying "I don't know."
 */
class LocalAutomotiveKnowledgeRetriever : AutomotiveKnowledgeRetriever {
    override fun relevantEntries(question: String, limit: Int): List<String> {
        val hints = mutableListOf<String>()

        CommonDtcGlossary.findMentioned(question)?.let { info ->
            hints += "${info.code}: ${info.meaning}. Causes: ${info.possibleCauses} Urgency: ${info.urgency}."
        }
        GeneralAutomotiveKnowledge.findTopic(question)?.let { topic ->
            hints += "${topic.topic}: ${topic.symptom} Likely cause: ${topic.cause}"
        }

        return hints.take(limit)
    }
}
