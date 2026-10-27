package uk.co.asmcoach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uk.co.asmcoach.data.model.Evaluation
import uk.co.asmcoach.data.model.Persona
import uk.co.asmcoach.data.model.StrategyBrief
import uk.co.asmcoach.data.remote.AppJson
import uk.co.asmcoach.data.remote.JsonText
import uk.co.asmcoach.data.repo.ListingParser

class ParsingTest {

    @Test
    fun `extracts json from fenced model reply`() {
        val raw = "Here you go:\n```json\n{\"overallScore\": 72, \"headline\": \"Solid\"}\n```"
        val evaluation = AppJson.decodeFromString(Evaluation.serializer(), JsonText.extract(raw))
        assertEquals(72, evaluation.overallScore)
        assertEquals("Solid", evaluation.headline)
    }

    @Test
    fun `tolerates missing and unknown fields`() {
        val raw = """{"compatibilityScore": 81, "plan": {"days30": {"theme": "Learn"}}, "extra": true}"""
        val brief = AppJson.decodeFromString(StrategyBrief.serializer(), raw)
        assertEquals(81, brief.compatibilityScore)
        assertEquals("Learn", brief.plan.days30.theme)
        assertEquals(emptyList<String>(), brief.keywords)
    }

    @Test
    fun `parses persona by enum name role or display name`() {
        assertEquals(Persona.CommercialManager, Persona.parse("CommercialManager"))
        assertEquals(Persona.SafetyAuditor, Persona.parse("H&S Auditor"))
        assertEquals(Persona.ProjectDirector, Persona.parse("graham holt"))
        assertNull(Persona.parse("Receptionist"))
    }

    @Test
    fun `pulls salary and town from listing text`() {
        val text = "Assistant Site Manager - Warrington. £42,000 - £48,000 per annum plus car"
        assertEquals("£42,000 - £48,000 per annum", ListingParser.salary(text))
        assertEquals("Warrington", ListingParser.location(text))
        assertNull(ListingParser.location("Remote role, anywhere"))
    }
}
