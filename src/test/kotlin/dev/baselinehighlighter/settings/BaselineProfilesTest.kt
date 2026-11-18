package dev.baselinehighlighter.settings

import com.intellij.openapi.components.service
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.ui.table.JBTable
import com.intellij.util.xmlb.XmlSerializer
import dev.baselinehighlighter.editor.BaselinePathNavigation
import dev.baselinehighlighter.editor.BaselineNavigation
import dev.baselinehighlighter.index.BaselineIndexService
import dev.baselinehighlighter.query.BaselineQueryService
import javax.swing.JComponent

class BaselineProfilesTest : BasePlatformTestCase() {
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
    private fun baseline(name: String, path: String = "../src/$name.php"): VirtualFile =
        myFixture.addFileToProject("config/$name.neon", """
            parameters:
              ignoreErrors:
                -
                  message: '#^Example$#'
                  identifier: test.example
                  path: $path
        """.trimIndent()).virtualFile

    private fun table(component: JComponent): JBTable {
        fun find(container: java.awt.Container): JBTable? = if (container is JBTable) container
            else container.components.filterIsInstance<java.awt.Container>().firstNotNullOfOrNull(::find)
        return find(component)!!
    }

    private fun awaitIndex(condition: (BaselineIndexService) -> Boolean) {
        val index = project.service<BaselineIndexService>()
        PlatformTestUtil.waitWithEventsDispatching("Profile index did not update", { !index.isUpdating && condition(index) }, 15)
    }

    fun testBehaviorLegacyWorkspaceMigratesWithoutLosingFlagsOrBaseDirectoriesAndRoundTrips() {
        val legacy = BaselineSettings.Data(false, mutableListOf(BaselineSettings.Source(false, "config/old.neon", "custom/base")))
        val xml = XmlSerializer.serialize(legacy)
        xml.children.removeIf { it.getAttributeValue("name") in listOf("profiles", "activeProfileId") }
        val settings = BaselineSettings()
        settings.loadState(XmlSerializer.deserialize(xml, BaselineSettings.Data::class.java))
        val migrated = settings.state
        assertFalse(migrated.enabled)
        assertTrue(migrated.sources.isEmpty())
        assertEquals("default", migrated.activeProfileId)
        assertEquals(legacy.sources, migrated.activeSources())
        legacy.sources.single().baseDirectory = "changed"
        assertEquals("custom/base", migrated.activeSources().single().baseDirectory)
        val restored = BaselineSettings()
        restored.loadState(XmlSerializer.deserialize(XmlSerializer.serialize(migrated), BaselineSettings.Data::class.java))
        assertEquals(migrated, restored.state)
        assertNotSame(migrated.profiles.single().sources, restored.state.profiles.single().sources)
    }

    fun testBehaviorStoredProfilesKeepTheirActiveIdAndRepairMalformedIdsWithoutDroppingLegacySources() {
        val settings = BaselineSettings()
        val source = BaselineSettings.Source(path = "old.neon")
        val state = BaselineSettings.Data(sources = mutableListOf(source), profiles = mutableListOf(
            BaselineSettings.Profile("a", "Main", mutableListOf(BaselineSettings.Source(path = "a.neon"))),
            BaselineSettings.Profile("a", "Copy", mutableListOf(BaselineSettings.Source(path = "b.neon"))),
            BaselineSettings.Profile("", "", mutableListOf(BaselineSettings.Source(path = "c.neon")))
        ), activeProfileId = "missing")
        settings.loadState(state)
        assertEquals("a", settings.state.activeProfileId)
        assertEquals(4, settings.state.profiles.map { it.id }.distinct().size)
        assertTrue(settings.state.profiles.all { it.name.isNotBlank() })
        assertEquals(setOf("a.neon", "b.neon", "c.neon", "old.neon"), settings.state.profiles.flatMap { it.sources }.map { it.path }.toSet())
        val active = settings.state.profiles[1].id
        settings.replace(settings.state.copy(activeProfileId = active))
        val restored = BaselineSettings()
        restored.loadState(XmlSerializer.deserialize(XmlSerializer.serialize(settings.state), BaselineSettings.Data::class.java))
        assertEquals(active, restored.state.activeProfileId)
        assertEquals(settings.state.profiles, restored.state.profiles)
    }

