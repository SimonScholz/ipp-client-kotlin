package de.gmuth.ipp.core

/**
 * Copyright (c) 2020 Gerhard Muth
 */

data class IppVersion(val major: Int = 1, val minor: Int = 1) {

    override fun toString() = "$major.$minor"

    constructor(version: String) : this(
            matchedGroupIntValue(version, 1),
            matchedGroupIntValue(version, 2)
    )

    companion object {
        private fun matchedGroupIntValue(version: String, group: Int): Int {
            val matchedVersion = """^(\d)\.(\d)$""".toRegex().find(version) ?: throw IllegalArgumentException(version)
            return matchedVersion.groups[group]!!.value.toInt()
        }
    }

}