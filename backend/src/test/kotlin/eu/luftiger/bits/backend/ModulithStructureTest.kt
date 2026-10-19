package eu.luftiger.bits.backend

import org.springframework.modulith.core.ApplicationModules
import kotlin.test.Test

/** Fails if a module reaches into another module's `domain` or `internal` package, or modules depend on each other in a cycle. */
class ModulithStructureTest {
    @Test
    fun `module structure is valid`() {
        ApplicationModules.of(BitsBackendApplication::class.java).verify()
    }
}
