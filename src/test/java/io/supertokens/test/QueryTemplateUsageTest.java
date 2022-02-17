package io.supertokens.test;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchUnitRunner;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.runner.RunWith;

import java.sql.Connection;
import java.sql.PreparedStatement;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@RunWith(ArchUnitRunner.class) // Remove this line for JUnit 5!!
@AnalyzeClasses(packages = "io.supertokens.inmemorydb.queries")
public class QueryTemplateUsageTest {

    // @formatter:off
    @ArchTest
    public static final ArchRule discourage_use_of_unsafe_methods_rule = noClasses()
            .that().resideInAnyPackage("io.supertokens.inmemorydb.queries")
            .should().callMethod(Connection.class, "prepareStatement", String.class)
            .orShould().callMethod(PreparedStatement.class, "executeQuery")
            .orShould().callMethod(PreparedStatement.class, "executeUpdate")
            .because("We consistently want to use QueryExecutorTemplate in our DB queries to avoid memory leaks");
    // @formatter:on

}
