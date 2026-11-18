package dev.baselinehighlighter.settings

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.ui.table.JBTable
import dev.baselinehighlighter.index.BaselineIndexService
import java.util.concurrent.Future
import javax.swing.JComponent

class BaselineSourceImportTest : BasePlatformTestCase() {
    override fun createTempDirTestFixture() = TempDirTestFixtureImpl()
    override fun setUp() {
        super.setUp()
        project.service<BaselineSettings>().replace(BaselineSettings.Data())
        project.service<BaselineIndexService>().reload()
    }
    override fun tearDown() {
        try {
            project.service<BaselineSettings>().replace(BaselineSettings.Data())
            project.service<BaselineIndexService>().reload()
        } finally { super.tearDown() }
    }
    private val empty = "parameters:\n  ignoreErrors: []\n"
    private fun baseline(): VirtualFile = myFixture.addFileToProject("config/custom.neon", """
        parameters:
          ignoreErrors:
            -
              message: '#^Example$#'
              identifier: example.rule
              path: ../src/Service.php
    """.trimIndent()).virtualFile

    private fun table(component: JComponent): JBTable {
        fun find(container: java.awt.Container): JBTable? {
            if (container is JBTable) return container
            return container.components.filterIsInstance<java.awt.Container>().firstNotNullOfOrNull(::find)
        }
        return find(component)!!
    }

    private fun awaitImport(configurable: BaselineSourcesConfigurable, future: Future<*>?) {
        assertNotNull(future)
        PlatformTestUtil.waitWithEventsDispatching("Baseline import did not finish", { future!!.isDone && !configurable.isImporting }, 15)
        future!!.get()
        com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents()
    }

    fun testBehaviorImportIsAnEditablePreviewAndApplyLoadsPathsRelativeToEachBaseline() {
        val target = myFixture.addFileToProject("src/Service.php", "<?php class Service {}").virtualFile
        val source = baseline()
        val config = myFixture.addFileToProject("phpstan.neon.dist", "includes: [config/custom.neon]\n").virtualFile
        val settings = project.service<BaselineSettings>()
        val configurable = BaselineSourcesConfigurable(project)
        try {
            val component = configurable.createComponent()
            val descriptor = configurable.importDescriptor()
            assertTrue(descriptor.isSelectable(config))
            assertTrue(descriptor.isSelectable(source))
            assertFalse(descriptor.isSelectable(target))
            assertFalse(descriptor.isSelectable(source.parent))
            assertTrue(descriptor.isShowHiddenFiles)
            awaitImport(configurable, configurable.importConfiguration(config))
            assertTrue(settings.state.activeSources().isEmpty())
            assertTrue(configurable.isModified)
            val table = table(component)
            assertEquals(1, table.rowCount)
            assertEquals(source.path, project.service<BaselineIndexService>().normalize(table.getValueAt(0, 1).toString()))
            assertEquals("", table.getValueAt(0, 2))
            assertTrue(configurable.importSummary.contains(dev.baselinehighlighter.BaselineBundle.message("settings.import.apply")))
            configurable.apply()
            val index = project.service<BaselineIndexService>()
            PlatformTestUtil.waitWithEventsDispatching("Imported baseline was not indexed", { !index.isUpdating && index.entries(target.path).size == 1 }, 15)
            com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents()
            assertEquals(source.path, index.snapshot.sources.keys.single())
            assertFalse(configurable.isModified)
            render(component, "settings-import.png")
            render(component, "settings-import-compact.png", 900, 500)
            val viewport = javax.swing.SwingUtilities.getAncestorOfClass(javax.swing.JViewport::class.java, table) as javax.swing.JViewport
            assertTrue("Imported sources must remain visible above the import report", viewport.height >= table.rowHeight * 2)
        } finally { configurable.disposeUIResources() }
    }

