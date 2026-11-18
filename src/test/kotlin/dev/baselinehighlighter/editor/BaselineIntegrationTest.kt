package dev.baselinehighlighter.editor

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.baselinehighlighter.php.PhpDeclarationProvider
import dev.baselinehighlighter.core.BaselineMatcher
import dev.baselinehighlighter.index.BaselineIndexService
import dev.baselinehighlighter.settings.BaselineSettings

class BaselineIntegrationTest : BasePlatformTestCase() {
    override fun createTempDirTestFixture(): com.intellij.testFramework.fixtures.TempDirTestFixture =
        com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl()
    private fun baseline(method: String = "save"): String = """
        parameters:
          ignoreErrors:
            -
              message: '#^Method App\\Service\:\:$method\(\) has no return type specified\.$#'
              identifier: missingType.return
              count: 1
              path: ../src/Service.php
    """.trimIndent()

    private fun configure(): Pair<VirtualFile, VirtualFile> {
        val php = myFixture.addFileToProject("src/Service.php", """
            <?php
            namespace App;
            class Service {
                public function save() {}
                public function other() {}
            }
        """.trimIndent()).virtualFile
        val neon = myFixture.addFileToProject("config/custom-architecture.neon", baseline()).virtualFile
        project.service<BaselineSettings>().replace(BaselineSettings.Data(sources = mutableListOf(BaselineSettings.Source(path = neon.path))))
        project.service<BaselineIndexService>().start()
        project.service<BaselineIndexService>().reload()
        awaitIndex { it.entries(php.path).size == 1 }
        return php to neon
    }

    private fun awaitIndex(condition: (BaselineIndexService) -> Boolean) {
        val service = project.service<BaselineIndexService>()
        PlatformTestUtil.waitWithEventsDispatching("Baseline index did not update", { condition(service) }, 15)
    }

    private fun methodIsBound(file: com.intellij.psi.PsiFile, name: String): Boolean {
        val method = PhpDeclarationProvider.forFile(file)!!.declarations(file).flatMap { it.methods }.firstOrNull { it.name == name } ?: return false
        return PhpBaselineMatcher.bindings(file)[PsiTreeUtil.getDeepestFirst(method.identifier).textOffset].orEmpty().isNotEmpty()
    }

    fun testClassAndMethodMarkersWithRelativePathAndArbitrarySourceName() {
        val (php, _) = configure()
        myFixture.configureFromExistingVirtualFile(php)
        val file = myFixture.file
        val type = PhpDeclarationProvider.forFile(file)!!.declarations(file).single()
        val provider = BaselineLineMarkerProvider()
        assertNotNull(provider.getLineMarkerInfo(PsiTreeUtil.getDeepestFirst(type.identifier)))
        val save = type.methods.single { it.name == "save" }
        assertNotNull(provider.getLineMarkerInfo(PsiTreeUtil.getDeepestFirst(save.identifier)))
        val other = type.methods.single { it.name == "other" }
        assertNull(provider.getLineMarkerInfo(PsiTreeUtil.getDeepestFirst(other.identifier)))
        assertNull(provider.getLineMarkerInfo(type.identifier.parent))
    }

