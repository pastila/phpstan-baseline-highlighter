package dev.baselinehighlighter.phpat

import com.intellij.codeInsight.navigation.CtrlMouseData
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.TextRange
import com.intellij.platform.backend.documentation.DocumentationLinkHandler
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import dev.baselinehighlighter.documentation.BaselineDocumentationTargetProvider
import java.util.concurrent.CompletableFuture

class PhpatNavigationTest : BasePlatformTestCase() {
    override fun createTempDirTestFixture() = TempDirTestFixtureImpl()
    override fun setUp() {
        super.setUp()
        com.intellij.testFramework.PsiTestUtil.addContentRoot(module, myFixture.tempDirFixture.findOrCreateDir(""))
    }

    private fun php(name: String = "Rules", method: String = "domainCanOnlyDependOn", type: String = "Rule", attribute: String = "#[ArchitectureRule]"): PsiFile =
        myFixture.addFileToProject("tests/$name.php", """
            <?php
            namespace Tests;
            use PHPat\Test\Attributes\TestRule as ArchitectureRule;
            use PHPat\Test\Builder\Rule;
            class $name {
                $attribute
                public function $method(): $type { throw new \Exception(); }
                public function notARule(): Rule { throw new \Exception(); }
                #[ArchitectureRule]
                private function privateRule(): Rule { throw new \Exception(); }
            }
        """.trimIndent())

    private fun baseline(identifier: String = "phpat.domainCanOnlyDependOn"): PsiFile {
        val file = myFixture.addFileToProject("baselines/architecture.neon", """
            parameters:
              ignoreErrors:
                -
                  message: '#^Old architecture violation$#'
                  identifier: '$identifier' # comment
                  path: ../src/Example.php
        """.trimIndent() + "\n")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        com.intellij.testFramework.IndexingTestUtil.waitUntilIndexesAreReady(project)
        return myFixture.file
    }

    private fun config(classes: List<String> = listOf("Rules")): PsiFile {
        myFixture.addFileToProject("config/includes.neon", "includes:\n  - ../baselines/architecture.neon\n  - ../phpstan.neon.dist\n")
        return myFixture.addFileToProject("phpstan.neon.dist", "includes:\n  - config/includes.neon\nservices:\n" + classes.joinToString("") {
            "  -\n    class: Tests\\$it\n    tags: [phpat.test]\n"
        })
    }

    private fun hoverAt(file: PsiFile, offset: Int): CtrlMouseData? {
        val result = CompletableFuture.supplyAsync {
            ReadAction.compute<CtrlMouseData?, RuntimeException> {
                GotoDeclarationAction().getCtrlMouseData(myFixture.editor, file, offset)
            }
        }
        PlatformTestUtil.waitWithEventsDispatching("PHPat Ctrl-hover lookup did not complete", { result.isDone }, 15)
        return result.get()
    }

    fun testRegisteredCtrlNavigationAndDocumentationUseTheRuleDeclaration() {
        val rule = php()
        config()
        val source = baseline()
        val identifier = "phpat.domainCanOnlyDependOn"
        assertEquals("indexed PHP files", 1, com.intellij.psi.search.FilenameIndex.getAllFilesByExt(project, "php", com.intellij.psi.search.GlobalSearchScope.projectScope(project)).size)
        assertEquals("PHP adapter rule discovery ${dev.baselinehighlighter.php.PhpDeclarationProvider.forFile(rule)!!.declarations(rule)}", 1, dev.baselinehighlighter.php.PhpDeclarationProvider.EP.extensionList.flatMap { it.phpatMethods(project, "Tests\\Rules") }.size)
        val start = source.text.indexOf(identifier)
        val targets = PhpatRuleResolver.resolve(source, identifier)
        assertEquals(1, targets.size)
        assertEquals("domainCanOnlyDependOn", targets.single().element.text)
        assertFalse(targets.single().method.collection)
        val hover = hoverAt(source, start + 3)!!
        assertTrue(hover.isNavigatable)
        assertEquals(listOf(TextRange(start, start + identifier.length)), hover.ranges)
        val doc = BaselineDocumentationTargetProvider().documentationTargets(source, start).single()
        val hint = doc.computeDocumentationHint()!!
        val url = PhpatDocumentationLinkHandler.url(targets.single().label)
        assertTrue(hint.contains(url.replace("&", "&amp;")))
        assertFalse(hint.contains("error-identifiers/phpat"))
        assertNotNull(doc.navigatable)
        assertTrue(DocumentationLinkHandler.EP_NAME.extensionList.any { it is PhpatDocumentationLinkHandler && it.resolveLink(doc, url) != null })
        myFixture.editor.caretModel.moveToOffset(start + 3)
        myFixture.performEditorAction(IdeActions.ACTION_GOTO_DECLARATION)
        PlatformTestUtil.waitWithEventsDispatching("Rule declaration did not open: ${FileEditorManager.getInstance(project).selectedFiles.toList()}", {
            rule.virtualFile in FileEditorManager.getInstance(project).selectedFiles
        }, 15)
        assertEquals(rule.text.indexOf("domainCanOnlyDependOn"), FileEditorManager.getInstance(project).selectedTextEditor!!.caretModel.offset)
    }

