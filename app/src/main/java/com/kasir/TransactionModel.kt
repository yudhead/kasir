package com.kasir

data class TransactionModel(
    val id: String = "",
    val namaPembeli: String = "", // Tambahkan nama pembeli
    val totalHarga: Int = 0,
    val timestamp: Long = 0L,
    val items: List<CartItem> = emptyList(),
    val statusBayar: String = "LUNAS", // "LUNAS" atau "BELUM_BAYAR"
    val metodePembayaran: String = "",
    val buktiPembayaranPath: String = ""
)