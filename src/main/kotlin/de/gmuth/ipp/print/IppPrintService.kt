package de.gmuth.ipp.print

/**
 * Copyright (c) 2020 Gerhard Muth
 */

import de.gmuth.ipp.client.IppClient
import de.gmuth.ipp.client.IppPrintJob
import de.gmuth.ipp.core.IppTag
import de.gmuth.print.ColorMode
import de.gmuth.print.PrintService
import java.io.File
import java.net.URI

class IppPrintService(private val printerUri: URI) : PrintService {

    private val ippClient = IppClient()

    override fun printFile(file: File, colorMode: ColorMode, waitForTermination: Boolean) {

        val printJob = IppPrintJob(printerUri, file = file)
        printJob.jobGroup.attribute("output-mode", IppTag.Keyword, ippColorMode(colorMode)) // CUPS extension
        printJob.logDetails("IPP: ")

        val job = ippClient.submitPrintJob(printerUri, printJob, waitForTermination)
        job.logDetails()
    }

    // API MAPPING

    fun ippColorMode(colorMode: ColorMode) = when (colorMode) {
        ColorMode.Auto -> "auto"
        ColorMode.Color -> "color"
        ColorMode.Monochrome -> "monochrome"
    }

}