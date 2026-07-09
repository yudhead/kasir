package com.kasir

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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

        if (t.items.isEmpty()) {

            hapusTransaksi(t.id)

            return

        }

        var selesai = 0

        t.items.forEach { item ->

            val produkId = item.product?.id ?: return@forEach

            db.child("products")
                .child(produkId)
                .child("stok")
                .get()
                .addOnSuccessListener { snapshot ->

                    val stokSekarang =
                        snapshot.getValue(Int::class.java) ?: 0

                    val stokBaru =
                        stokSekarang + item.quantity

                    db.child("products")
                        .child(produkId)
                        .child("stok")
                        .setValue(stokBaru)
                        .addOnSuccessListener {

                            selesai++

                            if (selesai == t.items.size) {

                                hapusTransaksi(t.id)

                            }

                        }

                }

        }

    }

    private fun hapusTransaksi(id: String) {

        db.child("transactions")
            .child(id)
            .removeValue()
            .addOnSuccessListener {

                Toast.makeText(
                    this,
                    "Bayar Nanti berhasil dihapus",
                    Toast.LENGTH_SHORT
                ).show()

                finish()

            }
            .addOnFailureListener {

                Toast.makeText(
                    this,
                    "Gagal menghapus",
                    Toast.LENGTH_SHORT
                ).show()

            }

    }


    private fun loadData() {

        db.child("transactions")
            .child(transaksiId)
            .get()
            .addOnSuccessListener { snapshot ->

                transaksi =
                    snapshot.getValue(TransactionModel::class.java)

                transaksi?.let {

                    tampilkanData(it)

                }

            }

    }

    private fun tampilkanData(t: TransactionModel) {

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

            CartManager.oldItems.clear()
            CartManager.oldItems.addAll(t.items)

            CartManager.keranjang.addAll(t.items)

            CartManager.totalHarga = t.totalHarga

            CartManager.transaksiId = t.id

            CartManager.namaPembeli = t.namaPembeli

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