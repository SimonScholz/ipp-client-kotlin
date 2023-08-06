package de.gmuth.ipp.client

/**
 * Copyright (c) 2020-2023 Gerhard Muth
 */

import de.gmuth.http.Http
import de.gmuth.ipp.core.IppOperation
import de.gmuth.ipp.core.IppOperation.*
import de.gmuth.ipp.core.IppRequest
import de.gmuth.ipp.core.IppTag
import de.gmuth.ipp.core.IppTag.*
import de.gmuth.log.Logging
import java.io.File
import java.io.InputStream
import java.net.URI
import java.time.Duration

// https://www.cups.org/doc/spec-ipp.html
open class CupsClient(
    val cupsUri: URI = URI.create("ipp://localhost"),
    val ippConfig: IppConfig = IppConfig(),
    httpClient: Http.Client = Http.defaultImplementation.createClient(Http.Config())
) {
    constructor(host: String = "localhost") : this(URI.create("ipp://$host"))

    companion object {
        val log = Logging.getLogger { }
    }

    init {
        if (cupsUri.scheme == "ipps") httpClient.config.trustAnyCertificateAndSSLHostname()
    }

    protected val ippClient = IppClient(ippConfig, httpClient)
    fun getIppServer() = ippClient.getHttpServer()

    var userName: String? by ippConfig::userName
    val httpConfig: Http.Config by httpClient::config

    fun getPrinters() = try {
        exchange(ippRequest(CupsGetPrinters))
            .getAttributesGroups(Printer)
            .map { IppPrinter(it, ippClient) }
    } catch (ippExchangeException: IppExchangeException) {
        if (ippExchangeException.isClientErrorNotFound()) emptyList()
        else throw ippExchangeException
    }

    fun getPrinterNames() =
        getPrinters().map { it.name.toString() }

    fun printerExists(printerName: String) =
        getPrinterNames().contains(printerName)

    fun getPrinter(printerName: String) =
        try {
            IppPrinter(printerUri = cupsPrinterUri(printerName), ippClient = ippClient).apply {
                workDirectory = File("cups-${cupsUri.host}")
            }
        } catch (exception: IppExchangeException) {
            if (exception.isClientErrorNotFound()) with(getPrinters()) {
                if (isNotEmpty()) log.warn { "Available CUPS printers: ${map { it.name }}" }
            }
            throw exception
        }

    fun getDefault() = IppPrinter(
        exchange(ippRequest(CupsGetDefault)).printerGroup, ippClient
    )

    fun setDefault(printerName: String) = exchange(
        cupsPrinterRequest(CupsSetDefault, printerName)
    )

    fun cupsPrinterUri(printerName: String) = with(cupsUri) {
        val optionalPort = if (port > 0) ":$port" else ""
        URI("$scheme://$host$optionalPort/printers/$printerName")
    }.apply {
        log.debug { "cupsPrinterUri($printerName) -> $this" }
    }

    // https://www.cups.org/doc/spec-ipp.html#CUPS_ADD_MODIFY_PRINTER
    fun addModifyPrinter(
        printerName: String,
        deviceUri: URI? = null,
        printerInfo: String? = null,
        printerLocation: String? = null,
        ppdName: String? = null, // virtual PPD 'everywhere' is not supported by all CUPS versions
        ppdInputStream: InputStream? = null
    ) = exchange(
        cupsPrinterRequest(
            CupsAddModifyPrinter,
            printerName,
            deviceUri,
            printerInfo,
            printerLocation,
            ppdName,
            ppdInputStream
        )
    )

    fun deletePrinter(printerName: String) = exchange(
        cupsPrinterRequest(CupsDeletePrinter, printerName)
    ).apply {
        log.info { "Printer deleted: $printerName" }
    }

    // https://www.cups.org/doc/spec-ipp.html#CUPS_CREATE_LOCAL_PRINTER
    fun createLocalPrinter(
        printerName: String,
        deviceUri: URI,
        printerInfo: String?,
        printerLocation: String?,
        ppdName: String? // virtual PPD 'everywhere' is supported asynchronous
    ) = exchange(
        cupsPrinterRequest(
            CupsCreateLocalPrinter,
            printerName,
            deviceUri,
            printerInfo,
            printerLocation,
            ppdName
        )
    )

    // --------------------------------------
    // build request for a named CUPS printer
    // --------------------------------------

    protected fun cupsPrinterRequest(
        operation: IppOperation,
        printerName: String,
        deviceUri: URI? = null,
        printerInfo: String? = null,
        printerLocation: String? = null,
        ppdName: String? = null,
        ppdInputStream: InputStream? = null
    ) =
        ippRequest(operation, cupsPrinterUri(printerName)).apply {
            with(createAttributesGroup(Printer)) {
                attribute("printer-name", NameWithoutLanguage, printerName)
                deviceUri?.let { attribute("device-uri", Uri, it) }
                ppdName?.let { attribute("ppd-name", NameWithoutLanguage, it) }
                printerInfo?.let { attribute("printer-info", TextWithoutLanguage, it) }
                printerLocation?.let { attribute("printer-location", TextWithoutLanguage, it) }
            }
            ppdInputStream?.let { documentInputStream = ppdInputStream }
        }

    //----------------------
    // delegate to IppClient
    //----------------------

    fun ippRequest(operation: IppOperation, printerURI: URI = cupsUri) =
        ippClient.ippRequest(operation, printerURI)

    fun exchange(ippRequest: IppRequest) =
        ippClient.exchange(ippRequest)

    fun basicAuth(user: String, password: String) =
        ippClient.basicAuth(user, password)

    //-----------------------
    // Delegate to IppPrinter
    //-----------------------

    val ippPrinter: IppPrinter by lazy {
        IppPrinter(cupsUri, ippClient = ippClient, getPrinterAttributesOnInit = false).apply {
            workDirectory = File("cups-${cupsUri.host}")
        }
    }

    fun getJobsAndSaveDocuments(whichJobs: IppWhichJobs = IppWhichJobs.All, command: String? = null) =
        ippPrinter.cupsGetJobsAndSaveDocuments(whichJobs, command)

    //----------------------------
    // Create printer subscription
    //----------------------------

    fun createPrinterSubscription(
        // https://datatracker.ietf.org/doc/html/rfc3995#section-5.3.3.4.2
        notifyEvents: List<String>? = listOf("all"),
        notifyLeaseDuration: Duration? = null,
        notifyTimeInterval: Duration? = null
    ) =
        ippPrinter.createPrinterSubscription(notifyEvents, notifyLeaseDuration, notifyTimeInterval)

    fun createPrinterSubscription(
        vararg notifyEvents: String = arrayOf("all"),
        notifyLeaseDuration: Duration? = null,
        notifyTimeInterval: Duration? = null
    ) =
        createPrinterSubscription(notifyEvents.toList(), notifyLeaseDuration, notifyTimeInterval)

    //-----------------------------
    // Setup IPP Everywhere Printer
    //-----------------------------

    fun setupIppEverywherePrinter(
        printerName: String,
        deviceUri: URI,
        printerInfo: String? = null,
        printerLocation: String? = null
    ): IppPrinter {

        // validate ipp scheme
        require(deviceUri.scheme.startsWith("ipp")) { "uri scheme unsupported: $deviceUri" }

        createLocalPrinter(printerName, deviceUri, printerInfo, printerLocation, ppdName = "everywhere").apply {
            log.info {
                "$statusMessage ${
                    printerGroup.getValues<List<URI>>("printer-uri-supported").joinToString(",")
                }"
            }
        }

        return getPrinter(printerName).apply {

            // https://github.com/apple/cups/issues/5919
            log.info { "Waiting for CUPS to generate IPP Everywhere PPD." }
            log.info { this }
            do {
                Thread.sleep(1000)
                updateAttributes("printer-make-and-model")
            } while (!makeAndModel.text.lowercase().contains("everywhere"))
            log.info { this }

            // make printer permanent
            exchange(
                cupsPrinterRequest(CupsAddModifyPrinter, printerName).apply {
                    createAttributesGroup(Printer).run {
                        attribute("printer-is-temporary", IppTag.Boolean, false)
                    }
                }
            )

            // make printer operational
            enable()
            resume()
            updateAttributes()
            log.info { this }
        }
    }

    // -----------------------------------------------------------------
    // Subscribe to 'job-created' events and then get and save documents
    // https://www.rfc-editor.org/rfc/rfc3995.html#section-5.3.3.4.3
    // -----------------------------------------------------------------

    fun subscribeToJobCreatedEventsAndThenGetAndSaveDocuments(
        workDirectory: File = File("cups-${cupsUri.host}"),
        leaseDuration: Duration = Duration.ofMinutes(30),
        autoRenewLease: Boolean = true,
        command: String? = null // e.g. "open" -> open <filename> with Preview on MacOS
    ) {
        ippPrinter.workDirectory = workDirectory
        log.info { "workDirectory: $workDirectory" }
        createPrinterSubscription("job-created", notifyLeaseDuration = leaseDuration)
            .getAndHandleNotifications(Duration.ofSeconds(1), autoRenewSubscription = autoRenewLease) {
                log.info { it }
                it.getJob().apply {
                    log.info { this }
                    cupsGetAndSaveDocuments(command = command, onIppExceptionThrow = false)
                }
            }
    }
}