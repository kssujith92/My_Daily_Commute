package com.example.mycommute

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import android.content.res.Configuration
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.core.content.ContextCompat

import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.utils.ColorTemplate


class StatsActivity : AppCompatActivity() {

    private lateinit var statsTextView: TextView
    private lateinit var pieChart: PieChart
    private lateinit var busFilterSpinner: Spinner
    private var selectedBus: String = "Total"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)
        supportActionBar?.title = "Statistics"

        busFilterSpinner = findViewById(R.id.busFilterSpinner)

        val busOptions = arrayOf("Total", "Bus 179", "Bus179A" , "Bus 180")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, busOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        busFilterSpinner.adapter = adapter

        busFilterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedBus = parent.getItemAtPosition(position).toString()
                renderStats() // re-calculate and refresh
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        statsTextView = findViewById(R.id.textViewStats)
        pieChart = findViewById(R.id.pieChart)
        renderStats()
    }

    private fun renderStats() {
        val file = File(filesDir, "commute_log.csv")
        if (!file.exists()) {
            statsTextView.text = "No commute data available."
            return
        }

        val lines = file.readLines().drop(1).filter { it.trim().isNotEmpty() }
        if (lines.isEmpty()) {
            statsTextView.text = "No commute data available."
            return
        }

        var morningStats = CommuteStats()
        var eveningStats = CommuteStats()
        var morningCount = 0
        var eveningCount = 0

        for (line in lines) {
            val cols = parseCsvLine(line)
            if (cols.size < 19) continue

            val bus1 = cols[2]
            val bus2 = cols[9]
            if (selectedBus != "Total" && selectedBus != bus1 && selectedBus != bus2) continue

            var commuteTime = 0
            var waitTotal = 0
            var stopCount = 0
            var stopTimeTotal = 0
            var movingTime = 0
            var start: LocalTime? = null

            if (selectedBus == "Total") {
                val startTime = cols[1]
                val bus1Board = cols[3]
                commuteTime = cols[17].toIntOrNull() ?: continue
                stopTimeTotal = cols[18].toIntOrNull() ?: 0

                val waitBeforeBus1 = durationBetween(startTime, bus1Board)
                val waitBetweenBus = cols[8].toIntOrNull() ?: 0
                waitTotal = waitBeforeBus1 + waitBetweenBus

                stopCount = (cols[5].toIntOrNull() ?: 0) + (cols[12].toIntOrNull() ?: 0)
                movingTime = commuteTime - stopTimeTotal - waitTotal

                start = parseTime(startTime)
            }else if (selectedBus == bus1) {
                val startTime = cols[1]
                val busBoard = cols[3]
                val busUnboard = cols[4]

                commuteTime = durationBetween(startTime, busUnboard)
                stopTimeTotal = cols[7].toIntOrNull() ?: 0

                val waitBeforeBus = durationBetween(startTime, busBoard)
                waitTotal = waitBeforeBus

                stopCount = (cols[5].toIntOrNull() ?: 0)
                movingTime = commuteTime - stopTimeTotal - waitTotal

                start = parseTime(startTime)
            }else if (selectedBus == bus2) {
                val startTime = cols[4]
                val busUnboard = cols[11]
                commuteTime = durationBetween(startTime, busUnboard)
                stopTimeTotal = cols[14].toIntOrNull() ?: 0

                waitTotal = cols[8].toIntOrNull() ?: 0

                stopCount = (cols[12].toIntOrNull() ?: 0)
                movingTime = commuteTime - stopTimeTotal - waitTotal

                start = parseTime(startTime)
            }


            val stats = if (start != null && start.isBefore(LocalTime.NOON)){
                morningCount++
                morningStats
            } else {
                eveningCount++
                eveningStats
            }

            stats.totalCommuteTime += commuteTime
            stats.totalWaitTime += waitTotal
            stats.totalStopCount += stopCount
            stats.totalStopTime += stopTimeTotal
            stats.totalMovingTime += movingTime
        }

        val sb = StringBuilder()

        sb.append("📊 Morning Commute: \n\t\t\t (Average times)\n\n")
        sb.append(morningStats.formatStats(morningCount))
        sb.append("\n\n🌆 Evening Commute: \n\t\t\t (Average times)\n\n")
        sb.append(eveningStats.formatStats(eveningCount))

        statsTextView.text = sb.toString()

        val totalCount = morningCount + eveningCount
        val totalStats = CommuteStats(
            totalCommuteTime = morningStats.totalCommuteTime + eveningStats.totalCommuteTime,
            totalWaitTime = morningStats.totalWaitTime + eveningStats.totalWaitTime,
            totalStopCount = morningStats.totalStopCount + eveningStats.totalStopCount,
            totalStopTime = morningStats.totalStopTime + eveningStats.totalStopTime,
            totalMovingTime = morningStats.totalMovingTime + eveningStats.totalMovingTime
        )

        if (totalCount > 0) {
            setupPieChart(totalStats)
        } else {
            pieChart.clear()
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val regex = Regex(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")
        return line.split(regex).map { it.trim().trim('"') }
    }

    data class CommuteStats(
        var totalCommuteTime: Int = 0,
        var totalWaitTime: Int = 0,
        var totalStopCount: Int = 0,
        var totalStopTime: Int = 0,
        var totalMovingTime: Int = 0
    ) {
        fun formatStats(count: Int): String {
            if (count == 0) return "No data."

            return """
                Commute time: ${formatTomin(totalCommuteTime / count)}
                Waiting time: ${formatTomin(totalWaitTime / count)}
                No. of stops: ${"%.1f".format(totalStopCount.toDouble() / count)}
                Stop time: ${formatTomin(totalStopTime / count)}
                Time moving: ${formatTomin(totalMovingTime / count)}
            """.trimIndent()
        }

        private fun formatTomin(seconds: Int): String {
            val minutes = seconds / 60
            val rem = seconds % 60
            return if (minutes > 0) "$minutes min $rem sec" else "$rem sec"
        }
    }

    private val timeFormats = listOf(
        DateTimeFormatter.ofPattern("HH:mm"),
        DateTimeFormatter.ofPattern("H:mm"),
        DateTimeFormatter.ofPattern("HH:mm:ss")
    )

    private fun parseTime(timeStr: String): LocalTime? {
        for (format in timeFormats) {
            try {
                return LocalTime.parse(timeStr, format)
            } catch (_: Exception) {}
        }
        return null
    }

    private fun durationBetween(timeStr1: String, timeStr2: String): Int {
        val t1 = parseTime(timeStr1)
        val t2 = parseTime(timeStr2)
        return if (t1 != null && t2 != null) {
            Duration.between(t1, t2).seconds.toInt().coerceAtLeast(0)
        } else 0
    }

    private fun setupPieChart(stats: CommuteStats) {
        val entries = ArrayList<PieEntry>()
        entries.add(PieEntry(stats.totalMovingTime.toFloat(), "Moving"))
        entries.add(PieEntry(stats.totalWaitTime.toFloat(), "Waiting"))
        entries.add(PieEntry(stats.totalStopTime.toFloat(), "Stopped"))

        val dataSet = PieDataSet(entries, "")
        dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()
        dataSet.valueTextSize = 14f

        val data = PieData(dataSet)
        data.setValueFormatter(PercentFormatter(pieChart))
        val color = ContextCompat.getColor(this, R.color.white)
        dataSet.valueTextColor = color

        pieChart.data = data
        pieChart.description.isEnabled = false
        pieChart.setDrawEntryLabels(false)
        pieChart.setUsePercentValues(true)
        pieChart.centerText = if (selectedBus == "Total") "My Commute" else selectedBus
        pieChart.setCenterTextSize(15f)
        pieChart.animateY(1000)


        pieChart.legend.textSize = 14f
        pieChart.legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER

        pieChart.invalidate() // refresh chart


        val isDarkMode = (resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES


        if (isDarkMode) {
            pieChart.setHoleColor(ContextCompat.getColor(this, R.color.grey_dark))
            pieChart.setCenterTextColor(ContextCompat.getColor(this, R.color.blue_1))
            pieChart.legend.textColor = ContextCompat.getColor(this, R.color.white)
            pieChart.setTransparentCircleColor(ContextCompat.getColor(this, R.color.grey_dark))
        } else {
            pieChart.setHoleColor(ContextCompat.getColor(this, R.color.white))
            pieChart.setCenterTextColor(ContextCompat.getColor(this, R.color.blue_1))
            pieChart.legend.textColor = ContextCompat.getColor(this, R.color.black)
        }
    }

}
