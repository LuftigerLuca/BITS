package eu.luftiger.bits.backend

import com.tngtech.archunit.base.DescribedPredicate.not
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields
import kotlinx.serialization.Serializable
import kotlin.test.Test

/**
 * A module's `domain/` is private, see "Backend architecture" in CONTRIBUTING.md. A domain type in a DTO would let a
 * change to the data model change the API without anybody noticing.
 */
class DtoBoundaryTest {
    @Test
    fun `DTOs do not use domain types`() {
        val classes =
            ClassFileImporter()
                .withImportOption(ImportOption.DoNotIncludeTests())
                .importPackages("eu.luftiger.bits.backend")
        fields()
            .that()
            .areDeclaredInClassesThat()
            .areAnnotatedWith(Serializable::class.java)
            .and()
            .areDeclaredInClassesThat(not(resideInAPackage("..domain..")))
            .should()
            .notHaveRawType(resideInAPackage("..domain.."))
            .check(classes)
    }
}
