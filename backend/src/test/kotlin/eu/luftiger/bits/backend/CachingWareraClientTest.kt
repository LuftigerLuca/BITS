package eu.luftiger.bits.backend

import eu.luftiger.bits.backend.shared.CachingWareraClient
import eu.luftiger.bits.warera.WareraApiException
import eu.luftiger.bits.warera.WareraClient
import eu.luftiger.bits.warera.WareraCountry
import eu.luftiger.bits.warera.WareraGovernment
import eu.luftiger.bits.warera.WareraMilitaryUnit
import eu.luftiger.bits.warera.WareraParty
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CachingWareraClientTest {
    private val clock = MutableClock()
    private val calls = mutableListOf<String>()
    private var down = false

    private val delegate =
        object : WareraClient by unused() {
            override fun getAllCountries(): List<WareraCountry> {
                calls += "countries"
                if (down) throw WareraApiException("down")
                return listOf(WareraCountry("de", "Germany"))
            }

            override fun getMilitaryUnit(muId: String): WareraMilitaryUnit {
                calls += muId
                return WareraMilitaryUnit(muId, "Deutsche Marine")
            }

            override fun getParties(countryId: String): List<WareraParty> {
                calls += "parties $countryId"
                if (down) throw WareraApiException("down")
                return listOf(WareraParty("p-$countryId", "Partei"))
            }

            override fun getGovernment(countryId: String): WareraGovernment {
                calls += "government $countryId"
                if (down) throw WareraApiException("down")
                return WareraGovernment(countryId)
            }
        }
    private val client = CachingWareraClient(delegate, clock)

    @Test
    fun `countries are loaded once per hour`() {
        repeat(3) { client.getAllCountries() }
        clock.advance(Duration.ofMinutes(59))
        client.getAllCountries()
        assertEquals(listOf("countries"), calls)

        clock.advance(Duration.ofMinutes(1))
        assertEquals(listOf(WareraCountry("de", "Germany")), client.getAllCountries())
        assertEquals(listOf("countries", "countries"), calls)
    }

    @Test
    fun `failures are not kept`() {
        down = true
        assertFailsWith<WareraApiException> { client.getAllCountries() }

        down = false
        client.getAllCountries()
        client.getAllCountries()
        assertEquals(listOf("countries", "countries"), calls)
    }

    @Test
    fun `military units are kept for ten minutes each`() {
        client.getMilitaryUnit("m1")
        client.getMilitaryUnit("m2")
        client.getMilitaryUnit("m1")
        clock.advance(Duration.ofMinutes(10))
        client.getMilitaryUnit("m1")

        assertEquals(listOf("m1", "m2", "m1"), calls)
    }

    @Test
    fun `parties and government are kept for ten minutes per country`() {
        repeat(2) {
            client.getParties("de")
            client.getGovernment("de")
        }
        client.getParties("fr")
        clock.advance(Duration.ofMinutes(9))
        client.getParties("de")
        client.getGovernment("de")
        assertEquals(listOf("parties de", "government de", "parties fr"), calls)

        clock.advance(Duration.ofMinutes(1))
        assertEquals(listOf(WareraParty("p-de", "Partei")), client.getParties("de"))
        assertEquals(WareraGovernment("de"), client.getGovernment("de"))
        assertEquals(listOf("parties de", "government de", "parties fr", "parties de", "government de"), calls)
    }

    @Test
    fun `failed parties and governments are not kept`() {
        down = true
        assertFailsWith<WareraApiException> { client.getParties("de") }
        assertFailsWith<WareraApiException> { client.getGovernment("de") }

        down = false
        repeat(2) {
            client.getParties("de")
            client.getGovernment("de")
        }
        assertEquals(listOf("parties de", "government de", "parties de", "government de"), calls)
    }

    private fun unused(): WareraClient =
        object : WareraClient {
            override fun getAllCountries() = TODO()

            override fun searchUserIds(text: String) = TODO()

            override fun getUser(userId: String) = TODO()

            override fun getCompanyIds(userId: String) = TODO()

            override fun getCompany(companyId: String) = TODO()

            override fun getGovernment(countryId: String) = TODO()

            override fun searchMilitaryUnitIds(text: String) = TODO()

            override fun getMilitaryUnit(muId: String) = TODO()

            override fun getParties(countryId: String) = TODO()
        }
}
