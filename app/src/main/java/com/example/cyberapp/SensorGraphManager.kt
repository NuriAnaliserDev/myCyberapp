package com.example.cyberapp

import android.graphics.Color
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet

class SensorGraphManager(private val lineChart: LineChart) {

    init {
        setupChart()
    }

    private fun setupChart() {
        lineChart.apply {
            description.isEnabled = false
            setTouchEnabled(false)
            isDragEnabled = false
            setScaleEnabled(false)
            setDrawGridBackground(false)
            setPinchZoom(false)
            setBackgroundColor(Color.TRANSPARENT)
            
            axisLeft.apply {
                textColor = Color.parseColor("#00E5FF")
                setDrawGridLines(false)
                axisMaximum = 20f
                axisMinimum = -20f
            }
            
            axisRight.isEnabled = false
            
            xAxis.isEnabled = false
            
            legend.isEnabled = false
        }

        val data = LineData()
        data.setValueTextColor(Color.WHITE)
        lineChart.data = data
    }

    fun addEntry(value: Float) {
        val data = lineChart.data
        if (data != null) {
            var set = data.getDataSetByIndex(0)
            if (set == null) {
                set = createSet()
                data.addDataSet(set)
            }

            data.addEntry(Entry(set.entryCount.toFloat(), value), 0)
            data.notifyDataChanged()

            lineChart.notifyDataSetChanged()
            lineChart.setVisibleXRangeMaximum(50f)
            lineChart.moveViewToX(data.entryCount.toFloat())
        }
    }

    private fun createSet(): LineDataSet {
        val set = LineDataSet(null, "Sensor Data")
        set.apply {
            axisDependency = com.github.mikephil.charting.components.YAxis.AxisDependency.LEFT
            color = Color.parseColor("#D500F9") // Neon Purple
            setCircleColor(Color.parseColor("#00E5FF")) // Neon Cyan
            lineWidth = 2f
            circleRadius = 3f
            fillAlpha = 65
            fillColor = Color.parseColor("#D500F9")
            highLightColor = Color.rgb(244, 117, 117)
            valueTextColor = Color.WHITE
            valueTextSize = 9f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }
        return set
    }
}
