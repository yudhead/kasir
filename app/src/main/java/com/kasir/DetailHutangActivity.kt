package com.kasir

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.kasir.databinding.ActivityDetailHutangBinding

class DetailBayarNantiActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailHutangBinding
    private val db = Firebase.database.reference

    private var transaksi: TransactionModel? = null
    private var transaksiId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDetailHutangBinding.inflate(layoutInflater)
        setContentView(binding.root)

        transaksiId = intent.getStringExtra("ID") ?: ""

        loadData()
    }

    private fun kembalikanStok(t: TransactionModel) {
        // Soft delete: tandai sebagai dihapus agar tetap ada di riwayat
        // Gunakan key 'deleted' untuk menghindari masalah prefix 'is'
        val update = mapOf("deleted" to true)
        db.child("transactions")
            .child(transaksiId) // Gunakan ID dari intent yang pasti benar
            .updateChildren(update)
            .addOnSuccessListener {
                Toast.makeText(this, "Berhasil dihapus dari daftar aktif.", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal menghapus.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadData() {
        db.child("transactions")
            .child(transaksiId)
            .get()
            .addOnSuccessListener { snapshot ->
                transaksi = snapshot.getValue(TransactionModel::class.java)
                transaksi?.let {
                    if (it.deleted) {
                        tampilkanWarningDihapus(it)
                    } else {
                        tampilkanData(it)
                    }
                }
            }
    }

    private fun tampilkanWarningDihapus(t: TransactionModel) {
        // Jika dibuka lewat sini (DetailBayarNanti), langsung arahkan ke riwayat saja karena ini daftar aktif
        Toast.makeText(this, "Data ini sudah dihapus. Silakan cek di menu Riwayat.", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun tampilkanData(t: TransactionModel) {
        binding.tvDetailInfo.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        binding.btnHapus.text = "Hapus Data Bayar Nanti"
        binding.btnHapus.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#E74C3C"))

        binding.tvDetailInfo.text =
            "Pembeli : ${t.namaPembeli}\n\nTotal : ${FormatterUtil.formatRupiah(t.totalHarga)}"

        val builder = StringBuilder()

        t.items.forEach {

            builder.append(
                "${it.product?.namaBarang} (${it.quantity}x)\n"
            )

        }

        binding.tvDetailItems.text = builder.toString()

        //-------------------------------
        // UPDATE BARANG
        //-------------------------------

        binding.btnUpdateBarang.setOnClickListener {
            CartManager.bersihkanKeranjang()
            // Gunakan .map { it.copy() } agar tidak berbagi referensi objek yang sama
            CartManager.oldItems.addAll(t.items.map { it.copy() })
            CartManager.keranjang.addAll(t.items.map { it.copy() })
            CartManager.totalHarga = t.totalHarga
            CartManager.transaksiId = t.id
            CartManager.namaPembeli = t.namaPembeli
            CartManager.nomorStruk = t.nomorStruk
            CartManager.mode = CartManager.MODE_EDIT
            startActivity(Intent(this, KasirActivity::class.java))
        }

        //-------------------------------
        // LANJUT PEMBAYARAN
        //-------------------------------

        binding.btnLanjutPembayaran.setOnClickListener {
            CartManager.mode = CartManager.MODE_PELUNASAN
            CartManager.transaksiId = t.id
            CartManager.namaPembeli = t.namaPembeli
            CartManager.nomorStruk = t.nomorStruk
            CartManager.keranjang.clear()
            CartManager.keranjang.addAll(t.items)
            CartManager.totalHarga = t.totalHarga

            val intent = Intent(this, CheckoutActivity::class.java)
            // Gunakan CLEAR_TOP agar setelah checkout selesai,
            // semua activity di bawahnya (termasuk Detail) dibersihkan
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }

        //-------------------------------
        // HAPUS BAYAR NANTI
        //-------------------------------

        binding.btnHapus.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#E74C3C"))
        binding.btnHapus.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Hapus Bayar Nanti")
                .setMessage("Yakin ingin menghapus transaksi ini?")
                .setPositiveButton("Ya") { _, _ ->
                    kembalikanStok(t)
                }
                .setNegativeButton("Batal", null)
                .show()
        }
    }
}