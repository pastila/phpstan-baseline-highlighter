package dev.baselinehighlighter

import com.intellij.DynamicBundle

object BaselineBundle : DynamicBundle(BaselineBundle::class.java, "messages.BaselineBundle") {
    fun message(key: String, vararg params: Any): String = getMessage(key, *params)
}
