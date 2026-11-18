package dev.baselinehighlighter.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import dev.baselinehighlighter.BaselineBundle.message
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.CopyOnWriteArrayList

@Service(Service.Level.PROJECT)
@State(name = "PhpStanBaselineHighlighter", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class BaselineSettings : PersistentStateComponent<BaselineSettings.Data>, ModificationTracker {
    data class Source(var enabled: Boolean = true, var path: String = "", var baseDirectory: String = "")
    data class Profile(var id: String = "", var name: String = "", var sources: MutableList<Source> = mutableListOf()) {
        fun deepCopy(): Profile = copy(sources = sources.map { it.copy() }.toMutableList())
    }
    data class Data(var enabled: Boolean = true, var sources: MutableList<Source> = mutableListOf(),
        var profiles: MutableList<Profile> = mutableListOf(), var activeProfileId: String = "") {
        fun activeProfile(): Profile? = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.firstOrNull()
        fun activeSources(): List<Source> = activeProfile()?.sources ?: sources
    }
    private val version = AtomicLong()
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    @Volatile private var data = migrate(Data())
    override fun getState(): Data = data
    override fun getModificationCount(): Long = version.get()
    override fun loadState(state: Data) = replace(state)
    fun replace(state: Data) {
        data = migrate(state)
        version.incrementAndGet()
        listeners.forEach { it() }
    }

    internal fun addChangeListener(parent: Disposable, listener: () -> Unit) {
        listeners += listener
        Disposer.register(parent, Disposable { listeners.remove(listener) })
    }

    private fun migrate(state: Data): Data {
        val profiles = state.profiles.map { it.deepCopy() }.toMutableList()
        val ids = mutableSetOf<String>()
        profiles.forEachIndexed { index, profile ->
            if (profile.id.isBlank() || !ids.add(profile.id)) {
                profile.id = UUID.randomUUID().toString()
                ids += profile.id
            }
            if (profile.name.isBlank()) profile.name = message("settings.profile.new.name", index + 1)
        }
        if (profiles.isEmpty()) {
            profiles += Profile("default", message("settings.profile.default"), state.sources.map { it.copy() }.toMutableList())
        } else if (state.sources.isNotEmpty() && profiles.none { it.sources == state.sources }) {
            profiles += Profile(UUID.randomUUID().toString(), message("settings.profile.legacy"), state.sources.map { it.copy() }.toMutableList())
        }
        val active = state.activeProfileId.takeIf { id -> profiles.any { it.id == id } } ?: profiles.first().id
        return Data(enabled = state.enabled, profiles = profiles, activeProfileId = active)
    }
}
