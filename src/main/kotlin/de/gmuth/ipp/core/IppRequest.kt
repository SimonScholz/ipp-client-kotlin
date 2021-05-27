package de.gmuth.ipp.core

/**
 * Copyright (c) 2020 Gerhard Muth
 */

import java.net.URI
import java.nio.charset.Charset

class IppRequest() : IppMessage() {

    override val codeDescription: String
        get() = operation.toString()

    val operation: IppOperation
        get() = IppOperation.fromNumber(code!!)

    constructor(
            operation: IppOperation,
            printerUri: URI? = null,
            jobId: Int? = null,
            requestedAttributes: List<String>? = null,
            requestingUserName: String? = null,
            version: String = "1.1",
            requestId: Int = 1,
            charset: Charset = Charsets.UTF_8,
            naturalLanguage: String = "en"
    ) : this() {

        this.version = version
        this.code = operation.code
        this.requestId = requestId

        with(createAttributesGroup(IppTag.Operation)) {

            // required attributes
            attribute("attributes-charset", IppTag.Charset, charset)
            attribute("attributes-natural-language", IppTag.NaturalLanguage, naturalLanguage)

            // optional attributes
            jobId?.let { attribute("job-id", IppTag.Integer, it) }
            printerUri?.let { attribute("printer-uri", IppTag.Uri, it) }
            requestedAttributes?.let { attribute("requested-attributes", IppTag.Keyword, it) }
            requestingUserName?.let { attribute("requesting-user-name", IppTag.NameWithoutLanguage, it.toIppString()) }
        }
    }
}