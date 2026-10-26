package io.github.nottarun7.fitt

import io.github.nottarun7.fitt.catalogue.*
import io.github.nottarun7.fitt.data.TrackingMetric
import org.junit.Assert.*
import org.junit.Test
import java.net.URI

class CatalogueTest {
    @Test fun `catalogue has broad machine and calisthenics coverage with stable unique IDs`() {
        val entries = ExerciseCatalogue.all
        assertTrue(entries.size >= 190)
        assertEquals(entries.size, entries.map { it.id }.distinct().size)
        assertTrue(entries.all { it.id.matches(Regex("[a-z0-9-]+")) && it.name.isNotBlank() && it.muscles.isNotBlank() })
        assertTrue(ExerciseCatalogue.search("", "Machines").size >= 60)
        assertTrue(ExerciseCatalogue.search("", "Cables").size >= 25)
        assertTrue(ExerciseCatalogue.search("", "Calisthenics").size >= 60)
    }

    @Test fun `each progression has contiguous ordered steps and both rep and timed skills exist`() {
        assertTrue(ExerciseCatalogue.families.size >= 11)
        ExerciseCatalogue.families.forEach { family ->
            val progression = ExerciseCatalogue.progression(family)
            assertEquals((1..progression.size).toList(), progression.map { it.step })
        }
        assertEquals(TrackingMetric.SECONDS, ExerciseCatalogue.find("front-lever")!!.metric)
        assertEquals(TrackingMetric.REPS, ExerciseCatalogue.find("pullup")!!.metric)
    }

    @Test fun `search combines words equipment muscles and common spelling variants`() {
        val presses = ExerciseCatalogue.search("  CHEST  press  ", "Machines")
        assertTrue(presses.isNotEmpty())
        assertTrue(presses.all { "press" in it.name.lowercase() && it.equipment in setOf(Equipment.MACHINE, Equipment.SMITH) })
        assertTrue(ExerciseCatalogue.search("pushups").any { it.id == "pushup" })
        assertTrue(ExerciseCatalogue.search("legpress").any { it.id == "leg-press-45" })
        assertTrue(ExerciseCatalogue.search("abs").any { it.id == "plank" })
        assertTrue(ExerciseCatalogue.search("", "Calisthenics", "Core", "Front lever").all { it.family == "Front lever" })
        assertTrue(ExerciseCatalogue.search("not-an-existing-exercise").isEmpty())
    }

    @Test fun `assistance per side and timed metadata reach exercise choices`() {
        assertTrue(ExerciseCatalogue.find("assisted-pullup")!!.choice().assistance)
        assertTrue(ExerciseCatalogue.find("pistol-squat")!!.choice().perSide)
        assertEquals(TrackingMetric.SECONDS, ExerciseCatalogue.find("l-sit")!!.choice().metric)
    }

    @Test fun `unavailable media remains empty and every reference is an explicit trusted HTTPS guide`() {
        assertTrue(ExerciseCatalogue.all.all { it.imageUrl == null })
        assertTrue(ExerciseCatalogue.all.any { it.referenceUrl == null })
        val links = ExerciseCatalogue.all.mapNotNull { it.referenceUrl }
        assertTrue(links.isNotEmpty())
        links.forEach { url ->
            val uri = URI(url)
            assertEquals("https", uri.scheme)
            assertTrue(uri.host in setOf("www.acefitness.org", "www.hybridcalisthenics.com", "gmb.io"))
        }
    }
}