    fun testOnlyRegisteredPublicRulesResolveAndCollisionsRemainMultipleTargets() {
        php()
        php("OtherRules")
        php("Unregistered")
        config(listOf("Rules", "OtherRules"))
        val source = baseline()
        assertEquals(2, PhpatRuleResolver.resolve(source, "phpat.domainCanOnlyDependOn").size)
        assertTrue(PhpatRuleResolver.resolve(source, "phpat.notARule").isEmpty())
        assertTrue(PhpatRuleResolver.resolve(source, "phpat.privateRule").isEmpty())
        assertTrue(PhpatRuleResolver.resolve(source, "phpat.domainCanOnlyDependOnExtra").isEmpty())
        assertTrue(PhpatRuleResolver.resolve(source, "missingType.return").isEmpty())
    }

    fun testCollectionRuleFallsBackToGeneratorAndTestPrefixIsNormalized() {
        php("Rules", "architectureRules", "array")
        php("PrefixRules", "test_domain_dependencies", attribute = "")
        config(listOf("Rules", "PrefixRules"))
        val identifier = "phpat.architectureRulesClientFinanceDomainServiceDependencies"
        val source = baseline(identifier)
        val target = PhpatRuleResolver.resolve(source, identifier).single()
        assertEquals("architectureRules", target.method.name)
        assertTrue(target.method.collection)
        val hint = BaselineDocumentationTargetProvider().documentationTargets(source, source.text.indexOf(identifier)).single().computeDocumentationHint()!!
        assertTrue(hint.contains(dev.baselinehighlighter.BaselineBundle.message("phpat.generator.hint")))
        assertEquals("test_domain_dependencies", PhpatRuleResolver.resolve(source, "phpat.testDomainDependencies").single().method.name)
    }

    fun testUnsavedConfigurationAndPhpChangesInvalidateCachedTargets() {
        val rule = php()
        val configuration = config()
        val source = baseline()
        val identifier = "phpat.domainCanOnlyDependOn"
        assertEquals(1, PhpatRuleResolver.resolve(source, identifier).size)
        val document = configuration.viewProvider.document!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText(document.text.replace("phpat.test", "other.tag")) }
        assertTrue(PhpatRuleResolver.resolve(source, identifier).isEmpty())
        WriteCommandAction.runWriteCommandAction(project) { document.setText(document.text.replace("other.tag", "phpat.test")) }
        assertEquals(1, PhpatRuleResolver.resolve(source, identifier).size)
        val phpDocument = rule.viewProvider.document!!
        WriteCommandAction.runWriteCommandAction(project) { phpDocument.setText(phpDocument.text.replace("domainCanOnlyDependOn", "renamedRule")) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertTrue(PhpatRuleResolver.resolve(source, identifier).isEmpty())
        assertEquals(1, PhpatRuleResolver.resolve(source, "phpat.renamedRule").size)
        WriteCommandAction.runWriteCommandAction(project) { configuration.virtualFile.delete(this) }
        assertTrue(PhpatRuleResolver.resolve(source, "phpat.renamedRule").isEmpty())
    }

