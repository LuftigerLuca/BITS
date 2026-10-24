package eu.luftiger.bits.backend

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import kotlin.test.Test

/** Without `BITS_ADMIN_USERNAME` and `BITS_ADMIN_PASSWORD_HASH`, only the Discord login is available. */
@SpringBootTest(properties = ["bits.service-token=test-token"])
@AutoConfigureMockMvc
@Import(TestcontainersConfig::class)
class AdminLoginDisabledTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `admin login is disabled`() {
        mockMvc.get("/auth/options").andExpect { jsonPath("$.adminLogin") { value(false) } }
        mockMvc
            .post("/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"username":"","password":""}"""
            }.andExpect { status { isNotFound() } }
    }
}
