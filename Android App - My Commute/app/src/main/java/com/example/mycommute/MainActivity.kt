package com.example.mycommute

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var startButton: Button
    private lateinit var endButton: Button
    private lateinit var boardButton: Button
    private lateinit var unboardButton: Button
    private lateinit var redButton: Button
    private lateinit var greenButton: Button
    private lateinit var busSpinner: Spinner
    private lateinit var logTextView: TextView
    private lateinit var scrollView: ScrollView

    private var startTime: Long = 0L
    private var endTime: Long = 0L

    private val segments = mutableListOf<CommuteSegment>()
    private var currentSegment: CommuteSegment? = null

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        initState()
        setupSpinner()
        setupListeners()
    }

    private fun bindViews() {
        startButton = findViewById(R.id.buttonStart)
        endButton = findViewById(R.id.buttonEnd)
        boardButton = findViewById(R.id.buttonBoard)
        unboardButton = findViewById(R.id.buttonUnboard)
        redButton = findViewById(R.id.buttonRed)
        greenButton = findViewById(R.id.buttonGreen)
        busSpinner = findViewById(R.id.spinnerBus)
        logTextView = findViewById(R.id.textViewLog)
        scrollView = findViewById(R.id.scrollViewLog)
    }

    private fun initState() {
        endButton.isEnabled = false
        boardButton.isEnabled = false
        unboardButton.isEnabled = false
        redButton.isEnabled = false
        greenButton.isEnabled = false
        redButton.alpha = 0.5f
        greenButton.alpha = 0.5f
    }

    private fun setupSpinner() {
        val busOptions = arrayOf("Bus 179", "Bus 179A", "Bus 180")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, busOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        busSpinner.adapter = adapter
    }

    private fun setupListeners() {
        startButton.setOnClickListener {
            startTime = System.currentTimeMillis()
            segments.clear()
            currentSegment = null
            startButton.isEnabled = false
            endButton.isEnabled = true
            boardButton.isEnabled = true
            updateLog()
        }

        boardButton.setOnClickListener {
            val now = System.currentTimeMillis()
            currentSegment = CommuteSegment(
                bus = busSpinner.selectedItem.toString(),
                boardTime = now
            )
            segments.add(currentSegment!!)
            boardButton.isEnabled = false
            unboardButton.isEnabled = true
            redButton.isEnabled = true
            redButton.alpha = 1f
            updateLog()
        }

        unboardButton.setOnClickListener {
            val now = System.currentTimeMillis()
            currentSegment?.unboardTime = now
            unboardButton.isEnabled = false
            boardButton.isEnabled = true
            redButton.isEnabled = false
            greenButton.isEnabled = false
            redButton.alpha = 0.5f
            greenButton.alpha = 0.5f
            updateLog()
        }

        redButton.setOnClickListener {
            val now = System.currentTimeMillis()
            currentSegment?.stopEvents?.add(StopEvent("Red Light", now))
            redButton.isEnabled = false
            greenButton.isEnabled = true
            greenButton.alpha = 1f
            redButton.alpha = 0.5f
            updateLog()
        }

        greenButton.setOnClickListener {
            val now = System.currentTimeMillis()
            currentSegment?.stopEvents?.add(StopEvent("Green Light", now))
            greenButton.isEnabled = false
            redButton.isEnabled = true
            redButton.alpha = 1f
            greenButton.alpha = 0.5f
            updateLog()
        }

        endButton.setOnClickListener {
            endTime = System.currentTimeMillis()
            endButton.isEnabled = false
            startButton.isEnabled = true
            boardButton.isEnabled = false
            unboardButton.isEnabled = false
            redButton.isEnabled = false
            greenButton.isEnabled = false
            redButton.alpha = 0.5f
            greenButton.alpha = 0.5f
            updateLog()
            appendDetailedCommuteCsv()
        }
    }

    private fun updateLog() {
        val sb = StringBuilder()
        if (startTime > 0) {
            sb.append("Commute started at: ${timeFormatter.format(Date(startTime))}\n\n")
        }
        segments.forEach { seg ->
            sb.append("Boarded: ${seg.bus} at ${timeFormatter.format(Date(seg.boardTime))}\n")
            if (seg.stopEvents.isNotEmpty()) {
                sb.append("Stops:\n")
                seg.stopEvents.forEach { ev ->
                    sb.append("    ${ev.type} at ${timeFormatter.format(Date(ev.timestamp))}\n")
                }
            }
            seg.unboardTime?.let {
                sb.append("Unboarded at ${timeFormatter.format(Date(it))}\n")
            }
            sb.append("\n")
        }
        if (endTime > 0) {
            sb.append("Commute ended at: ${timeFormatter.format(Date(endTime))}")
        }
        logTextView.text = sb.toString()
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun appendDetailedCommuteCsv() {
        val file = File(filesDir, "commute_log.csv")
        val newFile = !file.exists()
        val csvBuilder = StringBuilder()
        if (newFile) {
            csvBuilder.append(
                "Date,Start Time,Bus 1,Board Time 1,Unboard Time 1,Stops 1,Total Time 1,Stop Time 1,"
                        + "Wait Time,Bus 2,Board Time 2,Unboard Time 2,Stops 2,Total Time 2,Stop Time 2,"
                        + "End Time,Stop Events,Total Commute Time,Total Stop Time\n"
            )
        }
        val dateStr = dateFormatter.format(Date(startTime))
        val startStr = timeFormatter.format(Date(startTime))
        val endStr = timeFormatter.format(Date(endTime))
        val totalCommute = (endTime - startTime) / 1000

        val seg1 = segments.getOrNull(0)
        val seg2 = segments.getOrNull(1)

        fun segFields(seg: CommuteSegment?): List<String> {
            if (seg == null) return List(6) { "" }
            val stops = seg.stopEvents.filter { it.type.startsWith("Red") || it.type.startsWith("Green") }
            val duration = (seg.unboardTime!! - seg.boardTime) / 1000
            val stopTime = calculateStopTime(seg.stopEvents) / 1000
            return listOf(
                seg.bus,
                timeFormatter.format(Date(seg.boardTime)),
                timeFormatter.format(Date(seg.unboardTime!!)),
                stops.size.toString(),
                duration.toString(),
                stopTime.toString()
            )
        }

        val fields1 = segFields(seg1)
        val fields2 = segFields(seg2)

        val waitSec = if (seg1 != null && seg2 != null) (seg2.boardTime - seg1.unboardTime!!) / 1000 else 0L

        val stopEventsSummary = segments.joinToString("; ") { s ->
            s.stopEvents.joinToString(", ") {
                "${it.type}: ${timeFormatter.format(Date(it.timestamp))}"
            }.let { "${s.bus} [$it]" }
        }

        val row = mutableListOf<String>().apply {
            add(dateStr)
            add(startStr)
            addAll(fields1)
            add(waitSec.toString())
            addAll(fields2)
            add(endStr)
            add(escapeCsv(stopEventsSummary))
            add(totalCommute.toString())
            add(((calculateStopTime(seg1?.stopEvents ?: emptyList())
                    + calculateStopTime(seg2?.stopEvents ?: emptyList())) / 1000).toString())
        }

        csvBuilder.append(row.joinToString(","))
        csvBuilder.append("\n")
        file.appendText(csvBuilder.toString())

        val exportFile = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "commute_log.csv")
        file.copyTo(exportFile, overwrite = true)

        Toast.makeText(this, "Log saved to ${file.absolutePath}", Toast.LENGTH_LONG).show()
    }

    private fun calculateStopTime(events: List<StopEvent>): Long {
        var total = 0L
        var redTime: Long? = null
        events.forEach {
            if (it.type == "Red Light") redTime = it.timestamp
            else if (it.type == "Green Light" && redTime != null) {
                total += (it.timestamp - redTime!!)
                redTime = null
            }
        }
        return total
    }

    private fun escapeCsv(value: String): String = if (value.contains(",") || value.contains(";")) "\"$value\"" else value

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_history -> {
                startActivity(Intent(this, HistoryActivity::class.java))
                true
            }
            R.id.action_statistics -> {
                startActivity(Intent(this, StatsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

data class CommuteSegment(
    val bus: String,
    val boardTime: Long,
    var unboardTime: Long? = null,
    val stopEvents: MutableList<StopEvent> = mutableListOf()
)

data class StopEvent(val type: String, val timestamp: Long)
