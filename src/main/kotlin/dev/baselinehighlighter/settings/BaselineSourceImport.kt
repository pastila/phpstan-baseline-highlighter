package dev.baselinehighlighter.settings

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import dev.baselinehighlighter.core.BaselineConfigurationImporter
import java.io.IOException

/** Reads current documents under short read actions; parsing runs on the caller's background thread. */
internal object BaselineSourceImport {
    fun discover(root: VirtualFile, checkCanceled: () -> Unit): BaselineConfigurationImporter.Result =
        BaselineConfigurationImporter.discover(root.path, { path ->
            checkCanceled()
            try {
                ReadAction.compute<BaselineConfigurationImporter.Content, RuntimeException> {
                    checkCanceled()
                    val file = LocalFileSystem.getInstance().findFileByPath(path)
                    when {
                        file == null || !file.isValid || file.isDirectory -> BaselineConfigurationImporter.Content()
                        file.length > BaselineConfigurationImporter.MAX_FILE_CHARS * 4L -> BaselineConfigurationImporter.Content(problem = "file.limit")
                        else -> {
                            val document = FileDocumentManager.getInstance().getDocument(file)
                            when {
                                document == null -> BaselineConfigurationImporter.Content()
                                document.textLength > BaselineConfigurationImporter.MAX_FILE_CHARS -> BaselineConfigurationImporter.Content(problem = "file.limit")
                                else -> BaselineConfigurationImporter.Content(document.immutableCharSequence.toString())
                            }
                        }
                    }
                }
            } catch (_: IOException) { BaselineConfigurationImporter.Content() }
        }, SystemInfoRt.isFileSystemCaseSensitive, checkCanceled)
}
