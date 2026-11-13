package dev.baselinehighlighter.phpat

import org.junit.Assert.*
import org.junit.Test

class PhpatConfigurationTest {
    @Test fun `reads includes and tagged services without treating arguments as services`() {
        val config = PhpatConfiguration.parse("""
            includes:
                - 'rules.neon' # comment
                - "baseline.neon"
            services:
                -
                    class: Tests\Architecture
                    arguments:
                        class: NotAService
                        tags: [phpat.test]
                    tags:
                        - phpat.test
                - class: Tests\Inline
                  tags: ['phpat.test']
                factory: Tests\WithArguments('App', regex: false)
                    tags:
                        phpat.test: true
                Tests\Named:
                    tags: [phpat.test]
                unrelated:
                    class: Tests\Unrelated
                    arguments:
                        tags: [phpat.test]
            parameters:
                includes:
                    - ignored.neon
        """.trimIndent())
        assertEquals(listOf("rules.neon", "baseline.neon"), config.includes)
        assertEquals(listOf("Tests\\Architecture", "Tests\\Inline", "Tests\\WithArguments", "Tests\\Named"), config.testClasses)
    }

    @Test fun `handles tabs BOM CRLF quotes and rejects dynamic service expressions`() {
        val config = PhpatConfiguration.parse("\uFEFFincludes:\r\n\t- '../baseline.neon'\r\nservices:\r\n\t-\r\n\t\tclass: '%testClass%'\r\n\t\ttags: [phpat.test]\r\n")
        assertEquals(listOf("../baseline.neon"), config.includes)
        assertTrue(config.testClasses.isEmpty())
    }

    @Test fun `normalizes names as PHPat and only uses collection methods for prefix matches`() {
        assertEquals("testDomainDependencies", PhpatRuleNames.normalize("test_domain_dependencies"))
        assertEquals("architectureRulesClientFinanceDomainServiceDependencies", PhpatRuleNames.normalize("architectureRules:Client_Finance_Domain_Service_dependencies"))
        assertEquals("a.b", PhpatRuleNames.normalize("..a...b.."))
        assertTrue(PhpatRuleNames.matches("phpat.testDomainDependencies", "test_domain_dependencies", false))
        assertFalse(PhpatRuleNames.matches("phpat.testDomainDependenciesExtra", "test_domain_dependencies", false))
        assertTrue(PhpatRuleNames.matches("phpat.architectureRulesClientFinance", "architectureRules", true))
        assertTrue(PhpatRuleNames.matches("phpat.architectureRules", "architectureRules", true))
        assertFalse(PhpatRuleNames.matches("custom.architectureRulesClientFinance", "architectureRules", true))
        assertTrue(PhpatRuleNames.returnsCollection("#[TestRule] public function rules(): array { return []; }"))
        assertTrue(PhpatRuleNames.returnsCollection("public function test_rules(): \\Generator { yield 1; }"))
        assertFalse(PhpatRuleNames.returnsCollection("public function test_rule(): Rule { return factory(); }"))
    }
}
