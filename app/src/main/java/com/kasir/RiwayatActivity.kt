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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRiwayatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupCalendarFilter()
        setupSearch()
        loadData()

        binding.btnDownloadPdf.setOnClickListener {
            generatePdf()
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

                updateDateDisplay()
                applyFilters()
            }
            picker.show(supportFragmentManager, "DATE_RANGE_PICKER")
        }

        binding.btnResetFilter.setOnClickListener {
            startDate = null
            endDate = null
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

    private fun applyFilters() {
        val query = binding.etSearch.text.toString().lowercase()
        
        filteredList.clear()
        filteredList.addAll(listTransaksi.filter { t ->
            // 1. Filter Search
            val matchSearch = t.namaPembeli.lowercase().contains(query) || 
                             t.items.any { it.product?.namaBarang?.lowercase()?.contains(query) == true }
            
            // 2. Filter Date Range
            val matchDate = if (startDate != null && endDate != null) {
                t.timestamp in startDate!!..endDate!!
            } else {
                true
            }
            
            matchSearch && matchDate
        })

        updateListView()
    }

    private fun updateListView() {
        val displayList = filteredList.map { t ->
            val date = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(t.timestamp))
            "$date - ${t.namaPembeli} - ${FormatterUtil.formatRupiah(t.totalHarga)}"
        }

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayList)
        binding.lvRiwayat.adapter = adapter

        binding.lvRiwayat.setOnItemClickListener { _, _, position, _ ->
            val intent = Intent(this, DetailRiwayatActivity::class.java)
            intent.putExtra("ID", filteredList[position].id)
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