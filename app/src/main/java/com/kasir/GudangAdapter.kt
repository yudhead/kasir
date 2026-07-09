package com.kasir

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.kasir.databinding.ItemBarangBinding

class GudangAdapter(private val listBarang: List<Product>) : RecyclerView.Adapter<GudangAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemBarangBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemBarangBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val product = listBarang[position]
        val context = holder.itemView.context

        with(holder.binding) {
            tvItemNama.text = product.namaBarang
            // Menampilkan harga jual dan kategori
            tvItemHarga.text = "${FormatterUtil.formatRupiah(product.hargaJual)} (${product.kategori})"
            tvItemStok.text = "Sisa Stok: ${product.stok}"

            Glide.with(context)
                .load(product.imageUrl)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .into(ivItemFoto)

            ivItemFoto.setOnClickListener {
                if (product.imageUrl.isNotEmpty()) {
                    ImageDialogUtil.showImageDialog(context, product.imageUrl)
                }
            }
        }

        holder.itemView.setOnClickListener {
            val intent = Intent(context, EditBarangActivity::class.java).apply {
                putExtra("EXTRA_ID", product.id)
            }
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = listBarang.size
}
