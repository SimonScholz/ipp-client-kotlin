package de.gmuth.ipp.core

/**
 * Copyright (c) 2020-2024 Gerhard Muth
 */

import java.util.logging.Level
import java.util.logging.Level.INFO
import java.util.logging.Logger

// RFC8010 3.1.6.
data class IppCollection(val members: MutableCollection<IppAttribute<*>> = mutableListOf()) {

    constructor(vararg attributes: IppAttribute<*>) : this(attributes.toMutableList())

    fun addAttribute(name: String, tag: IppTag, vararg values: Any) =
        add(IppAttribute(name, tag, values.toMutableList()))

    fun add(attribute: IppAttribute<*>) =
        members.add(attribute)

    fun addAll(attributes: Collection<IppAttribute<*>>) =
        members.addAll(attributes)

    fun containsMember(memberName: String) =
        members.map { it.name }.contains(memberName)

    @Suppress("UNCHECKED_CAST")
    fun <T> getMember(memberName: String) =
        members.single { it.name == memberName } as IppAttribute<T>

    @Suppress("UNCHECKED_CAST")
    fun <T> getMemberOrNull(memberName: String) =
        members.singleOrNull { it.name == memberName } as IppAttribute<T>?

    fun <T> getValues(memberName: String) =
        getMember<T>(memberName).values

    fun <T> getValue(memberName: String) =
        getMember<T>(memberName).value

    fun <T> getValueOrNull(memberName: String) =
        getMemberOrNull<T>(memberName)?.value

    val size: Int
        get() = members.size

    override fun toString() = members.joinToString(" ", "{", "}") {
        "${it.name}=${it.valuesToString()}"
    }

    fun log(logger: Logger, level: Level = INFO, prefix: String = "") {
        val string = toString()
        if (string.length < 160) logger.log(level) { "$prefix$string" }
        else members.forEach { member -> member.log(logger, level, prefix) }
    }
}