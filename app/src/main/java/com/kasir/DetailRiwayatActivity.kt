package com.kasir

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.kasir.databinding.ActivityDetailRiwayatBinding
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.io.File
import java.util.UUID

class DetailRiwayatActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDetailRiwayatBinding
    private var transaksi: TransactionModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailRiwayatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val id = intent.getStringExtra("ID")
        if (id != null) {
            Firebase.database.reference.child("transactions").child(id).get().addOnSuccessListener {
                val t = it.getValue(TransactionModel::class.java)
                if (t != null) {
                    transaksi = t
                    tampilkanData(t)
                    setupRestoreButton(t)
                }
            }
        }

        binding.btnCetakUlangStruk.setOnClickListener {
            cekIzinBluetoothDanCetak()
        }

        binding.ivDetailBukti.setOnClickListener {
            transaksi?.let { t ->
                if (t.buktiPembayaranPath.isNotEmpty()) {
                    ImageDialogUtil.showImageDialog(this, t.buktiPembayaranPath)
                }
            }
        }
    }

    private fun tampilkanData(t: TransactionModel) {
        if (t.deleted) {
            binding.tvDetailInfo.text = "TRANSAKSI INI TELAH DIHAPUS\n\n" + """
            No. Struk: ${t.nomorStruk}
            Pembeli: ${t.namaPembeli}
            Tanggal: ${t.tanggalWaktu}
            Metode: ${t.metodePembayaran}
            Status: ${t.statusBayar}
            
            TOTAL AKHIR: ${FormatterUtil.formatRupiah(t.totalHarga)}
            """.trimIndent()
            binding.tvDetailInfo.setTextColor(android.graphics.Color.RED)
            binding.btnCetakUlangStruk.visibility = View.GONE
        } else {
            binding.tvDetailInfo.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            binding.btnCetakUlangStruk.visibility = View.VISIBLE
            // Menampilkan Nomor Struk dan Tanggal dari Firebase ke Layar HP
            binding.tvDetailInfo.text = """
                No. Struk: ${t.nomorStruk}
                Pembeli: ${t.namaPembeli}
                Tanggal: ${t.tanggalWaktu}
                Metode: ${t.metodePembayaran}
                Status: ${t.statusBayar}
                
                TOTAL AKHIR: ${FormatterUtil.formatRupiah(t.totalHarga)}
            """.trimIndent()
        }

        var itemsText = ""
        for (item in t.items) {
            // Mengambil harga dari property model (antisipasi jika namanya beda)
            val unitPrice = item.product?.harga ?: (item.product?.hargaJual ?: 0)
            val subTotal = unitPrice * item.quantity
            itemsText += "${item.product?.namaBarang}\n"
            itemsText += "  ${item.quantity} x ${FormatterUtil.formatRupiah(unitPrice)} = ${FormatterUtil.formatRupiah(subTotal)}\n\n"
        }
        binding.tvDetailItems.text = itemsText.trim()

        if (t.buktiPembayaranPath.isNotEmpty()) {
            val file = File(t.buktiPembayaranPath)
            if (file.exists()) {
                binding.ivDetailBukti.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
            } else {
                binding.ivDetailBukti.visibility = View.GONE
            }
        } else {
            binding.ivDetailBukti.visibility = View.GONE
        }
    }

    private fun setupRestoreButton(t: TransactionModel) {
        if (t.deleted && t.statusBayar == "BELUM_BAYAR") {
            binding.btnRestoreBayarNanti.visibility = View.VISIBLE
            binding.btnRestoreBayarNanti.setOnClickListener {
                Firebase.database.reference.child("transactions").child(t.id).child("deleted").setValue(false)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Transaksi dikembalikan ke daftar Bayar Nanti!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Gagal mengembalikan transaksi", Toast.LENGTH_SHORT).show()
                    }
            }
        } else {
            binding.btnRestoreBayarNanti.visibility = View.GONE
        }
    }

    // ==========================================

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
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Harap aktifkan Bluetooth terlebih dahulu!", Toast.LENGTH_SHORT).show()
            return
        }

        val pairedDevices = bluetoothAdapter.bondedDevices
        if (pairedDevices.isEmpty()) {
            Toast.makeText(this, "Belum ada printer dipasangkan.", Toast.LENGTH_LONG).show()
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
            .setNegativeButton("Batal", null)
            .show()
    }

    @SuppressLint("MissingPermission")
    private fun kirimDataKePrinter(device: BluetoothDevice) {
        val t = transaksi ?: return

        runOnUiThread {
            Toast.makeText(this, "Menyambungkan ke printer...", Toast.LENGTH_SHORT).show()
        }

        Thread {
            try {
                // 1. SUSUN TEKS & TAMPILKAN LOGCAT (Sama persis dengan Checkout)
                val struk = java.lang.StringBuilder()
                struk.append("\n")
                struk.append("         JAYATRI KEDIRI         \n")
                struk.append("      IG: @jayatrimini_4wd      \n")
                struk.append("    TikTok: @JAYATRI_MINI4WD    \n")
                struk.append("       WA: 0823-3311-1905       \n")
                struk.append("================================\n")

                struk.append("Tanggal   : ${t.tanggalWaktu}\n")
                struk.append("No. Struk : ${t.nomorStruk}\n")
                struk.append("Pelanggan : ${if(t.namaPembeli.isEmpty()) "Umum" else t.namaPembeli}\n")
                struk.append("Metode    : ${t.metodePembayaran}\n")
                struk.append("Status    : ${t.statusBayar}\n")
                struk.append("--------------------------------\n")

                for (item in t.items) {
                    val namaBarang = item.product?.namaBarang ?: ""
                    val qty = item.quantity
                    val harga = item.product?.harga ?: (item.product?.hargaJual ?: 0)
                    val subtotal = qty * harga

                    struk.append("$namaBarang\n")
                    struk.append("    $qty x Rp $harga = Rp $subtotal\n")
                }

                struk.append("--------------------------------\n")
                struk.append("TOTAL BELANJA : Rp ${t.totalHarga}\n")
                struk.append("================================\n")
                struk.append("         (CETAK ULANG)          \n") // Tanda Cetak Ulang
                struk.append("  Terima Kasih Atas Kunjungan   \n")
                struk.append("           Anda!                \n")
                struk.append("\n\n\n")

                // Tampilkan ke Logcat untuk testing
                android.util.Log.d("CEK_STRUK_KASIR", "\n" + struk.toString())

                // 2. KONEKSI BLUETOOTH
                val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                val socket = device.createRfcommSocketToServiceRecord(uuid)
                socket.connect()

                val outputStream = socket.outputStream

                // 1. KODE BANGUNKAN PRINTER
                val initPrinter = byteArrayOf(0x1B, 0x40)
                outputStream.write(initPrinter)

                // 2. KODE CETAK LOGO (Tambahan Baru)
                try {
                    // Ambil logo dari folder drawable
                    val bitmapAsli = BitmapFactory.decodeResource(resources, R.drawable.logo)

                    if (bitmapAsli != null) {
                        // Kertas 58mm maksimal lebarnya 384 dot. Kita kecilkan gambar ke 200x200 agar proporsional
                        val ukuranLogo = 200
                        val bitmapKecil = android.graphics.Bitmap.createScaledBitmap(bitmapAsli, ukuranLogo, ukuranLogo, false)

                        // Suruh printer meletakkan logo di tengah (Center Align)
                        outputStream.write(byteArrayOf(0x1B, 0x61, 0x01))

                        // Ubah gambar dan kirim ke printer
                        val logoBytes = ubahBitmapKeBytePrinter(bitmapKecil)
                        outputStream.write(logoBytes)

                        // Kembalikan susunan ke kiri (Left Align)
                        outputStream.write(byteArrayOf(0x1B, 0x61, 0x00))
                    }
                } catch (e: Exception) {
                    // Abaikan dan lanjut cetak teks jika gambar gagal diproses
                    e.printStackTrace()
                }

                // 3. KIRIM TEKS STRUK
                outputStream.write(struk.toString().toByteArray(java.nio.charset.StandardCharsets.US_ASCII))

                outputStream.flush()
                Thread.sleep(500) // Jeda agar memori printer tidak putus di tengah jalan
                socket.close()

                runOnUiThread {
                    Toast.makeText(this@DetailRiwayatActivity, "Berhasil mencetak ulang struk!", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@DetailRiwayatActivity, "Gagal mencetak. Pastikan printer menyala.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            mulaiPilihPrinter()
        }
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
}