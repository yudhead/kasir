package com.kasir

import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.kasir.databinding.ActivityEditBarangBinding
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.io.File
import java.io.FileOutputStream

class EditBarangActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditBarangBinding
    private lateinit var database: DatabaseReference
    private var imageUri: Uri? = null

    private var productId: String = ""
    private var oldImagePath: String = ""

    private val pilihFotoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            imageUri = uri
            binding.ivEditFotoBarang.setImageURI(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditBarangBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = Firebase.database.reference

        productId = intent.getStringExtra("EXTRA_ID") ?: ""
        
        loadProductData()
        loadKategori()

        binding.btnEditPilihFoto.setOnClickListener {
            pilihFotoLauncher.launch("image/*")
        }

        binding.ivEditFotoBarang.setOnClickListener {
            val source = imageUri ?: if (oldImagePath.isNotEmpty()) oldImagePath else null
            if (source != null) {
                ImageDialogUtil.showImageDialog(this, source)
            }
        }

        binding.btnUpdateBarang.setOnClickListener {
            val namaBaru = binding.etEditNamaBarang.text.toString().trim()
            val kategoriBaru = binding.etEditKategori.text.toString().trim()
            val hargaBeliStr = binding.etEditHargaBeli.text.toString().trim()
            val hargaJualStr = binding.etEditHargaJual.text.toString().trim()
            val stokStr = binding.etEditStok.text.toString().trim()

            if (namaBaru.isEmpty() || kategoriBaru.isEmpty()) {
                Toast.makeText(this, "Nama dan Kategori wajib diisi!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prosesUpdateData(
                namaBaru, 
                kategoriBaru, 
                hargaBeliStr.toIntOrNull() ?: 0, 
                hargaJualStr.toIntOrNull() ?: 0, 
                stokStr.toIntOrNull() ?: 0
            )
        }

        binding.btnHapusBarang.setOnClickListener {
            tampilkanDialogHapus()
        }
    }

    private fun loadProductData() {
        database.child("products").child(productId).get().addOnSuccessListener { snapshot ->
            val product = snapshot.getValue(Product::class.java)
            if (product != null) {
                binding.etEditNamaBarang.setText(product.namaBarang)
                binding.etEditKategori.setText(product.kategori, false)
                
                // Jika nilai 0, biarkan kosong agar hint "0" muncul (abu-abu)
                binding.etEditHargaBeli.setText(if (product.hargaBeli == 0) "" else product.hargaBeli.toString())
                binding.etEditHargaJual.setText(if (product.hargaJual == 0) "" else product.hargaJual.toString())
                binding.etEditStok.setText(if (product.stok == 0) "" else product.stok.toString())

                oldImagePath = product.imageUrl

                Glide.with(this)
                    .load(oldImagePath)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(binding.ivEditFotoBarang)
            }
        }
    }

    private fun loadKategori() {
        database.child("products").get().addOnSuccessListener { snapshot ->
            val kategoriSet = mutableSetOf<String>()
            for (data in snapshot.children) {
                val kat = data.child("kategori").getValue(String::class.java)
                if (!kat.isNullOrEmpty()) {
                    kategoriSet.add(kat)
                }
            }
            val kategoriList = kategoriSet.toList()
            val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, kategoriList)
            binding.etEditKategori.setAdapter(adapter)

            binding.etEditKategori.setOnClickListener {
                binding.etEditKategori.showDropDown()
            }
            binding.etEditKategori.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    binding.etEditKategori.showDropDown()
                }
            }
        }
    }

    private fun tampilkanDialogHapus() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Hapus Barang")
            .setMessage("Apakah Anda yakin ingin menghapus barang ini secara permanen?")
            .setPositiveButton("Ya, Hapus") { _, _ ->
                prosesHapusBarang()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun prosesHapusBarang() {
        binding.btnHapusBarang.isEnabled = false
        binding.btnHapusBarang.text = "Menghapus..."

        database.child("products").child(productId).removeValue()
            .addOnSuccessListener {
                if (oldImagePath.isNotEmpty()) {
                    val fileFoto = File(oldImagePath)
                    if (fileFoto.exists()) {
                        fileFoto.delete()
                    }
                }
                Toast.makeText(this, "Barang berhasil dihapus!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal menghapus: ${e.message}", Toast.LENGTH_LONG).show()
                binding.btnHapusBarang.isEnabled = true
                binding.btnHapusBarang.text = "Hapus Barang"
            }
    }

    private fun prosesUpdateData(nama: String, kategori: String, hargaBeli: Int, hargaJual: Int, stok: Int) {
        binding.btnUpdateBarang.isEnabled = false
        binding.btnUpdateBarang.text = "Mengupdate Data..."

        val finalImagePath = if (imageUri != null) {
            simpanFotoKeInternal(imageUri!!) ?: oldImagePath
        } else {
            oldImagePath
        }

        val updatedProduct = Product(
            id = productId, 
            namaBarang = nama, 
            kategori = kategori,
            hargaBeli = hargaBeli,
            hargaJual = hargaJual,
            harga = hargaJual,
            stok = stok, 
            imageUrl = finalImagePath
        )

        database.child("products").child(productId).setValue(updatedProduct)
            .addOnSuccessListener {
                Toast.makeText(this, "Data berhasil diupdate!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal update: ${e.message}", Toast.LENGTH_LONG).show()
                binding.btnUpdateBarang.isEnabled = true
                binding.btnUpdateBarang.text = "Update Data Barang"
            }
    }

    private fun simpanFotoKeInternal(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val fileName = "PRODUK_UPDATE_${System.currentTimeMillis()}.jpg"
            val file = File(filesDir, fileName)
            val outputStream = FileOutputStream(file)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}