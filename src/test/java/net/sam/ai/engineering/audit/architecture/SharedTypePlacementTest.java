package net.sam.ai.engineering.audit.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(
        packages = "net.sam.ai.engineering.audit",
        importOptions = ImportOption.DoNotIncludeTests.class)
class SharedTypePlacementTest {

    @ArchTest
    static final ArchRule auditEventResidesInDomainShared = classes()
            .that()
            .haveFullyQualifiedName("net.sam.ai.engineering.audit.domain.shared.AuditEvent")
            .should()
            .resideInAPackage("..domain.shared..");

    @ArchTest
    static final ArchRule outcomeResidesInDomainShared = classes()
            .that()
            .haveFullyQualifiedName("net.sam.ai.engineering.audit.domain.shared.Outcome")
            .should()
            .resideInAPackage("..domain.shared..");

    @ArchTest
    static final ArchRule auditEventRepositoryResidesInPersistenceShared = classes()
            .that()
            .haveFullyQualifiedName(
                    "net.sam.ai.engineering.audit.infrastructure.persistence.shared.AuditEventRepository")
            .should()
            .resideInAPackage("..infrastructure.persistence.shared..");
}
