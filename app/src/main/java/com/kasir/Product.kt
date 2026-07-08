package com.kasir

data class Product(
    val id: String = "",
    val namaBarang: String = "",
    val kategori: String = "",
    val hargaBeli: Int = 0,
    val hargaJual: Int = 0,
    val harga: Int = 0, // Fallback for backward compatibility
    val stok: Int = 0,
    val imageUrl: String = ""
)