    fun testUnrelatedConfigurationsAndNonIdentifierPositionsDoNotProduceLinks() {
        php()
        myFixture.addFileToProject("unrelated.neon", "services:\n  -\n    class: Tests\\Rules\n    tags: [phpat.test]\n")
        val source = baseline()
        assertTrue(PhpatRuleResolver.resolve(source, "phpat.domainCanOnlyDependOn").isEmpty())
        assertNull(PhpatRuleNavigation.linkAt(source, source.text.indexOf("# comment") + 3))
        assertNull(PhpatRuleNavigation.linkAt(source, source.text.indexOf("Old architecture")))
        assertNull(PhpatRuleNavigation.linkAt(source, source.text.indexOf("../src")))
        config()
        assertEquals(1, PhpatRuleResolver.resolve(source, "phpat.domainCanOnlyDependOn").size)
    }

    fun testInheritedMethodsResolveButAnUntaggedOverrideHidesTheParentRule() {
        val parent = php("ParentRules")
        myFixture.addFileToProject("tests/Rules.php", "<?php namespace Tests; class Rules extends ParentRules {}")
        myFixture.addFileToProject("tests/OverrideRules.php", "<?php namespace Tests; class OverrideRules extends ParentRules { public function domainCanOnlyDependOn(): \\PHPat\\Test\\Builder\\Rule { throw new \\Exception(); } }")
        config(listOf("Rules", "OverrideRules"))
        val source = baseline()
        val targets = PhpatRuleResolver.resolve(source, "phpat.domainCanOnlyDependOn")
        assertEquals(1, targets.size)
        assertEquals("Tests\\Rules", targets.single().className)
        assertEquals(parent.virtualFile, targets.single().element.containingFile.virtualFile)
    }

    fun testUnhintedRequestsSkipReadingAndEditedBaselineInvalidatesTheReference() {
        val untouched = java.lang.reflect.Proxy.newProxyInstance(javaClass.classLoader, arrayOf(com.intellij.psi.PsiPlainTextFile::class.java)) { _, method, _ ->
            error("Unhinted PHPat request accessed file: ${method.name}")
        } as com.intellij.model.psi.PsiExternalReferenceHost
        val hints = com.intellij.model.psi.PsiSymbolReferenceHints.referenceClassHint(PhpatRuleReference::class.java)
        assertTrue(PhpatRuleReferenceProvider().getReferences(untouched, hints).isEmpty())
        php()
        config()
        val source = baseline()
        val offset = source.text.indexOf("phpat.")
        val reference = com.intellij.model.psi.PsiSymbolReferenceService.getService().getExternalReferences(
            source as com.intellij.model.psi.PsiExternalReferenceHost,
            com.intellij.model.psi.PsiSymbolReferenceHints.offsetHint(offset)
        ).filterIsInstance<PhpatRuleReference>().single()
        assertEquals(1, reference.resolveReference().size)
        WriteCommandAction.runWriteCommandAction(project) { myFixture.editor.document.insertString(0, "# shifted\n") }
        assertTrue(reference.resolveReference().isEmpty())
    }

