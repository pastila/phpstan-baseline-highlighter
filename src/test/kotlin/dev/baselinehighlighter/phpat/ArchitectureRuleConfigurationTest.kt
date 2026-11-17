package dev.baselinehighlighter.phpat

import org.junit.Assert.*
import org.junit.Test

class ArchitectureRuleConfigurationTest {
    private val yaml = """
        metadata:
          root_namespace: "Example\\Domain"
        contexts:
          Sales:
            - Orders
            - Billing
          Shared: ~
        layer_templates:
          Domain.Service:
            onePublicMethod: true
            naming:
              suffix: Service
            dependencies:
              type: canOnlyDependOn
              targets:
                - "{context}\\Domain"
              special:
                - type: canImplementButShouldNotDependOn
                  target: 'Shared\Infrastructure'
            implements:
              - 'Example\ServiceInterface'
          Disabled:
            onePublicMethod: false
        context_structure:
          Domain:
            - Service
        context_group_rules:
          foreignInterface:
            context_group: Sales
            subject: '{context}\Domain'
            targets:
              - '{otherContext}\Domain'
    """.trimIndent()

    private fun find(rule: String, texts: List<String> = listOf(yaml)) = ArchitectureRuleConfiguration.find(texts,
        "architectureRules", "phpat." + PhpatRuleNames.normalize("architectureRules:$rule"))

    @Test fun `locates each condition and only configured contexts with UTF16 ranges`() {
        listOf("onePublicMethod", "naming", "dependencies", "implements").forEach { field ->
            val match = find("Sales_Orders_Domain_Service_$field").single().second
            assertEquals("layer_templates.Domain.Service.$field", match.path)
            assertEquals(field, yaml.substring(match.start, match.end))
        }
        assertEquals("layer_templates.Domain.Service.dependencies.special.0", find("Shared_Domain_Service_special_0").single().second.path)
        assertEquals("context_structure", find("contextStructure_Sales_Orders").single().second.path)
        assertEquals("context_group_rules.foreignInterface", find("contextGroupRule_foreignInterface_Sales_Billing").single().second.path)
        assertTrue(find("contextGroupRule_foreignInterface_Shared").isEmpty())
        assertTrue(find("Missing_Domain_Service_dependencies").isEmpty())
        assertTrue(find("Sales_Orders_Disabled_onePublicMethod").isEmpty())
        val crlf = "\uFEFF# 😀 comment\r\n" + yaml.replace("\n", "\r\n")
        val match = find("Sales_Orders_Domain_Service_dependencies", listOf(crlf)).single().second
        assertEquals("dependencies", crlf.substring(match.start, match.end))
        val scalarOnNextLine = yaml + "\nother_section:\n  Example:\n    -LiteralValue\n"
        assertEquals(1, find("Sales_Orders_Domain_Service_dependencies", listOf(scalarOnNextLine)).size)
        val longScalar = yaml + "\nother_section:\n  reason: '" + "x".repeat(70000) + "'\n"
        assertEquals(1, find("Sales_Orders_Domain_Service_dependencies", listOf(longScalar)).size)
    }

    @Test fun `later files replace sections and normalization collisions remain distinct candidates`() {
        val override = "layer_templates:\n  UseCase:\n    onePublicMethod: true\n"
        assertTrue(find("Sales_Orders_Domain_Service_dependencies", listOf(yaml, override)).isEmpty())
        assertEquals(1, find("Sales_Orders_UseCase_onePublicMethod", listOf(yaml, override)).single().first)
        val colliding = "contexts:\n  App: ~\nlayer_templates:\n  Domain.Service:\n    naming:\n      suffix: A\n  Domain_Service:\n    naming:\n      suffix: B\n"
        assertEquals(2, find("App_Domain_Service_naming", listOf(colliding)).size)
    }

    @Test fun `rejects ambiguous YAML rather than inventing conditions`() {
        val invalid = listOf(
            yaml.replace("onePublicMethod: true", "onePublicMethod: true\n    onePublicMethod: false"),
            yaml.replace("Sales:", "Sales: &contexts"),
            yaml.replace("Sales:", "Sales: *contexts"),
            yaml.replace("suffix: Service", "suffix: |\n        Service"),
            yaml.replace("    naming:", "   naming:"),
            yaml.replace("suffix: Service", "suffix: 'unterminated"),
            yaml.replace("Sales:", "'Sales: # comment"),
            yaml.replace("  Sales:", "\tSales:")
        )
        invalid.forEach { assertTrue(it, find("Sales_Orders_Domain_Service_dependencies", listOf(it)).isEmpty()) }
        assertTrue(find("Sales_Orders_Domain_Service_dependencies", listOf(yaml, "contexts: []")).isEmpty())
    }

    @Test fun `reads only complete literal PHP path arrays and never evaluates expressions`() {
        assertEquals(listOf(true to "/../architecture.yaml", false to "/tmp/extra.yml"),
            ArchitectureRuleConfiguration.paths("protected function getYamlPaths(): array { return [__DIR__ . '/../architecture.yaml', '/tmp/extra.yml',]; }"))
        val longPath = "/" + "a".repeat(70000) + ".yaml"
        assertEquals(listOf(false to longPath), ArchitectureRuleConfiguration.paths("function getYamlPaths() { return ['$longPath']; }"))
        listOf(
            "return [__DIR__ . '/architecture.yaml', getenv('EXTRA')];",
            "if (true) return [__DIR__ . '/architecture.yaml'];",
            "return [__DIR__ . '/architecture.yaml']; run();",
            "return ['relative.yaml'];",
            "return [__DIR__ . '/arch*.yaml'];",
            "return [__DIR__ . '/config.php'];"
        ).forEach { assertNull(it, ArchitectureRuleConfiguration.paths("function getYamlPaths() { $it }")) }
    }
}
