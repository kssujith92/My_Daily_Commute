package com.example.mycommute

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class HistoryActivity : AppCompatActivity() {

    private lateinit var historyTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        historyTextView = findViewById(R.id.textViewHistory)

        val deleteButton = findViewById<Button>(R.id.buttonDeleteLast)
        deleteButton.setOnClickListener {
            deleteLastEntry()
        }

        renderHistory()
    }

    private fun renderHistory() {
        val file = File(filesDir, "commute_log.csv")

        if (!file.exists()) {
            historyTextView.text = "No commute history available."
            return
        }

        val lines = file.readLines()
        if (lines.size <= 1) {
            historyTextView.text = "No commute history available."
            return
        }

        val header = lines[0].split(",")
        val commuteLines = lines.drop(1).filter { it.trim().isNotEmpty() }.reversed()

        val sb = StringBuilder()

        for (line in commuteLines) {
            val cols = parseCsvLine(line)
            if (cols.size < header.size) continue

            val date = cols[0]
            val startTime = cols[1]
            val endTime = cols[15]
            val totalCommuteTime = cols.getOrNull(17) ?: ""
            val totalStopTime = cols.getOrNull(18) ?: ""

            sb.append("Commute on $date\n")
            sb.append("Started at: $startTime\n")
            sb.append("Bus 1: ${cols[2]} (Boarded at ${cols[3]}, Unboarded at ${cols[4]})\n")
            sb.append("Stops: ${cols[5]} stops, total time ${formatTomin(cols[6])}, stop time ${formatTomin(cols[7])}\n")
            sb.append("Wait time before next bus: ${formatTomin(cols[8])}\n")
            sb.append("Bus 2: ${cols[9]} (Boarded at ${cols[10]}, Unboarded at ${cols[11]})\n")
            sb.append("Stops: ${cols[12]} stops, total time ${formatTomin(cols[13])}, stop time ${formatTomin(cols[14])}\n")
            sb.append("Ended at: $endTime\n")
            sb.append("Total commute time: ${formatTomin(totalCommuteTime)}\n")
            sb.append("Total stop time: ${formatTomin(totalStopTime)}\n")
            sb.append("---------------------------\n\n")
        }

        historyTextView.text = sb.toString()
    }

    private fun deleteLastEntry() {
        val file = File(filesDir, "commute_log.csv")
        if (!file.exists()) {
            Toast.makeText(this, "No file found.", Toast.LENGTH_SHORT).show()
            return
        }

        val lines = file.readLines().toMutableList()
        if (lines.size <= 1) {
            Toast.makeText(this, "No entries to delete.", Toast.LENGTH_SHORT).show()
            return
        }

        lines.removeAt(lines.size - 1)

        file.writeText(lines.joinToString("\n"))

        Toast.makeText(this, "Last entry deleted.", Toast.LENGTH_SHORT).show()
        renderHistory()  // More efficient than recreate()
    }

    private fun parseCsvLine(line: String): List<String> {
        val regex = Regex(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")
        return line.split(regex).map { it.trim().trim('"') }
    }

    private fun formatTomin(secondsStr: String): String {
        val seconds = secondsStr.toIntOrNull() ?: return "-"
        val minutes = seconds / 60
        val remSeconds = seconds % 60
        return if (minutes > 0) "$minutes min ${remSeconds} sec" else "$remSeconds sec"
    }
}
