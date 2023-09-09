package de.gmuth.ipp.core

/**
 * Copyright (c) 2020-2023 Gerhard Muth
 */

import de.gmuth.log.debug
import de.gmuth.log.warn
import java.io.File
import java.util.logging.Logger.getLogger

open class IppAttributesGroup(val tag: IppTag) : LinkedHashMap<String, IppAttribute<*>>() {

    val log = getLogger(javaClass.name)

    init {
        if (!tag.isGroupTag()) throw IppException("'$tag' is not a group tag")
    }

    open fun put(attribute: IppAttribute<*>, onReplaceWarn: Boolean = false) =
        put(attribute.name, attribute).also {
            if (it != null && onReplaceWarn) log.warn { "replaced '$it' with '${attribute.values.joinToString(",")}' in group $tag" }
        }

    fun attribute(name: String, tag: IppTag, vararg values: Any) =
        put(IppAttribute(name, tag, values.toList()))

    fun attribute(name: String, tag: IppTag, values: List<Any>) =
        put(IppAttribute(name, tag, values))

    @Suppress("UNCHECKED_CAST")
    fun <T> getValueOrNull(name: String) =
        get(name)?.value as T?

    @Suppress("UNCHECKED_CAST")
    fun <T> getValuesOrNull(name: String) =
        get(name)?.values as T?

    @Suppress("UNCHECKED_CAST")
    fun <T> getValue(name: String) =
        get(name)?.value as T ?: throw IppException("attribute '$name' not found in group $tag")

    @Suppress("UNCHECKED_CAST")
    fun <T> getValues(name: String) =
        get(name)?.values as T ?: throw IppException("attribute '$name' not found in group $tag")

    fun getTextValue(name: String) =
        getValue<IppString>(name).text

    fun put(attributesGroup: IppAttributesGroup) {
        log.debug { "put ${attributesGroup.size} attributes" }
        attributesGroup.values.forEach { put(it, false) }
    }

    override fun toString() = "'$tag' $size attributes"

    @JvmOverloads
    fun logDetails(prefix: String = "", title: String = "$tag") {
        log.info { "${prefix}$title" }
        keys.forEach { log.info { "$prefix  ${get(it)}" } }
    }

    fun saveText(file: File) = file.run {
        bufferedWriter().use { writer ->
            values.forEach { value ->
                writer.write(value.toString())
                writer.newLine()
            }
        }
        log.info { "saved $path" }
    }

}