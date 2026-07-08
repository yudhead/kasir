package com.kasir

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kasir.databinding.ItemHutangBinding

class HutangAdapter(
    private val listHutang: List<TransactionModel>,
    private val onClick: (TransactionModel) -> Unit
) : RecyclerView.Adapter<HutangAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemHutangBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHutangBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val transaksi = listHutang[position]

        with(holder.binding) {
            tvNamaPembeli.text = transaksi.namaPembeli
            tvTotalHutang.text = "Total Hutang: ${FormatterUtil.formatRupiah(transaksi.totalHarga)}"
        }

        holder.itemView.setOnClickListener {
            onClick(transaksi)
        }
    }

    override fun getItemCount(): Int = listHutang.size
}