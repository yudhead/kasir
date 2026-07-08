package com.kasir

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.kasir.databinding.ActivityTambahBarangBinding
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.io.File
import java.io.FileOutputStream

class TambahBarangActivity : AppCompatActivity() {
    private lateinit var binding: ActivityTambahBarangBinding
    private lateinit var database: DatabaseReference
    private var imageUri: Uri? = null
    private var tempCameraUri: Uri? = null

    // Launcher Galeri
    private val pilihFotoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            imageUri = uri
            binding.ivFotoBarang.setImageURI(uri)
        }
    }

    // Launcher Kamera
    private val kameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempCameraUri != null) {
            imageUri = tempCameraUri
            binding.ivFotoBarang.setImageURI(imageUri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTambahBarangBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = Firebase.database.reference

        // Panggil fungsi untuk memuat daftar kategori saat halaman dibuka
        loadKategori()

        binding.btnPilihFoto.setOnClickListener {
            val options = arrayOf("Ambil Foto (Kamera)", "Pilih dari Galeri")
            AlertDialog.Builder(this)
                .setTitle("Pilih Sumber Foto")
                .setItems(options) { _, which ->
                    if (which == 0) cekIzinKamera() else pilihFotoLauncher.launch("image/*")
                }.show()
        }

        binding.btnSimpanBarang.setOnClickListener {
            val nama = binding.etNamaBarang.text.toString().trim()
            val kategori = binding.etKategori.text.toString().trim()
            val hargaBeliStr = binding.etHargaBeli.text.toString().trim()
            val hargaJualStr = binding.etHargaJual.text.toString().trim()
            val stokStr = binding.etStok.text.toString().trim()

            if (nama.isEmpty() || kategori.isEmpty() || imageUri == null) {
                Toast.makeText(this, "Nama, Kategori, dan Foto wajib diisi!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val hargaBeli = hargaBeliStr.toIntOrNull() ?: 0
            val hargaJual = hargaJualStr.toIntOrNull() ?: 0
            val stok = stokStr.toIntOrNull() ?: 0

            prosesSimpanData(nama, kategori, hargaBeli, hargaJual, stok)
        }
    }

    private fun loadKategori() {
        // Ambil data products dari Firebase untuk mencari kategori yang sudah ada
        database.child("products").get().addOnSuccessListener { snapshot ->
            val kategoriSet = mutableSetOf<String>()

            for (data in snapshot.children) {
                val kat = data.child("kategori").getValue(String::class.java)
                if (!kat.isNullOrEmpty()) {
                    kategoriSet.add(kat) // Set otomatis membuang duplikat
                }
            }

            val kategoriList = kategoriSet.toList()
            // Gunakan layout item dropdown yang support Material Theme
            val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, kategoriList)

            binding.etKategori.setAdapter(adapter)

            // Tampilkan dropdown saat user menyentuh inputan
            binding.etKategori.setOnClickListener {
                binding.etKategori.showDropDown()
            }
            binding.etKategori.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    binding.etKategori.showDropDown()
                }
            }
        }.addOnFailureListener {
            // Jika gagal load kategori, biarkan kosong (user masih bisa mengetik manual)
        }
    }

    private fun cekIzinKamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
        } else {
            bukaKamera()
        }
    }

    private fun bukaKamera() {
        val values = ContentValues()
        values.put(MediaStore.Images.Media.TITLE, "Foto Baru")
        tempCameraUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        tempCameraUri?.let {
            kameraLauncher.launch(it)
        }
    }

    private fun prosesSimpanData(nama: String, kategori: String, hargaBeli: Int, hargaJual: Int, stok: Int) {
        binding.btnSimpanBarang.isEnabled = false
        binding.btnSimpanBarang.text = "Menyimpan Data..."

        val localImagePath = simpanFotoKeInternal(imageUri!!)

        if (localImagePath != null) {
            simpanKeRealtimeDatabase(nama, kategori, hargaBeli, hargaJual, stok, localImagePath)
        } else {
            Toast.makeText(this, "Gagal memproses foto", Toast.LENGTH_SHORT).show()
            binding.btnSimpanBarang.isEnabled = true
            binding.btnSimpanBarang.text = "Simpan ke Gudang"
        }
    }

    private fun simpanFotoKeInternal(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val fileName = "PRODUK_${System.currentTimeMillis()}.jpg"
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

    private fun simpanKeRealtimeDatabase(nama: String, kategori: String, hargaBeli: Int, hargaJual: Int, stok: Int, imagePath: String) {
        val productId = database.child("products").push().key

        if (productId != null) {
            val productBaru = Product(
                id = productId,
                namaBarang = nama,
                kategori = kategori,
                hargaBeli = hargaBeli,
                hargaJual = hargaJual,
                harga = hargaJual,
                stok = stok,
                imageUrl = imagePath
            )

            database.child("products").child(productId).setValue(productBaru)
                .addOnSuccessListener {
                    Toast.makeText(this, "Barang berhasil ditambahkan!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Gagal menyimpan: ${e.message}", Toast.LENGTH_LONG).show()
                    binding.btnSimpanBarang.isEnabled = true
                    binding.btnSimpanBarang.text = "Simpan ke Gudang"
                }
        }
    }
}