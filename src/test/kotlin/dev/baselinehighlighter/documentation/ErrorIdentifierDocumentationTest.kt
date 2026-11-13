package dev.baselinehighlighter.documentation

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.baselinehighlighter.core.*
import dev.baselinehighlighter.editor.BaselineEntryDetailsPanel
import dev.baselinehighlighter.editor.BaselineNavigation
import dev.baselinehighlighter.editor.BaselinePresentation
import java.awt.Container
import java.awt.image.BufferedImage
import java.io.File
import java.util.Properties
import javax.imageio.ImageIO

class ErrorIdentifierDocumentationTest : BasePlatformTestCase() {
    private fun entry(identifier: String?) = IndexedEntry("/config/baseline.neon", "/src/A.php", BaselineEntry("#^Message$#", "../src/A.php", identifier, 1, SourceRange(0, 10), 3))

    fun testKnownIdentifiersHaveSafeOfficialLinksAndOptionalExplanations() {
        val info = ErrorIdentifierDocumentation.lookup("missingType.iterableValue")
        assertTrue(info.knownIdentifier)
        assertEquals("missingType.*", info.errorGroup)
        assertEquals("https://phpstan.org/error-identifiers/missingType.iterableValue", info.documentationUrl)
        assertTrue(info.explanation!!.contains("array<User>"))
        val withoutExplanation = ErrorIdentifierDocumentation.lookup("property.notFound")
        assertTrue(withoutExplanation.knownIdentifier)
        assertNotNull(withoutExplanation.documentationUrl)
        assertNull(withoutExplanation.explanation)
    }

    fun testUnknownMissingAndInvalidIdentifiersNeverProduceAssumedLinks() {
        for (identifier in listOf(null, "phpat.projectRule", "missingType.futureUnknown", "missingType.IterableValue", "../argument.type", "missingType..return", "<html><img src=x>", "https://example.com")) {
            val info = ErrorIdentifierDocumentation.lookup(identifier)
            assertFalse(info.knownIdentifier)
            assertNull(info.documentationUrl)
            assertNull(info.explanation)
        }
        assertEquals("phpat.*", ErrorIdentifierDocumentation.lookup("phpat.projectRule").errorGroup)
        assertNull(ErrorIdentifierDocumentation.lookup(null).errorGroup)
    }

    fun testDetailsFollowSelectionAndOpenOnlyKnownDocumentationOnClick() {
        val opened = mutableListOf<String>()
        val panel = BaselineEntryDetailsPanel { opened += it }
        panel.showEntry(entry("missingType.iterableValue"))
        assertTrue(panel.documentationLink.isVisible)
        assertTrue(panel.explanation.text.contains("array<User>"))
        assertTrue(opened.isEmpty())
        panel.documentationLink.doClick()
        assertEquals(listOf("https://phpstan.org/error-identifiers/missingType.iterableValue"), opened)
        render(panel)
        panel.showEntry(entry("phpat.projectRule"))
        assertFalse(panel.documentationLink.isVisible)
        assertFalse(panel.explanation.text.contains("array<User>"))
        panel.documentationLink.doClick()
        assertEquals(1, opened.size)
        panel.showEntry(entry(null))
        assertFalse(panel.groupLabel.isVisible)
        assertFalse(panel.documentationLink.isVisible)
    }

    fun testTooltipsEscapeTextAndListSearchIncludesIdentifiers() {
        val known = entry("missingType.iterableValue")
        val html = BaselinePresentation.tooltip(listOf(BoundEntry(known)))
        assertTrue(html.contains("missingType.*"))
        assertTrue(html.contains("array&lt;User&gt;"))
        assertTrue(html.contains("href=\"https://phpstan.org/error-identifiers/missingType.iterableValue\""))
        assertTrue(BaselineNavigation.searchText(known).contains("missingType.iterableValue"))
        val unsafe = BaselinePresentation.tooltip(listOf(BoundEntry(entry("<img src=x>"))))
        assertFalse(unsafe.contains("<img"))
        assertFalse(unsafe.contains("href="))
        assertTrue(unsafe.contains("&lt;img"))
    }

    fun testPopupContainsDetailsAndSelectionUpdatesThem() {
        val popup = BaselineNavigation.createChooser(project, listOf(entry("missingType.iterableValue"), entry("phpat.projectRule")))
        try {
            fun children(container: Container): List<java.awt.Component> = container.components.flatMap {
                listOf(it) + if (it is Container) children(it) else emptyList()
            }
            val components = children(popup.content)
            val panel = components.filterIsInstance<BaselineEntryDetailsPanel>().single()
            assertTrue(panel.documentationLink.isVisible)
            val list = components.filterIsInstance<javax.swing.JList<*>>().single()
            list.selectedIndex = 1
            assertFalse(panel.documentationLink.isVisible)
        } finally { popup.dispose() }
    }

    fun testBothLanguagesContainAllExplanationAndUiKeys() {
        val bundles = listOf("BaselineBundle.properties", "BaselineBundle_ru.properties").map { name ->
            val properties = Properties()
            javaClass.getResourceAsStream("/messages/$name")!!.reader(Charsets.UTF_8).use { properties.load(it) }
            properties
        }
        assertEquals(bundles[0].stringPropertyNames(), bundles[1].stringPropertyNames())
        assertTrue(bundles[0].stringPropertyNames().any { it.startsWith("error.") })
        assertTrue(bundles[1].getProperty("error.missingType.iterableValue").contains("тип элементов"))
    }

    private fun render(panel: Container) {
        panel.setSize(620, 140)
        fun layout(component: Container) {
            component.doLayout()
            component.components.filterIsInstance<Container>().forEach(::layout)
        }
        layout(panel)
        val image = BufferedImage(620, 140, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().let { graphics -> try { panel.printAll(graphics) } finally { graphics.dispose() } }
        val target = File("build/reports/ui/error-identifier-details.png")
        target.parentFile.mkdirs()
        ImageIO.write(image, "png", target)
    }
}
