package com.kasir

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.kasir.databinding.ActivityDetailRiwayatBinding
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.io.File

class DetailRiwayatActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDetailRiwayatBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailRiwayatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val id = intent.getStringExtra("ID")
        Firebase.database.reference.child("transactions").child(id!!).get().addOnSuccessListener {
            val t = it.getValue(TransactionModel::class.java)
            if (t != null) {
                val sdf = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
                val dateStr = sdf.format(java.util.Date(t.timestamp))
                
                binding.tvDetailInfo.text = "Pembeli: ${t.namaPembeli}\nTanggal: $dateStr\nMetode: ${t.metodePembayaran}\nStatus: ${t.statusBayar}\n\nTOTAL AKHIR: ${FormatterUtil.formatRupiah(t.totalHarga)}"

                var itemsText = ""
                for (item in t.items) {
                    val unitPrice = item.product?.hargaJual ?: 0
                    val subTotal = unitPrice * item.quantity
                    itemsText += "${item.product?.namaBarang}\n"
                    itemsText += "  ${item.quantity} x ${FormatterUtil.formatRupiah(unitPrice)} = ${FormatterUtil.formatRupiah(subTotal)}\n\n"
                }
                binding.tvDetailItems.text = itemsText.trim()

                if (t.buktiPembayaranPath.isNotEmpty()) {
                    val file = File(t.buktiPembayaranPath)
                    if (file.exists()) {
                        binding.ivDetailBukti.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
                    }
                }
            }
        }
    }
}