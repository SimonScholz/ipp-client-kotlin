package de.gmuth.ipp.client

/**
 * Copyright (c) 2020 Gerhard Muth
 */

import de.gmuth.ipp.core.IppAttributesGroup
import de.gmuth.ipp.core.IppIntegerTime
import de.gmuth.ipp.core.IppString
import java.net.URI
import java.time.Duration

class IppJob(jobGroup: IppAttributesGroup) {

    lateinit var uri: URI
    var id: Int = 0
    var state: IppJobState? = null
    var stateReasons: List<String>? = null

    var printerUri: URI? = null
    var name: IppString? = null
    var originatingUserName: IppString? = null

    var timeAtCreation: IppIntegerTime? = null
    var timeAtProcessing: IppIntegerTime? = null
    var timeAtCompleted: IppIntegerTime? = null
    var printerUpTime: IppIntegerTime? = null

    var impressions: Int? = null
    var impressionsCompleted: Int? = null
    var mediaSheets: Int? = null
    var mediaSheetsCompleted: Int? = null

    init {
        readFrom(jobGroup)
    }

    fun readFrom(jobGroup: IppAttributesGroup) = with(jobGroup) {
        uri = getValue("job-uri")
        id = getValue("job-id")
        state = IppJobState.fromInt(getValue("job-state") as Int?)
        stateReasons = getValues("job-state-reasons")

        printerUri = getValue("job-printer-uri")
        name = getValue("job-name")
        originatingUserName = getValue("job-originating-user-name")

        fun getTimeAt(name: String) = IppIntegerTime.fromInt(getValue(name) as Int?)
        timeAtCreation = getTimeAt("time-at-creation")
        timeAtProcessing = getTimeAt("time-at-processing")
        timeAtCompleted = getTimeAt("time-at-completed")
        printerUpTime = getTimeAt("job-printer-up-time")

        impressions = getValue("job-impressions")
        impressionsCompleted = getValue("job-impressions-completed")
        mediaSheets = getValue("job-media-sheets")
        mediaSheetsCompleted = getValue("job-media-sheets-completed")
    }


    override fun toString(): String {
        val stateString =
                if (state == null) ""
                else ", state = $state, stateReasons = $stateReasons"

        return "IppJob: id = $id, uri = $uri$stateString"
    }

    fun logDetails() {
        println("JOB-$id")
        println("  uri = $uri")
        println("  id = $id")
        logAttributeIfValueNotNull("state", state)
        logAttributeIfValueNotNull("stateReasons", stateReasons)
        logAttributeIfValueNotNull("printerUri", printerUri)
        logAttributeIfValueNotNull("name", name)
        logAttributeIfValueNotNull("originatingUserName", originatingUserName)
        logAttributeIfValueNotNull("timeAtCreation", timeAtCreation)
        logAttributeIfValueNotNull("timeAtProcessing", timeAtProcessing)
        logAttributeIfValueNotNull("timeAtCompleted", timeAtCompleted)
        logAttributeIfValueNotNull("printerUpTime", printerUpTime)
        logAttributeIfValueNotNull("impressions", impressions)
        logAttributeIfValueNotNull("impressionsCompleted", impressionsCompleted)
        logAttributeIfValueNotNull("mediaSheets", mediaSheets)
        logAttributeIfValueNotNull("mediaSheetsCompleted", mediaSheetsCompleted)
    }

    private fun logAttributeIfValueNotNull(name: String, value: Any?) {
        if (value != null) println("  $name = $value")
    }

    // ------ operations requiring an IppClient ------

    val ippClient = IppClient()

    fun cancel() = ippClient.cancelJob(uri)

    fun refreshAttributes() {
        val response = ippClient.getJobAttributes(uri)
        readFrom(response.jobGroup)
    }

    fun waitForTermination(refreshRate: Duration = Duration.ofSeconds(1)) {
        do {
            Thread.sleep(refreshRate.toMillis())
            refreshAttributes()
            println("job-state = $state, job-impressions-completed = $impressionsCompleted")
        } while (state?.isNotTerminated()!!)
    }

}