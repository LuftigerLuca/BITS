package eu.luftiger.bits.backend

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.web.server.ResponseStatusException
import kotlin.test.Test

/**
 * Services know nothing about HTTP, see "Errors" in CONTRIBUTING.md: they throw a `DomainException`, which
 * `ApiExceptionHandler` turns into a status.
 */
class ServiceLayerTest {
    @Test
    fun `services do not use HTTP statuses`() {
        val classes =
            ClassFileImporter()
                .withImportOption(ImportOption.DoNotIncludeTests())
                .importPackages("eu.luftiger.bits.backend")
        noClasses()
            .that()
            .haveSimpleNameEndingWith("Service")
            .should()
            .dependOnClassesThat()
            .belongToAnyOf(ResponseStatusException::class.java, HttpStatus::class.java, HttpStatusCode::class.java)
            .check(classes)
    }
}
