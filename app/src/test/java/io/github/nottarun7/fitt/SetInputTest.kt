package io.github.nottarun7.fitt

import io.github.nottarun7.fitt.data.SetInput
import org.junit.Assert.*
import org.junit.Test

class SetInputTest {
    @Test fun `blank load zero load and fractional loads remain distinct`() {
        assertEquals(8 to null, SetInput("8").validated())
        assertEquals(8 to 0.0, SetInput("8", "0").validated())
        assertEquals(8 to 42.5, SetInput("8", "42,5").validated())
        assertEquals(20 to 15.5, SetInput("20", "15.5", "LB", true).validated())
    }

    @Test fun `reps and time require positive whole numbers`() {
        listOf("", " ", "0", "-1", "1.5", "abc", "2147483648").forEach { invalid ->
            assertThrows(invalid, IllegalArgumentException::class.java) { SetInput(invalid).validated() }
        }
    }

    @Test fun `negative non finite or malformed load never becomes bodyweight`() {
        listOf("-0.5", "NaN", "Infinity", "-Infinity", "1e999", "abc", "1,2.3").forEach { invalid ->
            assertThrows(invalid, IllegalArgumentException::class.java) { SetInput("8", invalid).validated() }
        }
    }

    @Test fun `unknown weight units are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { SetInput("8", "10", "STONE").validated() }
    }
}
