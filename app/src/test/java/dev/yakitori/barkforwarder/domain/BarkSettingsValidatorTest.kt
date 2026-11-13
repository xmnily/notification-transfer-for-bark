package dev.yakitori.barkforwarder.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BarkSettingsValidatorTest {
    @Test
    fun `accepts bark url`() {
        val validated = BarkSettingsValidator.validate(
            barkUrlOrKey = "https://api.day.app/ABCDEFGHIJKLMNOP",
        )

        assertThat(validated.endpoint.serverUrl).isEqualTo("https://api.day.app/push")
        assertThat(validated.endpoint.deviceKey).isEqualTo("ABCDEFGHIJKLMNOP")
    }

    @Test
    fun `accepts bare device key`() {
        val validated = BarkSettingsValidator.validate(
            barkUrlOrKey = "ABCDEFGHIJKLMNOP",
        )

        assertThat(validated.endpoint.serverUrl).isEqualTo("https://api.day.app/push")
        assertThat(validated.endpoint.deviceKey).isEqualTo("ABCDEFGHIJKLMNOP")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects blank input`() {
        BarkSettingsValidator.validate(
            barkUrlOrKey = "   ",
        )
    }
}
