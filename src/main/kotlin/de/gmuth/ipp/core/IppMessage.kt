package de.gmuth.ipp.core

/**
 * Copyright (c) 2020 Gerhard Muth
 */

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

abstract class IppMessage {

    var version: IppVersion? = null
    protected var code: Short? = null
    var requestId: Int? = null
    var naturalLanguage: String? = null

    companion object {
        var verbose: Boolean = false
    }

    // --------------------------------------------------------------------- IPP MESSAGE ENCODING

    private fun writeTo(outputStream: OutputStream) {
        if (version == null) throw IllegalArgumentException("version must not be null")
        if (code == null) throw IllegalArgumentException("code must not be null")
        if (requestId == null) throw IllegalArgumentException("requestId must not be null")
        if (naturalLanguage == null) throw IllegalArgumentException("naturalLanguage must not be null")

        with(IppOutputStream(outputStream)) {
            writeVersion(version as IppVersion)
            writeCode(code as Short)
            writeRequestId(requestId as Int)

            writeTag(IppTag.Operation)
            writeAttribute(IppTag.Charset, "attributes-charset", this.attributesCharset.name().toLowerCase())
            writeAttribute(IppTag.NaturalLanguage, "attributes-natural-language", naturalLanguage as String)
            writeOperationAttributes(this)

            writeJobGroups(this)
            writeTag(IppTag.End)
            close()
        }
    }

    open fun writeOperationAttributes(ippOutputStream: IppOutputStream) {
        // implement in subclass for extra attributes
    }

    open fun writeJobGroups(ippOutputStream: IppOutputStream) {
        // implement in subclass for extra attributes
    }

    fun toByteArray(): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()
        writeTo(byteArrayOutputStream)
        byteArrayOutputStream.close()
        return byteArrayOutputStream.toByteArray()
    }

    fun toInputStream(): InputStream {
        return ByteArrayInputStream(toByteArray())
    }

    // --------------------------------------------------------------------- IPP MESSAGE DECODING

    open fun readFrom(inputStream: InputStream): String? {
        val ippInputStream = IppInputStream(inputStream)

        val version = ippInputStream.readVersion()
        if (verbose) println("version = $version")

        code = ippInputStream.readCode()
        if (verbose) println(getCodeDescription())

        requestId = ippInputStream.readRequestId()
        if (verbose) println("request-id = $requestId")

        var tag: IppTag
        do {
            tag = ippInputStream.readTag()
            if (tag.isGroupTag()) {
                if (tag != IppTag.End && verbose)
                    println(String.format("%s group", tag))

            } else {
                // attribute tags
                val (name, value) = ippInputStream.readAttribute(tag)
                if (verbose) println(String.format("  %s (%s) = %s", name, tag, value))
            }
        } while (tag != IppTag.End)
        ippInputStream.close()

        return ippInputStream.statusMessage;
    }

    open fun getCodeDescription(): String = String.format("code = %04X", code)

}