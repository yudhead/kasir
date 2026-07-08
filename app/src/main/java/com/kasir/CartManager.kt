package com.kasir

object CartManager {

    val keranjang = mutableListOf<CartItem>()
    val oldItems = mutableListOf<CartItem>()
    var totalHarga = 0

    const val MODE_BARU = "BARU"
    const val MODE_EDIT = "EDIT"
    const val MODE_PELUNASAN = "PELUNASAN"

    var mode = MODE_BARU

    var transaksiId = ""

    var namaPembeli = ""

    fun bersihkanKeranjang() {

        keranjang.clear()

        oldItems.clear()

        totalHarga = 0

        mode = MODE_BARU

        transaksiId = ""

        namaPembeli = ""
    }
}