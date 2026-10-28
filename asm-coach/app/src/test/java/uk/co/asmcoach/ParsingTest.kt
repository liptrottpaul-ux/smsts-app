package uk.co.asmcoach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uk.co.asmcoach.data.model.Evaluation
import uk.co.asmcoach.data.model.Persona
import uk.co.asmcoach.data.model.StrategyBrief
import uk.co.asmcoach.data.remote.AppJson
import uk.co.asmcoach.data.remote.GeminiClient
import uk.co.asmcoach.data.remote.JsonText
import uk.co.asmcoach.data.remote.UpdateChecker
import uk.co.asmcoach.data.remote.htmlToText
import uk.co.asmcoach.data.repo.JobRepository
import uk.co.asmcoach.data.repo.ListingParser
import java.time.LocalDate

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

    @Test
    fun `formats reed salary and date`() {
        assertEquals("£40,000 – £46,000", JobRepository.formatSalary(40000.0, 46000.0))
        assertEquals("£45,000", JobRepository.formatSalary(45000.0, null))
        assertNull(JobRepository.formatSalary(null, null))
        assertEquals(LocalDate.of(2026, 10, 27), JobRepository.parseReedDate("27/10/2026"))
        assertNull(JobRepository.parseReedDate("yesterday"))
    }

    @Test
    fun `strips html from job descriptions`() {
        val text = htmlToText("<p>SMSTS &amp; First Aid</p><ul><li>CSCS</li></ul>Salary &pound;45k")
        assertEquals("SMSTS & First Aid\n• CSCS\nSalary £45k", text)
    }

    @Test
    fun `reads build number from release tag`() {
        assertEquals(9, UpdateChecker.buildNumber("asm-coach-build-9"))
        assertNull(UpdateChecker.buildNumber("v1.0"))
    }

    @Test
    fun `separates single vacancies from search pages`() {
        assertEquals(true, ListingParser.isSingleVacancy("https://uk.indeed.com/viewjob?jk=abc123"))
        assertEquals(false, ListingParser.isSingleVacancy("https://uk.indeed.com/q-assistant-site-manager-l-liverpool-jobs.html"))
        assertEquals(true, ListingParser.isSingleVacancy("https://www.reed.co.uk/jobs/assistant-site-manager/54123456"))
        assertEquals(false, ListingParser.isSingleVacancy("https://www.reed.co.uk/jobs/assistant-site-manager-jobs-in-manchester"))
    }

    @Test
    fun `picks a usable gemini model`() {
        assertEquals("gemini-flash-latest", GeminiClient.pickModel(listOf("gemini-2.5-flash", "gemini-flash-latest")))
        assertEquals("gemini-3.0-flash", GeminiClient.pickModel(listOf("gemini-2.5-flash", "gemini-3.0-flash", "gemini-3.0-flash-lite", "gemini-3.5-flash-preview")))
        assertNull(GeminiClient.pickModel(listOf("text-embedding-004")))
    }

    @Test
    fun `question bank is consistent and deep`() {
        val bank = uk.co.asmcoach.data.content.QuestionBank.all
        assertEquals(bank.size, bank.map { it.id }.toSet().size)
        assertEquals(true, bank.size >= 80)
        assertEquals(true, bank.all { it.category in uk.co.asmcoach.data.model.QuestionCategory.ALL })
        assertEquals(true, bank.all { it.probes.isNotEmpty() && it.lookingFor.isNotBlank() })
    }

    @Test
    fun `detects employer sector from listing text`() {
        assertEquals(uk.co.asmcoach.data.content.Sector.Housebuilder, uk.co.asmcoach.data.content.QuestionBank.sectorOf("120 plot NHBC development"))
        assertEquals(uk.co.asmcoach.data.content.Sector.Refurb, uk.co.asmcoach.data.content.QuestionBank.sectorOf("Live school refurbishment"))
    }

    @Test
    fun `extracts single vacancies from a board search page`() {
        val md = """
            # Assistant Site Manager jobs in Liverpool
            [Assistant Site Manager](https://uk.indeed.com/rc/clk?jk=abc123&from=serp) Acme Homes Liverpool £42,000 a year
            [Sign in](https://uk.indeed.com/account/login)
            [Assistant Site Manager - Housing](https://uk.indeed.com/viewjob?jk=def456) Wirral
        """.trimIndent()
        val found = ListingParser.vacanciesFromMarkdown(md, "https://uk.indeed.com/q-assistant-site-manager-l-liverpool-jobs.html")
        assertEquals(2, found.size)
        assertEquals("Assistant Site Manager", found[0].title)
        assertEquals(true, found[0].context.contains("£42,000"))

        val html = """<a class="job" href="/jobs/assistant-site-manager/54123456?source=search">Assistant <b>Site</b> Manager</a> Manchester
            <a href="/jobs/assistant-site-manager-jobs-in-manchester">More jobs</a>"""
        val reed = ListingParser.vacanciesFromHtml(html, "https://www.reed.co.uk/jobs/assistant-site-manager-jobs-in-manchester")
        assertEquals(1, reed.size)
        assertEquals("https://www.reed.co.uk/jobs/assistant-site-manager/54123456?source=search", reed[0].url)
    }

    @Test
    fun `classifies engine voices by gender`() {
        val cat = uk.co.asmcoach.voice.VoiceCatalog
        val male = uk.co.asmcoach.voice.VoiceGender.Male
        val female = uk.co.asmcoach.voice.VoiceGender.Female
        assertEquals(male, cat.genderOf("en-gb-x-gbb-network", emptySet()))
        assertEquals(male, cat.genderOf("en-gb-x-rjs-local", emptySet()))
        assertEquals(female, cat.genderOf("en-gb-x-gba-local", emptySet()))
        assertEquals(female, cat.genderOf("en-GB-SMTf00", emptySet()))
        assertEquals(male, cat.genderOf("en-GB-SMTm00", emptySet()))
        assertEquals(female, cat.genderOf("Some Voice", setOf("female")))
        assertEquals(uk.co.asmcoach.voice.VoiceGender.Unknown, cat.genderOf("en-gb-x-zzz-local", emptySet()))
    }

    @Test
    fun `panel seats get gender-matched distinct voice slots`() {
        val seats = uk.co.asmcoach.ui.roleplay.buildSeats(null, uk.co.asmcoach.data.model.InterviewMode.StandardPanel)
        assertEquals(listOf("Male", "Female", "Male"), seats.map { it.gender.name })
        assertEquals(listOf(0, 0, 1), seats.map { it.slot })
    }

    @Test
    fun `reads the full advert from JobPosting structured data`() {
        val html = """
            <html><head><script type="application/ld+json">
            {"@context":"https://schema.org","@type":"JobPosting","title":"Assistant Site Manager",
             "description":"&lt;p&gt;Join our &lt;b&gt;NHBC&lt;/b&gt; award-winning team on a 180-plot scheme in Warrington. You will manage trades, RAMS and key-stage inspections.&lt;/p&gt;&lt;ul&gt;&lt;li&gt;SMSTS&lt;/li&gt;&lt;li&gt;First Aid&lt;/li&gt;&lt;/ul&gt;",
             "hiringOrganization":{"@type":"Organization","name":"Acme Homes"},
             "jobLocation":{"@type":"Place","address":{"addressLocality":"Warrington"}},
             "baseSalary":{"value":{"minValue":42000,"maxValue":48000}}}
            </script></head><body>nav</body></html>
        """.trimIndent()
        val d = ListingParser.jobPosting(html)!!
        assertEquals("Acme Homes", d.company)
        assertEquals("Warrington", d.location)
        assertEquals(true, d.text.contains("180-plot scheme"))
        assertEquals(true, d.text.contains("• SMSTS"))
        assertEquals(false, d.text.contains("<b>"))
    }

    @Test
    fun `keeps site roles and drops salary pages`() {
        assertEquals(true, ListingParser.isSiteRole("Assistant Site Manager - Housing"))
        assertEquals(true, ListingParser.isSiteRole("Assistant Construction Manager"))
        assertEquals(false, ListingParser.isSiteRole("Manager Manufacturing salaries in Preston"))
        assertEquals(false, ListingParser.isSiteRole("Assistant Site Manager salaries in Preston"))
        assertEquals(false, ListingParser.isSiteRole("Store Manager"))
    }

    @Test
    fun `builds starter prep from the advert without AI`() {
        val advert = """
            We are looking for an Assistant Site Manager for a 180-plot development in Warrington.
            • Experience managing subcontractors and trades on new build housing
            • Knowledge of NHBC key stage inspections and build quality standards
            • Valid SMSTS, CSCS and First Aid certificates are essential
            • Ability to keep the build programme on track
        """.trimIndent()
        val (topics, questions) = uk.co.asmcoach.data.content.LocalPackBuilder.build(advert, "Assistant Site Manager plots NHBC")
        assertEquals(true, topics.isNotEmpty())
        assertEquals(true, topics.any { it.title.contains("NHBC") && it.listingRef.contains("NHBC") })
        assertEquals(true, questions.any { it.question.contains("subcontractors") })
        assertEquals(true, questions.all { it.lookingFor.isNotBlank() })
    }

    @Test
    fun companyFromAdvertText() {
        assertEquals("Persimmon Homes", uk.co.asmcoach.data.repo.ListingParser.company("Persimmon Homes are looking for an Assistant Site Manager in Wigan."))
        assertEquals("Acme Build", uk.co.asmcoach.data.repo.ListingParser.company("Assistant Site Manager\nCompany: Acme Build\nLocation: Bolton"))
        assertEquals(null, uk.co.asmcoach.data.repo.ListingParser.company("our client is a leading housebuilder"))
    }
}
