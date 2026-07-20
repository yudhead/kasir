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
    private var tempCameraUri: Uri? = null

    // Variabel untuk menyimpan transaksi sementara sebelum dicetak
    private var transaksiTerakhir: TransactionModel? = null

    // Launcher Kamera
    private val kameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempCameraUri != null) {
            imageUri = tempCameraUri
            binding.ivBuktiBayar.setImageURI(imageUri)
            binding.ivBuktiBayar.imageTintList = null // Hapus tint agar foto asli kelihatan
        }
    }

    // Launcher Galeri (Baru)
    private val galeriLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            imageUri = uri
            binding.ivBuktiBayar.setImageURI(imageUri)
            binding.ivBuktiBayar.imageTintList = null
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

        // Awal load: Sembunyikan bagian upload jika defaultnya Cash
        binding.btnUploadBukti.visibility = android.view.View.GONE
        binding.ivBuktiBayar.visibility = android.view.View.GONE

        // Listener perubahan metode pembayaran
        binding.rgMetodePembayaran.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbTransfer) {
                binding.btnUploadBukti.visibility = android.view.View.VISIBLE
                binding.ivBuktiBayar.visibility = android.view.View.VISIBLE
            } else {
                binding.btnUploadBukti.visibility = android.view.View.GONE
                binding.ivBuktiBayar.visibility = android.view.View.GONE
                // Opsional: Hapus imageUri jika pindah ke Cash/Bayar Nanti agar tidak tersimpan tidak sengaja
                imageUri = null
                binding.ivBuktiBayar.setImageResource(android.R.drawable.ic_menu_camera)
            }
        }

        binding.btnUploadBukti.setOnClickListener {
            tampilkanPilihanSumberFoto()
        }

        binding.ivBuktiBayar.setOnClickListener {
            if (imageUri != null) {
                ImageDialogUtil.showImageDialog(this, imageUri)
            }
        }

        binding.btnProsesBayar.setOnClickListener {
            val metode = when {
                binding.rbCash.isChecked -> "Cash"
                binding.rbTransfer.isChecked -> "Transfer"
                else -> "Bayar Nanti"
            }

            if (binding.rbTransfer.isChecked && imageUri == null) {
                Toast.makeText(this, "Bukti transfer wajib dilampirkan!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (binding.rbBayarNanti.isChecked && binding.etNamaPembeli.text.toString().isEmpty()) {
                Toast.makeText(this, "Nama Pembeli wajib diisi untuk Bayar Nanti!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(this)
                .setTitle("Konfirmasi Pembayaran")
                .setMessage("Apakah Anda yakin ingin menyelesaikan pembayaran ini?")
                .setPositiveButton("Ya, Selesaikan") { _, _ ->
                    prosesSimpanTransaksi(metode)
                }
                .setNegativeButton("Batal", null)
                .show()
        }
    }

    // ==========================================
    // LOGIKA SIMPAN TRANSAKSI & AMBIL NOMOR STRUK FIREBASE
    // ==========================================
    private fun prosesSimpanTransaksi(metode: String) {
        binding.btnProsesBayar.isEnabled = false
        binding.btnProsesBayar.text = "Memproses..."

        var buktiPath = ""
        if (imageUri != null) {
            buktiPath = simpanFotoKeInternal(imageUri!!) ?: ""
        }

        val status = if (binding.rbBayarNanti.isChecked) "BELUM_BAYAR" else "LUNAS"
        val nama = binding.etNamaPembeli.text.toString()
        val transactionId = if (CartManager.mode == CartManager.MODE_PELUNASAN || CartManager.mode == CartManager.MODE_EDIT) 
            CartManager.transaksiId 
        else 
            database.child("transactions").push().key ?: ""

        // Fungsi internal untuk menyimpan data
        fun simpanKeFirebase(nomorStruk: String) {
            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
            val tanggalSaatIni = sdf.format(java.util.Date())

            val transaksiBaru = TransactionModel(
                id = transactionId,
                namaPembeli = nama,
                totalHarga = CartManager.totalHarga,
                timestamp = System.currentTimeMillis(),
                items = CartManager.keranjang.toList(),
                statusBayar = status,
                metodePembayaran = metode,
                buktiPembayaranPath = buktiPath,
                nomorStruk = nomorStruk,
                tanggalWaktu = tanggalSaatIni
            )

            database.child("transactions").child(transactionId).setValue(transaksiBaru)
                .addOnSuccessListener {
                    // Hanya potong stok jika transaksi BARU (baik Cash, Transfer, atau Bayar Nanti)
                    // Pelunasan dan Edit tidak potong stok lagi karena sudah ditangani
                    if (CartManager.mode == CartManager.MODE_BARU) {
                        potongStokGudang()
                    }
                    transaksiTerakhir = transaksiBaru
                    tampilkanDialogCetakStruk()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Gagal memproses transaksi", Toast.LENGTH_SHORT).show()
                    binding.btnProsesBayar.isEnabled = true
                    binding.btnProsesBayar.text = "Selesaikan Pembayaran"
                }
        }

        // LOGIKA NOMOR STRUK: 
        // 1. Jika status LUNAS dan belum punya nomor struk (transaksi baru atau pelunasan), ambil nomor baru.
        // 2. Jika sudah ada nomor struk, gunakan yang lama.
        // 3. Jika status BELUM_BAYAR (Bayar Nanti), nomor struk dikosongkan dulu sampai lunas.
        if (status == "LUNAS" && CartManager.nomorStruk.isEmpty()) {
            database.child("settings").child("last_receipt_number").get().addOnSuccessListener { snapshot ->
                var lastNumber = snapshot.getValue(Int::class.java) ?: 0
                lastNumber++

                if (lastNumber > 9999) lastNumber = 1
                val nomorFormat = String.format("%04d", lastNumber)

                database.child("settings").child("last_receipt_number").setValue(lastNumber)
                simpanKeFirebase(nomorFormat)

            }.addOnFailureListener {
                Toast.makeText(this, "Gagal mengambil nomor urut. Periksa koneksi internet.", Toast.LENGTH_SHORT).show()
                binding.btnProsesBayar.isEnabled = true
                binding.btnProsesBayar.text = "Selesaikan Pembayaran"
            }
        } else {
            // Gunakan nomor yang sudah ada (berisi nomor lama atau tetap kosong jika BELUM_BAYAR)
            simpanKeFirebase(CartManager.nomorStruk)
        }
    }


    // ==========================================
    // LOGIKA CETAK STRUK BLUETOOTH
    // ==========================================

    private fun tampilkanDialogCetakStruk() {
        val t = transaksiTerakhir ?: return
        
        if (t.statusBayar == "BELUM_BAYAR") {
            AlertDialog.Builder(this)
                .setTitle("Berhasil!")
                .setMessage("Transaksi dipindahkan ke menu Bayar Nanti")
                .setPositiveButton("OK") { _, _ ->
                    selesaiDanTutup()
                }
                .setCancelable(false)
                .show()
            return
        }

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
            dialogGagalCetak("Bluetooth belum aktif. Aktifkan di pengaturan HP Anda lalu coba lagi.")
            return
        }

        val pairedDevices = bluetoothAdapter.bondedDevices
        if (pairedDevices.isEmpty()) {
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

    private fun dialogGagalCetak(pesan: String) {
        AlertDialog.Builder(this)
            .setTitle("Gagal Mencetak")
            .setMessage(pesan)
            .setPositiveButton("Coba Lagi") { _, _ ->
                cekIzinBluetoothDanCetak()
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

        runOnUiThread {
            Toast.makeText(this, "Menyambungkan ke printer...", Toast.LENGTH_SHORT).show()
        }

        Thread {
            // 1. SIAPKAN BAGIAN-BAGIAN STRUK
            val header = java.lang.StringBuilder()
            header.append("\n")
            header.append("JAYATRI KEDIRI\n")
            header.append("IG: @jayatrimini_4wd\n")
            header.append("TikTok: @JAYATRI_MINI4WD\n")
            header.append("WA: 0823-3311-1905\n")
            header.append("================================\n")

            val body = java.lang.StringBuilder()
            body.append("Tanggal   : ${t.tanggalWaktu}\n")
            body.append("No. Struk : ${t.nomorStruk}\n")
            body.append("Pelanggan : ${if(t.namaPembeli.isEmpty()) "Umum" else t.namaPembeli}\n")
            body.append("Metode    : ${t.metodePembayaran}\n")
            body.append("Status    : ${t.statusBayar}\n")
            body.append("--------------------------------\n")

            for (item in t.items) {
                val namaBarang = item.product?.namaBarang ?: ""
                val qty = item.quantity
                val harga = item.product?.harga ?: 0
                val subtotal = qty * harga

                body.append("$namaBarang\n")
                body.append("    $qty x Rp $harga = Rp $subtotal\n")
            }
            body.append("--------------------------------\n")

            val footer = java.lang.StringBuilder()
            footer.append("TOTAL BELANJA : Rp ${t.totalHarga}\n")
            footer.append("================================\n")
            footer.append("Terima Kasih Atas Kunjungan\n")
            footer.append("Anda!\n")
            footer.append("\n\n\n")

            // Gabungkan untuk logcat saja
            val fullStrukLog = header.toString() + body.toString() + footer.toString()
            android.util.Log.d("CEK_STRUK_KASIR", "\n" + fullStrukLog)

            // 2. BARU COBA KONEKSI KE PRINTER BLUETOOTH
            try {
                val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                val socket = device.createRfcommSocketToServiceRecord(uuid)
                socket.connect()

                val outputStream = socket.outputStream

                // KODE ALIGNMENT
                val alignCenter = byteArrayOf(0x1B, 0x61, 0x01)
                val alignLeft = byteArrayOf(0x1B, 0x61, 0x00)

                // 1. KODE BANGUNKAN PRINTER
                outputStream.write(byteArrayOf(0x1B, 0x40))

                // 2. KODE CETAK LOGO
                try {
                    val bitmapAsli = android.graphics.BitmapFactory.decodeResource(resources, R.drawable.logo)
                    if (bitmapAsli != null) {
                        val ukuranLogo = 200
                        val bitmapKecil = android.graphics.Bitmap.createScaledBitmap(bitmapAsli, ukuranLogo, ukuranLogo, false)
                        outputStream.write(alignCenter)
                        val logoBytes = ubahBitmapKeBytePrinter(bitmapKecil)
                        outputStream.write(logoBytes)
                    }
                } catch (e: Exception) { e.printStackTrace() }

                // 3. CETAK HEADER (CENTER)
                outputStream.write(alignCenter)
                outputStream.write(header.toString().toByteArray(java.nio.charset.StandardCharsets.US_ASCII))

                // 4. CETAK BODY (LEFT)
                outputStream.write(alignLeft)
                outputStream.write(body.toString().toByteArray(java.nio.charset.StandardCharsets.US_ASCII))

                // 5. CETAK FOOTER (CENTER)
                outputStream.write(alignCenter)
                outputStream.write(footer.toString().toByteArray(java.nio.charset.StandardCharsets.US_ASCII))

                outputStream.flush()
                Thread.sleep(500)
                socket.close()

                runOnUiThread {
                    Toast.makeText(this@CheckoutActivity, "Berhasil mencetak struk!", Toast.LENGTH_SHORT).show()
                    selesaiDanTutup()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    dialogGagalCetak("Gagal terhubung ke printer. Pastikan printer menyala, jarak dekat, dan tidak sedang dipakai perangkat lain.")
                }
            }
        }.start()
    }
    // ==========================================
    // FUNGSI UNTUK MENGUBAH GAMBAR KE BAHASA PRINTER
    // ==========================================
    private fun ubahBitmapKeBytePrinter(bitmap: android.graphics.Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val widthBytes = (width + 7) / 8
        val command = ByteArray(8 + widthBytes * height)

        // Perintah mesin GS v 0 (Print Raster Image)
        command[0] = 0x1D
        command[1] = 0x76
        command[2] = 0x30
        command[3] = 0x00

        command[4] = (widthBytes and 0xFF).toByte()
        command[5] = ((widthBytes shr 8) and 0xFF).toByte()
        command[6] = (height and 0xFF).toByte()
        command[7] = ((height shr 8) and 0xFF).toByte()

        var offset = 8
        for (y in 0 until height) {
            for (x in 0 until widthBytes) {
                var b = 0
                for (k in 0..7) {
                    val px = x * 8 + k
                    if (px < width) {
                        val pixel = bitmap.getPixel(px, y)
                        val r = android.graphics.Color.red(pixel)
                        val g = android.graphics.Color.green(pixel)
                        val bColor = android.graphics.Color.blue(pixel)
                        val a = android.graphics.Color.alpha(pixel)

                        // Menentukan warna hitam/putih (Luminance)
                        val luminance = (0.299 * r + 0.587 * g + 0.114 * bColor).toInt()
                        if (luminance < 128 && a > 128) {
                            b = b or (1 shl (7 - k))
                        }
                    }
                }
                command[offset++] = b.toByte()
            }
        }
        return command
    }

    // ==========================================

    private fun tampilkanPilihanSumberFoto() {
        val options = arrayOf("Ambil Foto (Kamera)", "Pilih dari Galeri")
        AlertDialog.Builder(this)
            .setTitle("Pilih Sumber Bukti Transfer")
            .setItems(options) { _, which ->
                if (which == 0) {
                    cekIzinKamera()
                } else {
                    galeriLauncher.launch("image/*")
                }
            }
            .show()
    }

    private fun cekIzinKamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
        } else {
            bukaKamera()
        }
    }

    private fun bukaKamera() {
        val values = android.content.ContentValues()
        values.put(android.provider.MediaStore.Images.Media.TITLE, "Bukti Transfer")
        tempCameraUri = contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        tempCameraUri?.let {
            kameraLauncher.launch(it)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            bukaKamera()
        } else if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            mulaiPilihPrinter()
        } else {
            if (requestCode == 101) {
                Toast.makeText(this, "Izin Bluetooth ditolak", Toast.LENGTH_SHORT).show()
                selesaiDanTutup()
            } else {
                Toast.makeText(this, "Izin Kamera ditolak", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun selesaiDanTutup() {
        CartManager.bersihkanKeranjang()
        if (CartManager.mode == CartManager.MODE_PELUNASAN) {
            val intent = Intent(this, BayarNantiActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        }
        finish()
    }

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