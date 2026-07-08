package com.kasir

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.kasir.databinding.ActivityCheckoutBinding
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class CheckoutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCheckoutBinding
    private lateinit var database: DatabaseReference
    private var imageUri: Uri? = null

    // Variabel untuk menyimpan transaksi sementara sebelum dicetak
    private var transaksiTerakhir: TransactionModel? = null

    private val pilihFotoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            imageUri = uri
            binding.ivBuktiBayar.setImageURI(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCheckoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = Firebase.database.reference

        // Setup Tampilan...
        var ringkasan = ""
        for (item in CartManager.keranjang) {
            val subtotal = item.quantity * (item.product?.harga ?: 0)
            ringkasan += "${item.quantity}x ${item.product?.namaBarang} = ${FormatterUtil.formatRupiah(subtotal)}\n"
        }
        binding.tvRingkasanBelanja.text = ringkasan
        binding.tvCheckoutTotal.text = "TOTAL: ${FormatterUtil.formatRupiah(CartManager.totalHarga)}"

        if (CartManager.mode == CartManager.MODE_PELUNASAN) {
            binding.etNamaPembeli.setText(CartManager.namaPembeli)
            binding.etNamaPembeli.isEnabled = false
        }

        binding.btnUploadBukti.setOnClickListener {
            pilihFotoLauncher.launch("image/*")
        }

        binding.btnProsesBayar.setOnClickListener {
            val metode = when {
                binding.rbCash.isChecked -> "Cash"
                binding.rbTransfer.isChecked -> "Transfer"
                else -> "Hutang"
            }

            if (binding.rbTransfer.isChecked && imageUri == null) {
                Toast.makeText(this, "Bukti transfer wajib dilampirkan!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (binding.rbHutang.isChecked && binding.etNamaPembeli.text.toString().isEmpty()) {
                Toast.makeText(this, "Nama Pembeli wajib diisi untuk Hutang!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prosesSimpanTransaksi(metode)
        }
    }

    private fun prosesSimpanTransaksi(metode: String) {
        binding.btnProsesBayar.isEnabled = false
        binding.btnProsesBayar.text = "Memproses..."

        var buktiPath = ""
        if (imageUri != null) {
            buktiPath = simpanFotoKeInternal(imageUri!!) ?: ""
        }

        val transactionId = if (CartManager.mode == CartManager.MODE_PELUNASAN) CartManager.transaksiId else database.child("transactions").push().key ?: ""
        val status = if (binding.rbHutang.isChecked) "BELUM_BAYAR" else "LUNAS"
        val nama = binding.etNamaPembeli.text.toString()

        val transaksiBaru = TransactionModel(
            id = transactionId,
            namaPembeli = nama,
            totalHarga = CartManager.totalHarga,
            timestamp = System.currentTimeMillis(),
            items = CartManager.keranjang.toList(), // Copy list
            statusBayar = status,
            metodePembayaran = metode,
            buktiPembayaranPath = buktiPath
        )

        // Simpan transaksi
        database.child("transactions").child(transactionId).setValue(transaksiBaru)
            .addOnSuccessListener {
                if (status == "LUNAS") {
                    potongStokGudang()
                }

                transaksiTerakhir = transaksiBaru
                tampilkanDialogCetakStruk() // MUNCULKAN DIALOG CETAK
            }
            .addOnFailureListener {
                Toast.makeText(this, "Gagal memproses transaksi", Toast.LENGTH_SHORT).show()
                binding.btnProsesBayar.isEnabled = true
                binding.btnProsesBayar.text = "Selesaikan Pembayaran"
            }
    }

    // ==========================================
    // LOGIKA CETAK STRUK BLUETOOTH
    // ==========================================

    private fun tampilkanDialogCetakStruk() {
        AlertDialog.Builder(this)
            .setTitle("Pembayaran Berhasil!")
            .setMessage("Apakah Anda ingin mencetak struk belanja?")
            .setPositiveButton("Cetak Struk") { _, _ ->
                cekIzinBluetoothDanCetak()
            }
            .setNegativeButton("Tutup") { _, _ ->
                selesaiDanTutup()
            }
            .setCancelable(false)
            .show()
    }

    private fun cekIzinBluetoothDanCetak() {
        // Khusus Android 12+, butuh izin BLUETOOTH_CONNECT saat runtime
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 101)
                return
            }
        }
        mulaiPilihPrinter()
    }

    @SuppressLint("MissingPermission")
    private fun mulaiPilihPrinter() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Perangkat ini tidak memiliki Bluetooth", Toast.LENGTH_SHORT).show()
            selesaiDanTutup()
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            // Jika Bluetooth mati, jangan langsung ditutup! Munculkan dialog coba lagi
            dialogGagalCetak("Bluetooth belum aktif. Aktifkan di pengaturan HP Anda lalu coba lagi.")
            return
        }

        val pairedDevices = bluetoothAdapter.bondedDevices
        if (pairedDevices.isEmpty()) {
            // Jika belum ada printer, beri kesempatan user untuk pairing tanpa kehilangan transaksi
            dialogGagalCetak("Belum ada printer tersimpan. Pairing printer di Pengaturan Bluetooth HP Anda lalu coba lagi.")
            return
        }

        val listDevice = pairedDevices.toList()
        val listNamaDevice = listDevice.map { it.name }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Pilih Printer Thermal")
            .setItems(listNamaDevice) { _, which ->
                val deviceDipilih = listDevice[which]
                kirimDataKePrinter(deviceDipilih)
            }
            .setNegativeButton("Lewati & Selesai") { _, _ -> selesaiDanTutup() }
            .setCancelable(false)
            .show()
    }

    // FUNGSI BARU: Untuk menahan layar agar tidak langsung pindah saat gagal
    private fun dialogGagalCetak(pesan: String) {
        AlertDialog.Builder(this)
            .setTitle("Gagal Mencetak")
            .setMessage(pesan)
            .setPositiveButton("Coba Lagi") { _, _ ->
                cekIzinBluetoothDanCetak() // Ulangi proses
            }
            .setNegativeButton("Selesai (Tanpa Struk)") { _, _ ->
                selesaiDanTutup()
            }
            .setCancelable(false)
            .show()
    }

    @SuppressLint("MissingPermission")
    private fun kirimDataKePrinter(device: BluetoothDevice) {
        val t = transaksiTerakhir ?: return

        // Ubah tombol sementara saat proses print
        runOnUiThread {
            Toast.makeText(this, "Menyambungkan ke printer...", Toast.LENGTH_SHORT).show()
        }

        Thread {
            try {
                val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                val socket = device.createRfcommSocketToServiceRecord(uuid)
                socket.connect()

                val outputStream = socket.outputStream

                // ----- FORMAT STRUK (ESC/POS) -----
                val struk = java.lang.StringBuilder()
                struk.append("\n")
                struk.append("           JAYATRI KEDIRI          \n")
                struk.append("===============================\n")
                struk.append("Pelanggan : ${if(t.namaPembeli.isEmpty()) "Umum" else t.namaPembeli}\n")
                struk.append("Metode    : ${t.metodePembayaran}\n")
                struk.append("Status    : ${t.statusBayar}\n")
                struk.append("-------------------------------\n")

                for (item in t.items) {
                    val namaBarang = item.product?.namaBarang ?: ""
                    val qty = item.quantity
                    val harga = item.product?.harga ?: 0
                    val subtotal = qty * harga

                    struk.append("$namaBarang\n")
                    struk.append("   $qty x Rp $harga = Rp $subtotal\n")
                }

                struk.append("-------------------------------\n")
                struk.append("TOTAL : Rp ${t.totalHarga}\n")
                struk.append("===============================\n")
                struk.append("          Terima Kasih         \n")
                struk.append("\n\n\n")

                outputStream.write(struk.toString().toByteArray())
                outputStream.flush()
                socket.close()

                runOnUiThread {
                    Toast.makeText(this, "Berhasil mencetak struk!", Toast.LENGTH_SHORT).show()
                    selesaiDanTutup()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                // JIKA GAGAL: Jangan panggil selesaiDanTutup(), melainkan panggil dialogGagalCetak
                runOnUiThread {
                    dialogGagalCetak("Gagal terhubung ke printer. Pastikan printer menyala, jarak dekat, dan tidak sedang dipakai perangkat lain.")
                }
            }
        }.start()
    }

    // ==========================================

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            mulaiPilihPrinter() // Lanjut pilih printer setelah diizinkan
        } else {
            Toast.makeText(this, "Izin Bluetooth ditolak", Toast.LENGTH_SHORT).show()
            selesaiDanTutup()
        }
    }

    private fun selesaiDanTutup() {
        CartManager.bersihkanKeranjang()
        if (CartManager.mode == CartManager.MODE_PELUNASAN) {
            val intent = Intent(this, HutangActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }
        finish()
    }

    // ... (Fungsi potongStokGudang dan simpanFotoKeInternal Anda tetap biarkan sama)
    private fun potongStokGudang() {
        for (item in CartManager.keranjang) {
            val produkId = item.product?.id
            val quantity = item.quantity
            if (produkId != null) {
                database.child("products").child(produkId).child("stok").get().addOnSuccessListener { snapshot ->
                    val currentStok = snapshot.getValue(Int::class.java) ?: 0
                    database.child("products").child(produkId).child("stok").setValue(currentStok - quantity)
                }
            }
        }
    }

    private fun simpanFotoKeInternal(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val fileName = "BUKTI_BAYAR_${System.currentTimeMillis()}.jpg"
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