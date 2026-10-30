package eu.luftiger.bits.bot.status

import eu.luftiger.bits.shared.DiscordRequestCount
import io.mockk.every
import io.mockk.mockk
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import kotlin.test.Test
import kotlin.test.assertEquals

class DiscordRequestCounterTest {
    @Test
    fun `answers of Discord are counted by kind`() {
        val counter = DiscordRequestCounter()

        listOf(200, 204, 429, 403, 404, 502).forEach { counter.intercept(chain(it)) }

        assertEquals(DiscordRequestCount(total = 6, rateLimited = 1, refused = 2, serverErrors = 1), counter.snapshot())
    }

    private fun chain(code: Int): Interceptor.Chain {
        val request = Request.Builder().url("https://discord.com/api/v10/guilds/1").build()
        val response =
            Response
                .Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("")
                .build()
        return mockk {
            every { request() } returns request
            every { proceed(request) } returns response
        }
    }
}