    fun testDetailsPanelFollowsSelectedEntryAndRendersRuleLink() {
        val source = baseline()
        val entry = dev.baselinehighlighter.core.IndexedEntry(source.virtualFile.path, "Example.php", dev.baselinehighlighter.core.BaselineParser.parse(source.text).entries.single())
        val panel = dev.baselinehighlighter.editor.BaselineEntryDetailsPanel(project)
        panel.showEntry(entry)
        assertTrue(panel.ruleLink.isVisible)
        assertFalse(panel.documentationLink.isVisible)
        panel.setSize(720, 150)
        fun layout(component: java.awt.Container) { component.doLayout(); component.components.filterIsInstance<java.awt.Container>().forEach(::layout) }
        layout(panel)
        val image = java.awt.image.BufferedImage(panel.width, panel.height, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try { panel.printAll(graphics) } finally { graphics.dispose() }
        val output = java.io.File("build/reports/ui/phpat-details.png").apply { parentFile.mkdirs() }
        javax.imageio.ImageIO.write(image, "png", output)
        panel.showEntry(entry.copy(entry = entry.entry.copy(identifier = "missingType.return")))
        assertFalse(panel.ruleLink.isVisible)
        assertTrue(panel.documentationLink.isVisible)
        panel.showEntry(null)
        assertFalse(panel.ruleLink.isVisible)
    }

    fun testAbsoluteBaselineIncludeResolvesAndRemovingTheIncludeDropsNavigation() {
        php()
        val source = baseline()
        val configuration = myFixture.addFileToProject("absolute.neon", "includes:\n  - '${source.virtualFile.path}'\nservices:\n  -\n    class: Tests\\Rules\n    tags: [phpat.test]\n")
        assertEquals(1, PhpatRuleResolver.resolve(source, "phpat.domainCanOnlyDependOn").size)
        WriteCommandAction.runWriteCommandAction(project) { configuration.viewProvider.document!!.setText("services:\n  -\n    class: Tests\\Rules\n    tags: [phpat.test]\n") }
        assertTrue(PhpatRuleResolver.resolve(source, "phpat.domainCanOnlyDependOn").isEmpty())
    }

    private fun yamlGenerator(paths: String = "__DIR__ . '/../config/architecture.yaml'"): PsiFile =
        myFixture.addFileToProject("tests/Rules.php", """
            <?php namespace Tests;
            class Rules {
                #[\PHPat\Test\Attributes\TestRule]
                public function architectureRules(): array { return []; }
                protected function getYamlPaths(): array { return [$paths]; }
            }
        """.trimIndent())

    private fun architecture(): PsiFile = myFixture.addFileToProject("config/architecture.yaml", """
        # 😀 offsets are UTF-16
        contexts:
          Sales:
            - Orders
        layer_templates:
          Domain.Service:
            dependencies:
              type: canOnlyDependOn
              targets:
                - '{context}\Domain'
    """.trimIndent())

    fun testArchitectureConditionSupportsCtrlNavigationAndDocumentationAtExactOffset() {
        yamlGenerator()
        val yaml = architecture()
        config()
        val identifier = "phpat.architectureRulesSalesOrdersDomainServiceDependencies"
        val source = baseline(identifier)
        val offset = source.text.indexOf(identifier)
        val target = PhpatRuleResolver.resolve(source, identifier).single()
        assertEquals(yaml.virtualFile, target.element.containingFile.virtualFile)
        assertEquals(yaml.text.indexOf("dependencies:"), target.offset)
        assertNotNull(target.configuration)
        val hover = hoverAt(source, offset + 3)!!
        assertEquals(TextRange(offset, offset + identifier.length), hover.ranges.single())
        val doc = BaselineDocumentationTargetProvider().documentationTargets(source, offset).single()
        assertTrue(doc.computeDocumentationHint()!!.contains(dev.baselinehighlighter.BaselineBundle.message("phpat.configuration.hint")))
        val url = PhpatDocumentationLinkHandler.url(target.label)
        assertNotNull(PhpatDocumentationLinkHandler().resolveLink(doc, url))
        val identifierToken = object : com.intellij.psi.impl.FakePsiElement() {
            override fun getParent() = source
            override fun getTextRange() = TextRange(offset, offset + identifier.length)
        }
        val psiTarget = PhpatRuleDeclarationHandler().getGotoDeclarationTargets(identifierToken, offset, myFixture.editor)!!.single()
        assertEquals(yaml.text.indexOf("dependencies:"), psiTarget.textOffset)
        (psiTarget as com.intellij.pom.Navigatable).navigate(true)
        assertEquals(yaml.virtualFile, FileEditorManager.getInstance(project).selectedFiles.single())
        doc.navigatable!!.navigate(true)
        assertEquals(yaml.text.indexOf("dependencies:"), FileEditorManager.getInstance(project).selectedTextEditor!!.caretModel.offset)
        myFixture.configureFromExistingVirtualFile(source.virtualFile)
        myFixture.editor.caretModel.moveToOffset(offset + 3)
        myFixture.performEditorAction(IdeActions.ACTION_GOTO_DECLARATION)
        PlatformTestUtil.waitWithEventsDispatching("Architecture condition did not open", {
            yaml.virtualFile in FileEditorManager.getInstance(project).selectedFiles
        }, 15)
        assertEquals(yaml.text.indexOf("dependencies:"), FileEditorManager.getInstance(project).selectedTextEditor!!.caretModel.offset)
    }

    fun testUnsavedYamlEditsRemovalAndRecreationInvalidateConditionTargets() {
        val rule = yamlGenerator()
        val yaml = architecture()
        config()
        val identifier = "phpat.architectureRulesSalesOrdersDomainServiceDependencies"
        val source = baseline(identifier)
        val first = PhpatRuleResolver.resolve(source, identifier).single()
        val pointer = first.pointer()
        val document = yaml.viewProvider.document!!
        WriteCommandAction.runWriteCommandAction(project) { document.insertString(0, "# shifted\n") }
        val shifted = PhpatRuleResolver.resolve(source, identifier).single()
        assertEquals(first.offset + 10, shifted.offset)
        assertEquals(shifted.offset, pointer.range!!.startOffset)
        WriteCommandAction.runWriteCommandAction(project) { document.setText(document.text.replace("dependencies:", "renamed:")) }
        assertNull(PhpatRuleResolver.resolve(source, identifier).single().configuration)
        WriteCommandAction.runWriteCommandAction(project) { yaml.virtualFile.delete(this) }
        assertEquals(rule.virtualFile, PhpatRuleResolver.resolve(source, identifier).single().element.containingFile.virtualFile)
        architecture()
        assertNotNull(PhpatRuleResolver.resolve(source, identifier).single().configuration)
    }

    fun testYamlOverridesAndDynamicPathsKeepConservativeGeneratorFallback() {
        yamlGenerator("__DIR__ . '/../config/architecture.yaml', __DIR__ . '/../config/extra.yaml'")
        architecture()
        val extra = myFixture.addFileToProject("config/extra.yaml", "layer_templates:\n  UseCase:\n    onePublicMethod: true\n")
        config()
        val identifier = "phpat.architectureRulesSalesOrdersDomainServiceDependencies"
        val source = baseline(identifier)
        assertNull(PhpatRuleResolver.resolve(source, identifier).single().configuration)
        val override = PhpatRuleResolver.resolve(source, "phpat.architectureRulesSalesOrdersUseCaseOnePublicMethod").single()
        assertEquals(extra.virtualFile, override.element.containingFile.virtualFile)
        WriteCommandAction.runWriteCommandAction(project) { extra.viewProvider.document!!.setText("metadata:\n  root_namespace: Example\n") }
        assertNotNull(PhpatRuleResolver.resolve(source, identifier).single().configuration)
        val rule = yamlGenerator("__DIR__ . '/../config/architecture.yaml', getenv('DYNAMIC')")
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertNull(PhpatRuleResolver.resolve(source, identifier).single().configuration)
        assertEquals(rule.virtualFile, PhpatRuleResolver.resolve(source, identifier).single().element.containingFile.virtualFile)
    }

    fun testInheritedGeneratorUsesOverriddenPathsAndIgnoresUnrelatedYaml() {
        myFixture.addFileToProject("base/ParentRules.php", """
            <?php namespace Tests;
            class ParentRules {
                #[\PHPat\Test\Attributes\TestRule]
                public function architectureRules(): array { return []; }
                protected function getYamlPaths(): array { return [__DIR__ . '/missing.yaml']; }
            }
        """.trimIndent())
        myFixture.addFileToProject("tests/Rules.php", """
            <?php namespace Tests;
            class Rules extends ParentRules {
                protected function getYamlPaths(): array { return [__DIR__ . '/../config/architecture.yaml']; }
            }
        """.trimIndent())
        val yaml = architecture()
        myFixture.addFileToProject("unrelated/architecture.yaml", yaml.text)
        config()
        val identifier = "phpat.architectureRulesSalesOrdersDomainServiceDependencies"
        val source = baseline(identifier)
        assertEquals(yaml.virtualFile, PhpatRuleResolver.resolve(source, identifier).single().element.containingFile.virtualFile)
        val document = yaml.viewProvider.document!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText(document.text.replace("Sales:", "Other:")) }
        assertNull(PhpatRuleResolver.resolve(source, identifier).single().configuration)
    }

    fun testDeletedPhpRuleInvalidatesItsNavigationPointer() {
        val rule = php()
        config()
        val source = baseline()
        val pointer = PhpatRuleResolver.resolve(source, "phpat.domainCanOnlyDependOn").single().pointer()
        WriteCommandAction.runWriteCommandAction(project) { rule.viewProvider.document!!.setText("<?php namespace Tests; class Rules {}") }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertNull(pointer.element)
        assertNull(pointer.range)
    }
}
