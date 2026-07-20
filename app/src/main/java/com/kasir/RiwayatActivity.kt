package com.kasir

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.Pair
import com.google.android.material.datepicker.MaterialDatePicker
import com.kasir.databinding.ActivityRiwayatBinding
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.UnitValue
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class RiwayatActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRiwayatBinding
    private val listTransaksi = mutableListOf<TransactionModel>()
    private val filteredList = mutableListOf<TransactionModel>()
    
    private var startDate: Long? = null
    private var endDate: Long? = null
    private var selectedMetode = "Semua"
    private var presetFilter = "Semua"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRiwayatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupCalendarFilter()
        setupPresetFilters()
        setupSearch()
        setupMetodeFilter()
        loadData()

        binding.btnDownloadPdf.setOnClickListener {
            generatePdf()
        }
    }

    private fun setupPresetFilters() {
        binding.chipGroupFilter.setOnCheckedStateChangeListener { group, checkedIds ->
            presetFilter = when (checkedIds.firstOrNull()) {
                binding.chipMingguIni.id -> "Minggu"
                binding.chipBulanIni.id -> "Bulan"
                binding.chipTahunIni.id -> "Tahun"
                else -> "Semua"
            }
            
            if (presetFilter != "Semua") {
                // Reset custom date picker when preset is chosen
                startDate = null
                endDate = null
                updateDateDisplay()
            }
            applyFilters()
        }
    }

    private fun setupCalendarFilter() {
        binding.btnPickDate.setOnClickListener {
            val builder = MaterialDatePicker.Builder.dateRangePicker()
            builder.setTitleText("Pilih Rentang Tanggal")
            
            // Set default selection if exists
            if (startDate != null && endDate != null) {
                builder.setSelection(Pair(startDate, endDate))
            }

            val picker = builder.build()
            picker.addOnPositiveButtonClickListener { selection ->
                // Adjust start date to 00:00:00.000 in local timezone
                val startCal = Calendar.getInstance()
                startCal.timeInMillis = selection.first
                startCal.set(Calendar.HOUR_OF_DAY, 0)
                startCal.set(Calendar.MINUTE, 0)
                startCal.set(Calendar.SECOND, 0)
                startCal.set(Calendar.MILLISECOND, 0)
                startDate = startCal.timeInMillis
                
                // Adjust end date to 23:59:59.999 in local timezone
                val endCal = Calendar.getInstance()
                endCal.timeInMillis = selection.second
                endCal.set(Calendar.HOUR_OF_DAY, 23)
                endCal.set(Calendar.MINUTE, 59)
                endCal.set(Calendar.SECOND, 59)
                endCal.set(Calendar.MILLISECOND, 999)
                endDate = endCal.timeInMillis

                // Reset preset filter when custom date is picked
                binding.chipSemua.isChecked = true
                presetFilter = "Semua"

                updateDateDisplay()
                applyFilters()
            }
            picker.show(supportFragmentManager, "DATE_RANGE_PICKER")
        }

        binding.btnResetFilter.setOnClickListener {
            startDate = null
            endDate = null
            binding.chipSemua.isChecked = true
            presetFilter = "Semua"
            updateDateDisplay()
            applyFilters()
        }
    }

    private fun updateDateDisplay() {
        if (startDate != null && endDate != null) {
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val display = "${sdf.format(Date(startDate!!))} - ${sdf.format(Date(endDate!!))}"
            binding.tvSelectedDate.text = display
            binding.btnResetFilter.visibility = View.VISIBLE
        } else {
            binding.tvSelectedDate.text = "Semua Waktu"
            binding.btnResetFilter.visibility = View.GONE
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadData() {
        Firebase.database.reference.child("transactions").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                listTransaksi.clear()
                for (data in snapshot.children) {
                    val t = data.getValue(TransactionModel::class.java)
                    if (t != null) {
                        listTransaksi.add(t)
                    }
                }
                listTransaksi.sortByDescending { it.timestamp }
                applyFilters()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun setupMetodeFilter() {
        val metodes = listOf("Semua", "Cash", "Transfer", "Bayar Nanti")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, metodes)
        binding.actvFilterMetode.setAdapter(adapter)

        binding.actvFilterMetode.setOnItemClickListener { parent, _, position, _ ->
            selectedMetode = parent.getItemAtPosition(position).toString()
            applyFilters()
        }
    }

    private fun applyFilters() {
        val query = binding.etSearch.text.toString().lowercase()
        
        filteredList.clear()
        filteredList.addAll(listTransaksi.filter { t ->
            // 1. Filter Search
            val matchSearch = t.namaPembeli.lowercase().contains(query) || 
                             t.items.any { it.product?.namaBarang?.lowercase()?.contains(query) == true }
            
            // 2. Filter Date Range (Custom Picker or Preset)
            val matchDate = when {
                startDate != null && endDate != null -> {
                    t.timestamp in startDate!!..endDate!!
                }
                presetFilter == "Minggu" -> {
                    val cal = Calendar.getInstance()
                    val now = cal.timeInMillis
                    cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    val startOfWeek = cal.timeInMillis
                    t.timestamp in startOfWeek..now
                }
                presetFilter == "Bulan" -> {
                    val cal = Calendar.getInstance()
                    val now = cal.timeInMillis
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    val startOfMonth = cal.timeInMillis
                    t.timestamp in startOfMonth..now
                }
                presetFilter == "Tahun" -> {
                    val cal = Calendar.getInstance()
                    val now = cal.timeInMillis
                    cal.set(Calendar.DAY_OF_YEAR, 1)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    val startOfYear = cal.timeInMillis
                    t.timestamp in startOfYear..now
                }
                else -> true
            }

            // 3. Filter Metode Pembayaran
            val matchMetode = when (selectedMetode) {
                "Semua" -> true
                "Cash" -> t.metodePembayaran == "Cash"
                "Transfer" -> t.metodePembayaran == "Transfer"
                "Bayar Nanti" -> t.metodePembayaran == "Bayar Nanti"
                else -> true
            }
            
            matchSearch && matchDate && matchMetode
        })

        updateListView()
    }

    private fun updateListView() {
        val displayList = filteredList.map { t ->
            val date = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(t.timestamp))
            val deletedTag = if (t.deleted) " [DIHAPUS]" else ""
            "$date - ${t.namaPembeli}$deletedTag - ${FormatterUtil.formatRupiah(t.totalHarga)}"
        }

        val adapter = ArrayAdapter(this, R.layout.item_riwayat_list, displayList)
        binding.lvRiwayat.adapter = adapter

        // Update Total pada UI sesuai filter
        val total = filteredList.sumOf { it.totalHarga }
        binding.tvTotalRiwayat.text = "Total: ${FormatterUtil.formatRupiah(total)}"

        binding.lvRiwayat.setOnItemClickListener { _, _, position, _ ->
            val transaksi = filteredList[position]
            val intent = Intent(this, DetailRiwayatActivity::class.java)
            intent.putExtra("ID", transaksi.id)
            startActivity(intent)
        }
    }

    private fun generatePdf() {
        if (filteredList.isEmpty()) {
            Toast.makeText(this, "Tidak ada data untuk diunduh", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val fileName = "Laporan_Transaksi_${System.currentTimeMillis()}.pdf"
            // Simpan ke folder Downloads publik agar mudah ditemukan
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val filePath = File(downloadsDir, fileName)
            
            val writer = PdfWriter(FileOutputStream(filePath))
            val pdf = PdfDocument(writer)
            val document = Document(pdf)

            document.add(Paragraph("Laporan Transaksi Kasir").setBold().setFontSize(18f))
            
            val filterText = if (startDate != null && endDate != null) {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                "${sdf.format(Date(startDate!!))} s/d ${sdf.format(Date(endDate!!))}"
            } else {
                "Semua Waktu"
            }
            
            document.add(Paragraph("Rentang Waktu: $filterText"))
            document.add(Paragraph("Tanggal Cetak: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}"))
            document.add(Paragraph("\n"))

            val table = Table(UnitValue.createPointArray(floatArrayOf(100f, 150f, 100f, 100f)))
            table.width = UnitValue.createPercentValue(100f)
            
            table.addHeaderCell("Waktu")
            table.addHeaderCell("Barang")
            table.addHeaderCell("Pembeli")
            table.addHeaderCell("Total")

            for (t in filteredList) {
                val date = SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(Date(t.timestamp))
                table.addCell(date)
                
                val itemsStr = t.items.joinToString("\n") { 
                    val unitPrice = it.product?.hargaJual ?: 0
                    "${it.product?.namaBarang} (${it.quantity}x @${FormatterUtil.formatRupiah(unitPrice)})" 
                }
                table.addCell(itemsStr)
                
                table.addCell(t.namaPembeli)
                table.addCell(FormatterUtil.formatRupiah(t.totalHarga))
            }

            document.add(table)

            // Tambahkan Total Keseluruhan di bawah tabel
            val grandTotal = filteredList.sumOf { it.totalHarga }
            document.add(Paragraph("\n"))
            document.add(Paragraph("TOTAL KESELURUHAN: ${FormatterUtil.formatRupiah(grandTotal)}")
                .setBold()
                .setFontSize(14f)
                .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.RIGHT))

            document.close()

            // Beritahu sistem bahwa ada file baru agar muncul di File Manager/Gallery
            android.media.MediaScannerConnection.scanFile(
                this,
                arrayOf(filePath.absolutePath),
                arrayOf("application/pdf"),
                null
            )

            Toast.makeText(this, "PDF berhasil disimpan di folder Download: $fileName", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Gagal membuat PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}