    fun testBehaviorDraftProfilesCopyIndependentlyRenameDeleteAndResetWithoutChangingAppliedState() {
        val original = BaselineSettings.Source(false, "old.neon", "original/base")
        val settings = project.service<BaselineSettings>()
        settings.replace(BaselineSettings.Data(sources = mutableListOf(original)))
        val initial = settings.state.activeProfileId
        val configurable = BaselineSourcesConfigurable(project)
        try {
            val component = configurable.createComponent()
            val copy = configurable.addProfile("CI", true)
            assertEquals(1, table(component).rowCount)
            table(component).setValueAt("copy/base", 0, 2)
            configurable.renameProfile("CI full")
            configurable.selectProfile(initial)
            assertEquals("original/base", table(component).getValueAt(0, 2))
            assertEquals(1, settings.state.profiles.size)
            assertTrue(configurable.isModified)
            configurable.selectProfile(copy)
            configurable.deleteProfile()
            assertEquals(initial, configurable.selectedProfile)
            assertFalse(configurable.isModified)
            configurable.addProfile("Temporary")
            configurable.reset()
            assertEquals(initial, configurable.selectedProfile)
            assertEquals(1, table(component).rowCount)
            assertFalse(configurable.isModified)
            configurable.renameProfile("Renamed")
            configurable.apply()
            assertEquals("Renamed", settings.state.activeProfile()!!.name)
            assertEquals(listOf(original), settings.state.activeSources())
            assertFalse(configurable.isModified)
        } finally { configurable.disposeUIResources() }
    }

    fun testBehaviorOnlyAppliedProfileIsIndexedAndOldQueryIdsExpireDuringSwitching() {
        val a = baseline("A")
        val b = baseline("B")
        val phpA = myFixture.addFileToProject("src/A.php", "<?php class A {}").virtualFile
        val phpB = myFixture.addFileToProject("src/B.php", "<?php class B {}").virtualFile
        val settings = project.service<BaselineSettings>()
        settings.replace(BaselineSettings.Data(profiles = mutableListOf(
            BaselineSettings.Profile("a", "Main", mutableListOf(BaselineSettings.Source(path = a.path))),
            BaselineSettings.Profile("b", "CI", mutableListOf(BaselineSettings.Source(path = b.path)))
        ), activeProfileId = "a"))
        val index = project.service<BaselineIndexService>()
        index.reload()
        awaitIndex { it.entries(phpA.path).size == 1 }
        val query = project.service<BaselineQueryService>()
        val old = query.find(filePath = phpA.path).entries.single().entryId
        val previousEntry = index.entries(phpA.path).single()
        assertTrue(BaselineNavigation.isActive(project, previousEntry))
        val configurable = BaselineSourcesConfigurable(project)
        try {
            configurable.createComponent()
            configurable.selectProfile("b")
            assertEquals("a", settings.state.activeProfileId)
            assertEquals(1, index.entries(phpA.path).size)
            configurable.apply()
            assertTrue(index.isUpdating)
            assertFalse(BaselineNavigation.isActive(project, previousEntry))
            assertTrue(index.entries(phpA.path).isEmpty())
            assertEquals("updating", query.find(filePath = phpA.path).state)
            try { query.selected(old); fail("Old query must expire") } catch (_: IllegalArgumentException) { }
            awaitIndex { it.entries(phpB.path).size == 1 && it.entries(phpA.path).isEmpty() }
            assertEquals(setOf(b.path), index.snapshot.sources.keys)
            configurable.selectProfile("a")
            configurable.apply()
            configurable.selectProfile("b")
            configurable.apply()
            configurable.selectProfile("a")
            configurable.apply()
            awaitIndex { it.entries(phpA.path).size == 1 && it.entries(phpB.path).isEmpty() }
            assertEquals("a", settings.state.activeProfileId)
            settings.loadState(settings.state.copy(activeProfileId = "b"))
            awaitIndex { it.entries(phpB.path).size == 1 && it.entries(phpA.path).isEmpty() }
        } finally { configurable.disposeUIResources() }
    }