    fun testBehaviorImportPreservesExistingDisabledSourcesAndBaseDirectoriesWithoutDuplicates() {
        val source = baseline()
        val emptySource = myFixture.addFileToProject("config/empty.neon", empty).virtualFile
        val config = myFixture.addFileToProject("phpstan.neon", "includes: [config/custom.neon, config/empty.neon, ./config/../config/custom.neon]\n").virtualFile
        val original = BaselineSettings.Source(false, source.path, "custom/base")
        project.service<BaselineSettings>().replace(BaselineSettings.Data(false, mutableListOf(original.copy())))
        val configurable = BaselineSourcesConfigurable(project)
        try {
            val component = configurable.createComponent()
            awaitImport(configurable, configurable.importConfiguration(config))
            awaitImport(configurable, configurable.importConfiguration(config))
            val table = table(component)
            assertEquals(2, table.rowCount)
            assertEquals(false, table.getValueAt(0, 0))
            assertEquals("custom/base", table.getValueAt(0, 2))
            assertEquals(emptySource.path, project.service<BaselineIndexService>().normalize(table.getValueAt(1, 1).toString()))
            configurable.apply()
            val state = project.service<BaselineSettings>().state
            assertFalse(state.enabled)
            assertEquals(original, state.activeSources().first())
            assertEquals(2, state.activeSources().size)
        } finally { configurable.disposeUIResources() }
    }

    fun testBehaviorUnsavedConfigAndBaselineChangesAreReadAndProblemsRemainVisible() {
        val source = baseline()
        val config = myFixture.addFileToProject("phpstan.neon", "includes: []\n").virtualFile
        WriteCommandAction.runWriteCommandAction(project) {
            FileDocumentManager.getInstance().getDocument(config)!!.setText("includes:\n  - config/custom.neon\n  - missing.neon\n  - generate.php\n")
            FileDocumentManager.getInstance().getDocument(source)!!.setText("not a baseline")
        }
        val configurable = BaselineSourcesConfigurable(project)
        try {
            val component = configurable.createComponent()
            awaitImport(configurable, configurable.importConfiguration(config))
            assertEquals(0, table(component).rowCount)
            assertTrue(configurable.importSummary.contains("missing.neon"))
            assertTrue(configurable.importSummary.contains("generate.php"))
            assertFalse(configurable.isModified)
            WriteCommandAction.runWriteCommandAction(project) { FileDocumentManager.getInstance().getDocument(source)!!.setText(empty) }
            awaitImport(configurable, configurable.importConfiguration(config))
            assertEquals(1, table(component).rowCount)
            configurable.reset()
            assertEquals(0, table(component).rowCount)
            assertEquals("", configurable.importSummary)
            assertFalse(configurable.isModified)
        } finally { configurable.disposeUIResources() }
    }

    fun testBehaviorResetAndDisposalDiscardInFlightImportsAndReopeningKeepsOneImportAction() {
        val source = baseline()
        val configurable = BaselineSourcesConfigurable(project)
        try {
            val component = configurable.createComponent()
            fun buttons(container: java.awt.Container): List<javax.swing.JButton> =
                container.components.flatMap { child ->
                    if (child is javax.swing.JButton) listOf(child)
                    else if (child is java.awt.Container) buttons(child) else emptyList()
                }
            val cancelled = configurable.importConfiguration(source)
            buttons(component).single { it.text == dev.baselinehighlighter.BaselineBundle.message("settings.import.cancel") }.doClick(0)
            awaitImport(configurable, cancelled)
            assertEquals(0, table(component).rowCount)
            assertEquals(dev.baselinehighlighter.BaselineBundle.message("settings.import.cancelled"), configurable.importSummary)
            val pending = configurable.importConfiguration(source)
            configurable.reset()
            awaitImport(configurable, pending)
            assertEquals(0, table(component).rowCount)
            val closing = configurable.importConfiguration(source)
            configurable.disposeUIResources()
            awaitImport(configurable, closing)
            val reopened = configurable.createComponent()
            assertEquals(1, buttons(reopened).single { it.text == dev.baselinehighlighter.BaselineBundle.message("settings.import") }.actionListeners.size)
            awaitImport(configurable, configurable.importConfiguration(source))
            assertEquals(1, table(reopened).rowCount)
            assertTrue(project.service<BaselineSettings>().state.activeSources().isEmpty())
        } finally { configurable.disposeUIResources() }
    }

    private fun render(component: JComponent, name: String, width: Int = 1000, height: Int = 700) {
        component.setSize(width, height)
        fun layout(container: java.awt.Container) {
            container.doLayout()
            container.components.filterIsInstance<java.awt.Container>().forEach(::layout)
        }
        layout(component)
        val image = java.awt.image.BufferedImage(component.width, component.height, java.awt.image.BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try { component.printAll(graphics) } finally { graphics.dispose() }
        val output = java.io.File("build/reports/ui/$name").apply { parentFile.mkdirs() }
        javax.imageio.ImageIO.write(image, "png", output)
    }
}
