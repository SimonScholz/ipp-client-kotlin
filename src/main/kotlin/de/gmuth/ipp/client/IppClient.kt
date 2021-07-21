package de.gmuth.ipp.client

/**
 * Copyright (c) 2020-2021 Gerhard Muth
 */

import de.gmuth.http.Http
import de.gmuth.http.HttpURLConnectionClient
import de.gmuth.http.SSLHelper
import de.gmuth.ipp.core.*
import de.gmuth.ipp.iana.IppRegistrationsSection2
import de.gmuth.log.Logging
import java.net.URI
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicInteger

open class IppClient(
        var ippVersion: String = "1.1",
        val httpClient: Http.Client = HttpURLConnectionClient(),
        var httpBasicAuth: Http.BasicAuth? = null,
        val requestingUserName: String? = if (httpBasicAuth != null) httpBasicAuth.user else System.getProperty("user.name")
) {
    var requestCharset: Charset = Charsets.UTF_8
    var requestNaturalLanguage: String = "en"

    protected val requestCounter = AtomicInteger(1)

    fun trustAnyCertificate() {
        httpClient.config.sslSocketFactory = SSLHelper.sslSocketFactoryForAnyCertificate()
    }

    fun basicAuth(user: String, password: String) {
        httpBasicAuth = Http.BasicAuth(user, password)
    }

    companion object {
        val log = Logging.getLogger {}
    }

    //------------------------------------
    // factory/build method for IppRequest
    //------------------------------------

    fun ippRequest(
            operation: IppOperation,
            printerUri: URI,
            jobId: Int? = null,
            requestedAttributes: List<String>? = null
    ) = IppRequest(
            operation,
            printerUri,
            jobId,
            requestedAttributes,
            requestingUserName,
            ippVersion,
            requestCounter.getAndIncrement(),
            requestCharset,
            requestNaturalLanguage
    )

    //------------------------------------
    // exchange IppRequest for IppResponse
    //------------------------------------

    fun exchangeSuccessful(request: IppRequest) =
            exchange(request).apply {
                if (!isSuccessful()) {
                    IppRegistrationsSection2.validate(request)
                    val statusMessage = operationGroup.getValueOrNull("status-message") ?: IppString("")
                    throw IppExchangeException(request, this, message = "${request.operation} failed: '$status' $statusMessage")
                }
            }

    fun exchange(request: IppRequest): IppResponse {
        val ippUri: URI = request.operationGroup.getValueOrNull("printer-uri") ?: throw IppException("missing 'printer-uri'")
        log.trace { "send '${request.operation}' request to $ippUri" }

        // convert ipp uri to http uri
        val httpUri = with(ippUri) {
            val scheme = scheme.replace("ipp", "http")
            val port = if (port == -1) 631 else port
            URI.create("$scheme://$host:$port$path")
        }

        // http post binary ipp request
        val httpResponse = httpClient.post(
                httpUri, "application/ipp",
                { httpPostStream -> request.write(httpPostStream) },
                httpBasicAuth
        ).apply {
            log.trace { "ipp-server: $server" }
            when {
                !isOK() -> "http request to $httpUri failed: status=$status, content-type=$contentType${textContent()}"
                !hasContentType() -> "missing content-type in http response (application/ipp required)"
                !contentType!!.startsWith("application/ipp") -> "invalid content-type: $contentType"
                else -> ""
            }.let {
                if (it.isNotEmpty()) throw IppExchangeException(request, null, status, message = it)
            }
        }

        // decode ipp response
        return IppResponse().apply {
            try {
                read(httpResponse.contentStream!!)
                log.debug { "$ippUri: $request => $this" }
            } catch (exception: Exception) {
                throw IppExchangeException(request, this, httpResponse.status, "failed to decode ipp response", exception).apply {
                    saveRequestAndResponse("decoding_ipp_response_${request.requestId}_failed")
                }
            }
            if (operationGroup.containsKey("status-message")) log.debug { "status-message: $statusMessage" }
            if (containsGroup(IppTag.Unsupported)) unsupportedGroup.values.forEach { log.warn { "unsupported attribute: $it" } }
        }
    }
}