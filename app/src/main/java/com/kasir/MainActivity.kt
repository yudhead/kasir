package com.kasir

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.kasir.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupMenu()
        loadChartData()
    }

    private fun setupMenu() {
        binding.cardKasir.setOnClickListener {
            CartManager.mode = CartManager.MODE_BARU
            startActivity(Intent(this, KasirActivity::class.java))
        }

        binding.cardStok.setOnClickListener {
            startActivity(Intent(this, GudangActivity::class.java))
        }

        binding.cardRiwayat.setOnClickListener {
            startActivity(Intent(this, RiwayatActivity::class.java))
        }

        binding.cardHutang.setOnClickListener {
            startActivity(Intent(this, HutangActivity::class.java))
        }

        binding.btnTambahBarang.setOnClickListener {
            startActivity(Intent(this, TambahBarangActivity::class.java))
        }

        binding.btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun loadChartData() {
        Firebase.database.reference.child("transactions")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val monthlyRevenue = mutableMapOf<String, Float>()
                    val monthlyProfit = mutableMapOf<String, Float>()
                    
                    val sdf = SimpleDateFormat("MMM", Locale.getDefault())
                    val monthNames = mutableListOf<String>()
                    
                    for (i in 5 downTo 0) {
                        val tempCal = Calendar.getInstance()
                        tempCal.add(Calendar.MONTH, -i)
                        val monthName = sdf.format(tempCal.time)
                        monthNames.add(monthName)
                        monthlyRevenue[monthName] = 0f
                        monthlyProfit[monthName] = 0f
                    }

                    val currentMonthName = sdf.format(Date())

                    for (data in snapshot.children) {
                        val t = data.getValue(TransactionModel::class.java)
                        if (t != null && t.statusBayar == "LUNAS") {
                            val transCal = Calendar.getInstance()
                            transCal.timeInMillis = t.timestamp
                            val monthName = sdf.format(transCal.time)
                            
                            if (monthlyRevenue.containsKey(monthName)) {
                                val revenue = t.totalHarga.toFloat()
                                monthlyRevenue[monthName] = monthlyRevenue[monthName]!! + revenue
                                
                                // Hitung modal (COGS)
                                var totalHpp = 0f
                                t.items.forEach { item ->
                                    totalHpp += (item.product?.hargaBeli ?: 0) * item.quantity
                                }
                                val profit = revenue - totalHpp
                                monthlyProfit[monthName] = monthlyProfit[monthName]!! + profit
                            }
                        }
                    }

                    // Tampilkan ringkasan bulan ini
                    val kotorBulanIni = monthlyRevenue[currentMonthName]?.toInt() ?: 0
                    val bersihBulanIni = monthlyProfit[currentMonthName]?.toInt() ?: 0
                    
                    binding.tvKeuntunganKotor.text = "Keuntungan Kotor (Bulan Ini): ${FormatterUtil.formatRupiah(kotorBulanIni)}"
                    binding.tvKeuntunganBersih.text = "Keuntungan Bersih (Bulan Ini): ${FormatterUtil.formatRupiah(bersihBulanIni)}"

                    setupChart(monthNames, monthlyRevenue, monthlyProfit)
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun setupChart(months: List<String>, revenueMap: Map<String, Float>, profitMap: Map<String, Float>) {
        val textColor = ContextCompat.getColor(this, R.color.text_primary)
        
        val entriesRevenue = mutableListOf<BarEntry>()
        val entriesProfit = mutableListOf<BarEntry>()
        
        months.forEachIndexed { index, month ->
            entriesRevenue.add(BarEntry(index.toFloat(), revenueMap[month] ?: 0f))
            entriesProfit.add(BarEntry(index.toFloat(), profitMap[month] ?: 0f))
        }

        val setRevenue = BarDataSet(entriesRevenue, "Kotor")
        setRevenue.color = Color.parseColor("#3B82F6") // Blue
        setRevenue.setDrawValues(false)

        val setProfit = BarDataSet(entriesProfit, "Bersih")
        setProfit.color = Color.parseColor("#10B981") // Green
        setProfit.setDrawValues(false)

        val barData = BarData(setRevenue, setProfit)
        barData.barWidth = 0.35f
        
        binding.barChart.data = barData
        
        // Grouping
        val groupSpace = 0.1f
        val barSpace = 0.05f
        binding.barChart.groupBars(0f, groupSpace, barSpace)
        
        // Customizing Chart
        binding.barChart.xAxis.valueFormatter = IndexAxisValueFormatter(months)
        binding.barChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        binding.barChart.xAxis.setDrawGridLines(false)
        binding.barChart.xAxis.textColor = textColor
        binding.barChart.xAxis.granularity = 1f
        binding.barChart.xAxis.isGranularityEnabled = true
        binding.barChart.xAxis.setCenterAxisLabels(true)
        binding.barChart.xAxis.axisMinimum = 0f
        binding.barChart.xAxis.axisMaximum = months.size.toFloat()

        binding.barChart.axisLeft.setDrawGridLines(true)
        binding.barChart.axisLeft.gridColor = Color.parseColor("#E2E8F0")
        binding.barChart.axisLeft.textColor = textColor
        binding.barChart.axisLeft.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return FormatterUtil.formatRupiah(value.toInt())
            }
        }
        
        binding.barChart.axisRight.isEnabled = false
        binding.barChart.description.isEnabled = false
        
        binding.barChart.legend.isEnabled = true
        binding.barChart.legend.textColor = textColor

        binding.barChart.animateY(1000)
        binding.barChart.invalidate()
    }
}