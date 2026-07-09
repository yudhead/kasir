package com.kasir

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.kasir.databinding.ItemBarangBinding

class KasirAdapter(
    private val listBarang: List<Product>,
    private val onClick: (Product) -> Unit
) : RecyclerView.Adapter<KasirAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemBarangBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

        val binding = ItemBarangBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )

        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val product = listBarang[position]

        with(holder.binding) {

            tvItemNama.text = product.namaBarang

            tvItemHarga.text = FormatterUtil.formatRupiah(product.harga)

            tvItemStok.text = "Stok : ${product.stok}"

            Glide.with(root.context)
                .load(product.imageUrl)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .into(ivItemFoto)

            ivItemFoto.setOnClickListener {
                if (product.imageUrl.isNotEmpty()) {
                    ImageDialogUtil.showImageDialog(root.context, product.imageUrl)
                }
            }

            root.setOnClickListener {
                onClick(product)
            }
        }
    }

    override fun getItemCount(): Int = listBarang.size
}