    fun testUnsavedBaselineEditInvalidatesBindingsAndInvalidSourceRemovesEntries() {
        val (php, neon) = configure()
        myFixture.configureFromExistingVirtualFile(php)
        val file = myFixture.file
        assertTrue(methodIsBound(file, "save"))
        val document = FileDocumentManager.getInstance().getDocument(neon)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText(baseline("other")) }
        awaitIndex { it.entries(php.path).singleOrNull()?.owner?.methodName == "other" }
        assertTrue(methodIsBound(file, "other"))
        WriteCommandAction.runWriteCommandAction(project) { document.setText("invalid baseline") }
        awaitIndex { it.entries(php.path).isEmpty() && it.snapshot.sources.values.single().issues.isNotEmpty() }
        assertTrue(PhpBaselineMatcher.bindings(file).isEmpty())
    }

    fun testUnsavedPhpRenameDropsMethodMarkerWithoutReindexingBaseline() {
        val (php, _) = configure()
        myFixture.configureFromExistingVirtualFile(php)
        val file = myFixture.file
        val index = project.service<BaselineIndexService>()
        val version = index.modificationCount
        assertTrue(methodIsBound(file, "save"))
        val document = myFixture.editor.document
        WriteCommandAction.runWriteCommandAction(project) { document.setText(document.text.replace("function save", "function renamed")) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertFalse(methodIsBound(file, "renamed"))
        assertEquals(1, PhpBaselineMatcher.bindings(file).size)
        assertEquals(version, index.modificationCount)
    }

    fun testMultipleSourcesPreserveEntriesAndDisablingClearsMarkers() {
        val (php, _) = configure()
        val second = myFixture.addFileToProject("config/second.neon", baseline()).virtualFile
        val settings = project.service<BaselineSettings>()
        settings.replace(settings.state.copy(profiles = settings.state.profiles.map { profile ->
            profile.deepCopy().also { it.sources += BaselineSettings.Source(path = second.path) }
        }.toMutableList()))
        project.service<BaselineIndexService>().reload()
        awaitIndex { it.entries(php.path).size == 2 }
        settings.replace(settings.state.copy(enabled = false))
        project.service<BaselineIndexService>().reload()
        awaitIndex { it.snapshot.sources.isEmpty() && it.entries(php.path).isEmpty() }
    }

    fun testDeletedSourceRemovesEntries() {
        val (php, neon) = configure()
        WriteAction.run<RuntimeException> { neon.delete(this) }
        awaitIndex { it.entries(php.path).isEmpty() && it.snapshot.sources.values.single().unavailable }
    }

    fun testNavigationRechecksOffsetAfterUnsavedEdit() {
        val (php, neon) = configure()
        val selected = project.service<BaselineIndexService>().entries(php.path).single()
        val document = FileDocumentManager.getInstance().getDocument(neon)!!
        WriteCommandAction.runWriteCommandAction(project) { document.insertString(0, "# added comment\n") }
        BaselineNavigation.navigate(project, selected)
        PlatformTestUtil.waitWithEventsDispatching("Navigation did not select the message", {
            val editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).selectedTextEditor
            editor?.document === document && editor.selectionModel.selectedText?.startsWith("'#^Method") == true
        }, 15)
        val editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).selectedTextEditor!!
        assertTrue(editor.selectionModel.selectionStart > selected.entry.range.start)
    }

    fun testInterfacesTraitsEnumsAndSeveralNamespaces() {
        val file = myFixture.configureByText("Types.php", "<?php namespace One { interface I { public function run(); } trait T { public function work() {} } } namespace Two { enum E { case A; public function label() {} } }")
        val types = PhpDeclarationProvider.forFile(file)!!.declarations(file)
        assertEquals(setOf("one\\i", "one\\t", "two\\e"), types.map { BaselineMatcher.key(it.fqn) }.toSet())
        assertEquals(setOf("run", "work", "label"), types.flatMap { it.methods }.map { it.name }.toSet())
    }

    fun testRegisteredProviderProducesEditorGutters() {
        val (php, _) = configure()
        myFixture.configureFromExistingVirtualFile(php)
        val markers = myFixture.findAllGutters().filter { it.tooltipText?.contains("PHPStan baseline") == true }
        assertEquals(2, markers.size)
    }

    fun testBehaviorUnusedPublicAndIncompatibleReturnMethodsProduceOnlyOwnerGutters() {
        val php = myFixture.addFileToProject("src/Service.php", """
            <?php
            namespace App;
            class ParentService { public function other() {} }
            class Service extends ParentService {
                public function save() {}
                public function other() {}
            }
        """.trimIndent()).virtualFile
        val neon = myFixture.addFileToProject("config/baseline.neon", """
            parameters:
              ignoreErrors:
                -
                  message: '#^Public method "App\\Service\:\:save\(\)" is never used$#'
                  identifier: public.method.unused
                  path: ../src/Service.php
                -
                  message: '#^Return type \(void\) of method App\\Service\:\:other\(\) should be compatible with return type \(int\|null\) of method App\\ParentService\:\:other\(\)$#'
                  identifier: method.childReturnType
                  path: ../src/Service.php
        """.trimIndent()).virtualFile
        project.service<BaselineSettings>().replace(BaselineSettings.Data(sources = mutableListOf(BaselineSettings.Source(path = neon.path))))
        project.service<BaselineIndexService>().reload()
        awaitIndex { !it.isUpdating && it.entries(php.path).size == 2 }
        myFixture.configureFromExistingVirtualFile(php)
        val file = myFixture.file
        val types = PhpDeclarationProvider.forFile(file)!!.declarations(file)
        val bindings = PhpBaselineMatcher.bindings(file)
        val service = types.single { BaselineMatcher.key(it.fqn) == "app\\service" }
        val classEntries = bindings[PsiTreeUtil.getDeepestFirst(service.identifier).textOffset].orEmpty()
        assertEquals(2, classEntries.size)
        for (method in service.methods) {
            val entries = bindings[PsiTreeUtil.getDeepestFirst(method.identifier).textOffset].orEmpty()
            assertEquals(method.name, 1, entries.size)
            val entry = entries.single()
            assertEquals(method.name, entry.indexed.owner!!.methodName)
            assertEquals(dev.baselinehighlighter.core.BindingNote.NONE, entry.note)
        }
        val parent = types.single { BaselineMatcher.key(it.fqn) == "app\\parentservice" }
        assertNull(bindings[PsiTreeUtil.getDeepestFirst(parent.identifier).textOffset])
        assertNull(bindings[PsiTreeUtil.getDeepestFirst(parent.methods.single().identifier).textOffset])
        val markers = myFixture.findAllGutters().filter { it.tooltipText?.contains("PHPStan baseline") == true }
        assertEquals(3, markers.size)
    }

    fun testBehaviorArchitectureRequirementsProduceOnlyTheOwnerClassGutter() {
        val php = myFixture.addFileToProject("src/Service.php", "<?php namespace App; interface Contract {} class BaseService {} class Service { public function save() {} }").virtualFile
        val requirements = listOf(
            "not exist", "have only one public method", "be named matching the regex /.*Service$/",
            "implement App\\Contract", "extend App\\BaseService",
        )
        val text = "parameters:\n  ignoreErrors:\n" + requirements.joinToString("") { requirement ->
            val message = "rule: App\\Service should $requirement"
            val pattern = "#^" + message.map { if (it.isLetterOrDigit() || it in " _-:") "$it" else "\\$it" }.joinToString("") + "$#"
            "    -\n      message: '$pattern'\n      identifier: phpat.rule\n      path: ../src/Service.php\n"
        }
        val neon = myFixture.addFileToProject("config/baseline.neon", text).virtualFile
        project.service<BaselineSettings>().replace(BaselineSettings.Data(sources = mutableListOf(BaselineSettings.Source(path = neon.path))))
        project.service<BaselineIndexService>().reload()
        awaitIndex { !it.isUpdating && it.entries(php.path).size == 5 }
        myFixture.configureFromExistingVirtualFile(php)
        val bindings = PhpBaselineMatcher.bindings(myFixture.file)
        val owner = PhpDeclarationProvider.forFile(myFixture.file)!!.declarations(myFixture.file).single { BaselineMatcher.key(it.fqn) == "app\\service" }
        val offset = PsiTreeUtil.getDeepestFirst(owner.identifier).textOffset
        assertEquals(setOf(offset), bindings.keys)
        val entries = bindings.getValue(offset)
        assertEquals(5, entries.size)
        assertTrue(entries.all { it.note == dev.baselinehighlighter.core.BindingNote.NONE && it.indexed.owner!!.methodName == null })
        assertEquals(1, myFixture.findAllGutters().count { it.tooltipText?.contains("PHPStan baseline") == true })
    }

    fun testBehaviorPropertyAccessUsesFileFallbackAndNeverTheReferencedClass() {
        val php = myFixture.addFileToProject("src/Service.php", "<?php namespace App; class Service { public function save() {} public \$own; }").virtualFile
        val neon = myFixture.addFileToProject("config/baseline.neon", """
            parameters:
              ignoreErrors:
                -
                  message: '#^Property App\\Dependency\:\:\${'$'}value \(string\) in isset\(\) is not nullable\.$#'
                  identifier: isset.property
                  path: ../src/Service.php
                -
                  message: '#^Property App\\Service\:\:\${'$'}own has no type specified\.$#'
                  identifier: missingType.property
                  path: ../src/Service.php
        """.trimIndent()).virtualFile
        project.service<BaselineSettings>().replace(BaselineSettings.Data(sources = mutableListOf(BaselineSettings.Source(path = neon.path))))
        project.service<BaselineIndexService>().reload()
        awaitIndex { !it.isUpdating && it.entries(php.path).size == 2 }
        myFixture.configureFromExistingVirtualFile(php)
        val initial = PhpBaselineMatcher.bindings(myFixture.file)
        assertEquals(1, initial.size)
        assertEquals(setOf(dev.baselinehighlighter.core.BindingNote.NONE, dev.baselinehighlighter.core.BindingNote.FILE_ONLY), initial.values.single().map { it.note }.toSet())
        val markers = myFixture.findAllGutters().filter { it.tooltipText?.contains("PHPStan baseline") == true }
        assertEquals(1, markers.size)
        assertTrue(markers.single().tooltipText!!.contains(dev.baselinehighlighter.BaselineBundle.message("binding.file_only")))
        WriteCommandAction.runWriteCommandAction(project) { myFixture.editor.document.insertString(myFixture.editor.document.textLength, " class Dependency { public \$value; }") }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val bindings = PhpBaselineMatcher.bindings(myFixture.file)
        assertEquals(1, bindings.size)
        val remaining = bindings.values.single().single()
        assertEquals("App\\Service", remaining.indexed.owner!!.className)
        assertEquals(dev.baselinehighlighter.core.BindingNote.NONE, remaining.note)
    }

    fun testBehaviorClassPhpDocAndGenericsProduceOnlyTheDeclaringClassGutter() {
        val php = myFixture.addFileToProject("src/Service.php", "<?php namespace App; interface Contract {} class BaseService {} class Service { public function findBy() {} }").virtualFile
        val messages = listOf(
            "Class App\\Service implements generic interface App\\Contract but does not specify its types: T",
            "Class App\\Service extends generic class App\\BaseService but does not specify its types: T",
            "Class App\\Service has PHPDoc tag @method for method findBy() parameter #1 \$criteria with no value type specified in iterable type array.",
            "Class App\\BaseService constructor invoked with 4 parameters, 3 required.",
        )
        val text = "parameters:\n  ignoreErrors:\n" + messages.joinToString("") { message ->
            val pattern = "#^" + message.map { if (it.isLetterOrDigit() || it in " _-:") "$it" else "\\$it" }.joinToString("") + "$#"
            "    -\n      message: '$pattern'\n      path: ../src/Service.php\n"
        }
        val neon = myFixture.addFileToProject("config/baseline.neon", text).virtualFile
        project.service<BaselineSettings>().replace(BaselineSettings.Data(sources = mutableListOf(BaselineSettings.Source(path = neon.path))))
        project.service<BaselineIndexService>().reload()
        awaitIndex { !it.isUpdating && it.entries(php.path).size == 4 }
        myFixture.configureFromExistingVirtualFile(php)
        val owner = PhpDeclarationProvider.forFile(myFixture.file)!!.declarations(myFixture.file).single { BaselineMatcher.key(it.fqn) == "app\\service" }
        val bindings = PhpBaselineMatcher.bindings(myFixture.file)
        val offset = PsiTreeUtil.getDeepestFirst(owner.identifier).textOffset
        assertEquals(setOf(offset), bindings.keys)
        val entries = bindings.getValue(offset)
        assertEquals(3, entries.size)
        assertTrue(entries.all { it.note == dev.baselinehighlighter.core.BindingNote.NONE && it.indexed.owner!!.methodName == null })
        assertEquals(1, myFixture.findAllGutters().count { it.tooltipText?.contains("PHPStan baseline") == true })
    }

    fun testBehaviorStartAnchoredLiteralCreatesOnlyTheOwnerGutter() {
        val php = myFixture.addFileToProject("src/Service.php", "<?php namespace App; class Service { public function save() {} } class Dependency {} class Other {}").virtualFile
        val neon = myFixture.addFileToProject("config/baseline.neon", """
            parameters:
              ignoreErrors:
                -
                  message: '#^rule\: App\\Service should not depend on App\\Dependency#'
                  identifier: phpat.rule
                  path: ../src/Service.php
                -
                  message: '#^rule\: App\\Other should not depend on App\\Dependency.*#'
                  identifier: phpat.rule
                  path: ../src/Service.php
        """.trimIndent()).virtualFile
        project.service<BaselineSettings>().replace(BaselineSettings.Data(sources = mutableListOf(BaselineSettings.Source(path = neon.path))))
        project.service<BaselineIndexService>().reload()
        awaitIndex { !it.isUpdating && it.entries(php.path).size == 2 }
        myFixture.configureFromExistingVirtualFile(php)
        val owner = PhpDeclarationProvider.forFile(myFixture.file)!!.declarations(myFixture.file).single { BaselineMatcher.key(it.fqn) == "app\\service" }
        val bindings = PhpBaselineMatcher.bindings(myFixture.file)
        val offset = PsiTreeUtil.getDeepestFirst(owner.identifier).textOffset
        assertEquals(setOf(offset), bindings.keys)
        val entry = bindings.getValue(offset).single()
        assertEquals(dev.baselinehighlighter.core.BindingNote.NONE, entry.note)
        assertEquals("App\\Service", entry.indexed.owner!!.className)
        assertEquals(1, myFixture.findAllGutters().count { it.tooltipText?.contains("PHPStan baseline") == true })
    }

    fun testSettingsPanelRendersAndCanBeReopened() {
        configure()
        val configurable = dev.baselinehighlighter.settings.BaselineSourcesConfigurable(project)
        try {
            val component = configurable.createComponent()
            component.setSize(900, 550)
            fun layoutTree(container: java.awt.Container) {
                container.doLayout()
                container.components.filterIsInstance<java.awt.Container>().forEach(::layoutTree)
            }
            layoutTree(component)
            val image = java.awt.image.BufferedImage(900, 550, java.awt.image.BufferedImage.TYPE_INT_RGB)
            image.createGraphics().let { graphics -> try { component.printAll(graphics) } finally { graphics.dispose() } }
            val target = java.io.File("build/reports/ui/settings.png")
            target.parentFile.mkdirs()
            javax.imageio.ImageIO.write(image, "png", target)
            assertFalse(configurable.isModified)
        } finally { configurable.disposeUIResources() }
        try { assertNotNull(configurable.createComponent()) } finally { configurable.disposeUIResources() }
    }

    fun testOnlySelectedPhpProviderIsActive() {
        val file = myFixture.configureByText("Backend.php", "<?php class Backend {}")
        val backend = System.getProperty("baseline.phpBackend")
        val provider = PhpDeclarationProvider.forFile(file)!!
        assertEquals(1, PhpDeclarationProvider.EP.extensionList.size)
        val plugins = mapOf("jetbrains" to "com.jetbrains.php", "openphp" to "ru.openide.openphp", "raketa" to "ru.raketa.php")
        assertTrue(provider.javaClass.name.startsWith("dev.baselinehighlighter.php.$backend."))
        for ((name, id) in plugins) {
            val plugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(com.intellij.openapi.extensions.PluginId.getId(id))
            assertEquals(id, name == backend, plugin?.isEnabled == true)
        }
    }

    fun testBehaviorProfileSwitchInvalidatesCachedBindingsBeforePublishingTheNewIndex() {
        val (php, first) = configure()
        val second = myFixture.addFileToProject("config/ci.neon", baseline("other")).virtualFile
        val settings = project.service<BaselineSettings>()
        settings.replace(BaselineSettings.Data(profiles = mutableListOf(
            BaselineSettings.Profile("main", "Main", mutableListOf(BaselineSettings.Source(path = first.path))),
            BaselineSettings.Profile("ci", "CI", mutableListOf(BaselineSettings.Source(path = second.path)))
        ), activeProfileId = "main"))
        val index = project.service<BaselineIndexService>()
        index.reload()
        awaitIndex { !it.isUpdating && it.entries(php.path).size == 1 }
        myFixture.configureFromExistingVirtualFile(php)
        assertTrue(methodIsBound(myFixture.file, "save"))
        assertFalse(methodIsBound(myFixture.file, "other"))
        settings.replace(settings.state.copy(activeProfileId = "ci"))
        assertTrue(index.isUpdating)
        assertTrue(PhpBaselineMatcher.bindings(myFixture.file).isEmpty())
        index.reload()
        awaitIndex { !it.isUpdating && it.entries(php.path).singleOrNull()?.owner?.methodName == "other" }
        assertFalse(methodIsBound(myFixture.file, "save"))
        assertTrue(methodIsBound(myFixture.file, "other"))
    }
}