    fun testBehaviorSameBaselineCanUseDifferentBaseDirectoriesInDifferentProfiles() {
        val source = baseline("common", "Target.php")
        val targetA = myFixture.addFileToProject("one/Target.php", "<?php class One {}").virtualFile
        val targetB = myFixture.addFileToProject("two/Target.php", "<?php class Two {}").virtualFile
        val settings = project.service<BaselineSettings>()
        settings.replace(BaselineSettings.Data(profiles = mutableListOf(
            BaselineSettings.Profile("a", "One", mutableListOf(BaselineSettings.Source(path = source.path, baseDirectory = targetA.parent.path))),
            BaselineSettings.Profile("b", "Two", mutableListOf(BaselineSettings.Source(path = source.path, baseDirectory = targetB.parent.path)))
        ), activeProfileId = "a"))
        project.service<BaselineIndexService>().reload()
        awaitIndex { it.entries(targetA.path).size == 1 }
        val previous = project.service<BaselineIndexService>().entries(targetA.path).single()
        val file = com.intellij.psi.PsiManager.getInstance(project).findFile(source)!!
        assertEquals(targetA, BaselinePathNavigation.resolveFile(file, "Target.php"))
        val configurable = BaselineSourcesConfigurable(project)
        try {
            configurable.createComponent()
            configurable.selectProfile("b")
            configurable.apply()
            awaitIndex { it.entries(targetB.path).size == 1 && it.entries(targetA.path).isEmpty() }
            assertFalse(BaselineNavigation.isActive(project, previous))
            assertEquals(targetB, BaselinePathNavigation.resolveFile(file, "Target.php"))
            assertEquals(2, settings.state.profiles.size)
        } finally { configurable.disposeUIResources() }
    }

    fun testBehaviorImportCannotCrossProfileBoundariesAndResetDiscardsDraftImports() {
        val source = baseline("A")
        val configurable = BaselineSourcesConfigurable(project)
        try {
            val component = configurable.createComponent()
            val original = configurable.selectedProfile
            val pending = configurable.importConfiguration(source)!!
            val second = configurable.addProfile("CI")
            PlatformTestUtil.waitWithEventsDispatching("Cancelled import did not stop", { pending.isDone }, 15)
            com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents()
            assertEquals(0, table(component).rowCount)
            configurable.selectProfile(original)
            assertEquals(0, table(component).rowCount)
            configurable.selectProfile(second)
            val complete = configurable.importConfiguration(source)!!
            PlatformTestUtil.waitWithEventsDispatching("Profile import did not finish", { complete.isDone && !configurable.isImporting }, 15)
            assertEquals(1, table(component).rowCount)
            configurable.selectProfile(original)
            assertEquals(0, table(component).rowCount)
            configurable.selectProfile(second)
            assertEquals(1, table(component).rowCount)
            configurable.reset()
            assertEquals(original, configurable.selectedProfile)
            assertEquals(0, table(component).rowCount)
            assertFalse(configurable.isModified)
        } finally { configurable.disposeUIResources() }
    }

    fun testBehaviorProfileValidationRejectsAmbiguousNamesAndReportsInvalidInactiveSources() {
        val configurable = BaselineSourcesConfigurable(project)
        try {
            val component = configurable.createComponent()
            val original = configurable.selectedProfile
            fun invalid(action: () -> Unit) { try { action(); fail("Invalid profile should be rejected") } catch (_: ConfigurationException) { } }
            invalid { configurable.deleteProfile() }
            invalid { configurable.addProfile(" ") }
            val ci = configurable.addProfile("CI")
            invalid { configurable.addProfile(" ci ") }
            configurable.selectProfile(original)
            invalid { configurable.renameProfile("CI") }
            val complete = configurable.importConfiguration(baseline("A"))!!
            PlatformTestUtil.waitWithEventsDispatching("Import did not finish", { complete.isDone && !configurable.isImporting }, 15)
            table(component).setValueAt("", 0, 1)
            configurable.selectProfile(ci)
            invalid { configurable.apply() }
        } finally { configurable.disposeUIResources() }
    }
}
