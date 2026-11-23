package com.enginesense.app.vehicle.cards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FaultCardRepositoryTest {

    private fun text(title: String) =
        """{"title":"$title","meaning":"m","notice":["n"],"causes":["c1","c2"],"drive":"d","first_check":"f","cost_parts":["p"],"india_note":""}"""

    private val base = """[
      {"code":"P0300","brand":null,"urgency":"NOW","fuel":["Petrol"],"costs":[[1000,4000]],"text":{"en":${text("Misfire")},"hi":${text("मिसफ़ायर")}}},
      {"code":"P1100","brand":"TATA","urgency":"SOON","fuel":["Diesel"],"costs":[[500,2000]],"text":{"en":${text("Tata air-flow")}}},
      {"code":"P1100","brand":null,"urgency":"WAIT","fuel":["Petrol"],"costs":[[100,200]],"text":{"en":${text("Generic P1100")}}}
    ]"""
    private val tamil = """[{"code":"P0300","brand":null,${text("மிஸ்ஃபயர்").removePrefix("{")}]"""

    private val cards = FaultCardRepository.parse(base, mapOf("ta" to tamil))

    @Test
    fun `cards carry every language they were given`() {
        val misfire = FaultCardRepository.find(cards, "p0300", null)!!
        assertEquals(CardUrgency.NOW, misfire.urgency)
        assertEquals(1000..4000, misfire.costs.single())
        assertEquals("மிஸ்ஃபயர்", misfire.textIn("ta").title)
        assertEquals("मिसफ़ायर", misfire.textIn("hi").title)
        assertEquals(listOf("c1", "c2"), misfire.textIn("en").causes)
    }

    @Test
    fun `a language not translated yet falls back to English`() {
        val misfire = FaultCardRepository.find(cards, "P0300", null)!!
        assertFalse(misfire.has("te"))
        assertEquals("Misfire", misfire.textIn("te").title)
    }

    @Test
    fun `the maker's own card wins for that maker's cars, the standard one otherwise`() {
        assertEquals("Tata air-flow", FaultCardRepository.find(cards, "P1100", "Tata Motors")!!.textIn("en").title)
        assertEquals("Generic P1100", FaultCardRepository.find(cards, "P1100", "Hyundai")!!.textIn("en").title)
        assertNull(FaultCardRepository.find(cards, "P9999", null))
    }

    @Test
    fun `maker names map to the card makers`() {
        assertEquals("SUZUKI", FaultCardRepository.makerKey("Maruti Suzuki"))
        assertEquals("MAHINDRA", FaultCardRepository.makerKey("Mahindra"))
        assertNull(FaultCardRepository.makerKey("Kia"))
        assertNull(FaultCardRepository.makerKey(null))
    }

    @Test
    fun `labels exist for every app language`() {
        for (lang in com.enginesense.app.storage.AppLanguage.entries) {
            assertTrue(lang.code, FaultCardLabels.of(lang.code).urgency.size == CardUrgency.entries.size)
        }
    }
}
