package dev.baselinehighlighter.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros

@Service(Service.Level.PROJECT)
@State(name = "PhpStanBaselineHighlighter", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class BaselineSettings : PersistentStateComponent<BaselineSettings.Data> {
    data class Source(var enabled: Boolean = true, var path: String = "", var baseDirectory: String = "")
    data class Data(var enabled: Boolean = true, var sources: MutableList<Source> = mutableListOf())
    @Volatile private var data = Data()
    override fun getState(): Data = data
    override fun loadState(state: Data) { data = state }
    fun replace(state: Data) { data = state }
}
