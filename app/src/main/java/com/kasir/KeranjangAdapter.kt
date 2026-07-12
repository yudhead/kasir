package com.kasir

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.recyclerview.widget.RecyclerView
import com.kasir.databinding.ItemKeranjangBinding

class KeranjangAdapter(
    private val listItems: List<CartItem>,
    private val onQuantityChanged: () -> Unit
) : RecyclerView.Adapter<KeranjangAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemKeranjangBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemKeranjangBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = listItems[position]
        with(holder.binding) {
            tvNamaItem.text = item.product?.namaBarang
            tvHargaItem.text = FormatterUtil.formatRupiah(item.product?.harga ?: 0)
            
            // Set text tanpa trigger listener (menggunakan setText langsung)
            etQuantity.setText(item.quantity.toString())

            btnTambah.setOnClickListener {
                val stockInWarehouse = item.product?.stok ?: 0
                
                // Jika dalam mode edit bayar nanti, kita hitung selisih dari quantity lama
                val oldQty = CartManager.oldItems.find { it.product?.id == item.product?.id }?.quantity ?: 0
                val totalAvailableStock = stockInWarehouse + oldQty
                
                if (item.quantity < totalAvailableStock) {
                    item.quantity++
                    etQuantity.setText(item.quantity.toString())
                    onQuantityChanged()
                } else {
                    android.widget.Toast.makeText(holder.itemView.context, "Stok tidak mencukupi!", android.widget.Toast.LENGTH_SHORT).show()
                }
            }

            btnKurang.setOnClickListener {
                if (item.quantity > 1) {
                    item.quantity--
                    etQuantity.setText(item.quantity.toString())
                    onQuantityChanged()
                } else {
                    val currentPos = holder.bindingAdapterPosition
                    if (currentPos != RecyclerView.NO_POSITION) {
                        (listItems as MutableList).removeAt(currentPos)
                        notifyItemRemoved(currentPos)
                        notifyItemRangeChanged(currentPos, listItems.size)
                        onQuantityChanged()
                    }
                }
            }

            // Input Manual melalui EditText
            etQuantity.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val input = etQuantity.text.toString()
                    if (input.isNotEmpty()) {
                        val newQty = input.toIntOrNull() ?: item.quantity
                        val stockInWarehouse = item.product?.stok ?: 0
                        
                        // Hitung total stok yang bisa digunakan (stok gudang + yang sudah diambil sebelumnya)
                        val oldQty = CartManager.oldItems.find { it.product?.id == item.product?.id }?.quantity ?: 0
                        val totalAvailableStock = stockInWarehouse + oldQty
                        
                        when {
                            newQty > totalAvailableStock -> {
                                android.widget.Toast.makeText(holder.itemView.context, "Stok hanya tersedia $totalAvailableStock", android.widget.Toast.LENGTH_SHORT).show()
                                item.quantity = totalAvailableStock
                            }
                            newQty <= 0 -> {
                                item.quantity = 1
                            }
                            else -> {
                                item.quantity = newQty
                            }
                        }
                        etQuantity.setText(item.quantity.toString())
                        onQuantityChanged()
                    } else {
                        etQuantity.setText(item.quantity.toString())
                    }
                }
            }

            // Menangani tombol "Done" pada keyboard
            etQuantity.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT) {
                    etQuantity.clearFocus()
                    true
                } else {
                    false
                }
            }
        }
    }

    override fun getItemCount(): Int = listItems.size
}