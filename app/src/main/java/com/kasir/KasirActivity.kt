package com.kasir

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.kasir.databinding.ActivityKasirBinding

class KasirActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKasirBinding
    private lateinit var database: DatabaseReference

    private val fullProductList = mutableListOf<Product>()
    private val filteredProductList = mutableListOf<Product>()
    private lateinit var productAdapter: KasirAdapter
    private lateinit var keranjangAdapter: KeranjangAdapter
    
    private var selectedCategory = "Semua Kategori"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKasirBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = Firebase.database.reference

        setupRecyclerViews()
        setupSearchAndFilter()

        // Bersihkan keranjang hanya jika transaksi baru
        if (CartManager.mode == CartManager.MODE_BARU) {
            CartManager.bersihkanKeranjang()
        }

        updateUIBasedOnMode()
        updateTotalUI()
        ambilDataBarang()

        binding.btnLanjutCheckout.setOnClickListener {
            if (CartManager.keranjang.isEmpty()) {
                Toast.makeText(this, "Keranjang masih kosong!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (CartManager.mode == CartManager.MODE_EDIT) {
                simpanPerubahanHutang()
            } else {
                startActivity(Intent(this, CheckoutActivity::class.java))
            }
        }
    }

    private fun setupRecyclerViews() {
        // Setup Produk
        productAdapter = KasirAdapter(filteredProductList) { product ->
            tampilkanDialogTambah(product)
        }
        binding.rvKasirProducts.layoutManager = LinearLayoutManager(this)
        binding.rvKasirProducts.adapter = productAdapter

        // Setup Keranjang
        binding.rvKeranjang.layoutManager = LinearLayoutManager(this)
        keranjangAdapter = KeranjangAdapter(CartManager.keranjang) {
            updateTotalUI()
        }
        binding.rvKeranjang.adapter = keranjangAdapter
    }

    private fun setupSearchAndFilter() {
        // Search by Name
        binding.etSearchKasir.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterProducts()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Filter by Category
        binding.actvCategoryKasir.setOnItemClickListener { parent, _, position, _ ->
            selectedCategory = parent.getItemAtPosition(position).toString()
            filterProducts()
        }
    }

    private fun updateCategoryDropdown() {
        val categories = mutableListOf("Semua Kategori")
        categories.addAll(fullProductList.map { it.kategori }.filter { it.isNotEmpty() }.distinct().sorted())
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        binding.actvCategoryKasir.setAdapter(adapter)
        
        if (!categories.contains(selectedCategory)) {
            selectedCategory = "Semua Kategori"
            binding.actvCategoryKasir.setText("Semua Kategori", false)
        } else {
            binding.actvCategoryKasir.setText(selectedCategory, false)
        }
    }

    private fun filterProducts() {
        val query = binding.etSearchKasir.text.toString().lowercase()
        
        val filtered = fullProductList.filter { product ->
            val matchName = product.namaBarang.lowercase().contains(query)
            val matchCategory = if (selectedCategory == "Semua Kategori") {
                true
            } else {
                product.kategori == selectedCategory
            }
            matchName && matchCategory
        }
        
        filteredProductList.clear()
        filteredProductList.addAll(filtered)
        productAdapter.notifyDataSetChanged()
        
        // Update Total Stok yang tersedia berdasarkan filter
        val totalStokTersedia = filtered.sumOf { it.stok }
        binding.tvTotalStokKasir.text = "Tersedia: $totalStokTersedia"
    }

    private fun updateUIBasedOnMode() {
        when (CartManager.mode) {
            CartManager.MODE_BARU -> {
                binding.tvKasirTitle.text = "Pilih Barang"
                binding.rvKeranjang.visibility = android.view.View.GONE
                binding.tvDaftarProdukTitle.text = "Daftar Produk"
            }
            CartManager.MODE_EDIT -> {
                binding.tvKasirTitle.text = "Edit Barang Hutang"
                binding.rvKeranjang.visibility = android.view.View.VISIBLE
                binding.tvDaftarProdukTitle.text = "Tambah Barang Lain"
                binding.btnLanjutCheckout.text = "Simpan Perubahan"
            }
            CartManager.MODE_PELUNASAN -> {
                binding.tvKasirTitle.text = "Pelunasan Hutang"
                binding.rvKeranjang.visibility = android.view.View.VISIBLE
                binding.tvDaftarProdukTitle.visibility = android.view.View.GONE
                binding.tilSearchKasir.visibility = android.view.View.GONE
                binding.tilCategoryFilterKasir.visibility = android.view.View.GONE
                binding.tvTotalStokKasir.visibility = android.view.View.GONE
            }
        }
    }

    private fun ambilDataBarang() {
        database.child("products").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                fullProductList.clear()
                for (data in snapshot.children) {
                    val product = data.getValue(Product::class.java)
                    if (product != null && product.stok > 0) {
                        fullProductList.add(product)
                    }
                }
                updateCategoryDropdown()
                filterProducts()
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@KasirActivity, error.message, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun tampilkanDialogTambah(product: Product) {
        AlertDialog.Builder(this)
            .setTitle(product.namaBarang)
            .setMessage("Stok tersedia: ${product.stok}\nTambahkan barang ini ke keranjang?")
            .setPositiveButton("Tambah") { _, _ ->
                val item = CartManager.keranjang.find { it.product?.id == product.id }
                if (item != null) {
                    if (item.quantity < product.stok) {
                        item.quantity += 1
                        Toast.makeText(this, "Barang berhasil ditambahkan", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Stok tidak mencukupi!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    CartManager.keranjang.add(CartItem(product, 1))
                    Toast.makeText(this, "Barang berhasil ditambahkan", Toast.LENGTH_SHORT).show()
                }
                keranjangAdapter.notifyDataSetChanged()
                updateTotalUI()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun updateTotalUI() {
        hitungTotalBarang()
        binding.tvKasirTotal.text = "Total Keranjang : ${FormatterUtil.formatRupiah(CartManager.totalHarga)}"
        
        // Tampilkan/Sembunyikan RecyclerView Keranjang berdasarkan isi
        if (CartManager.mode == CartManager.MODE_BARU) {
            binding.rvKeranjang.visibility = if (CartManager.keranjang.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        }
    }

    private fun hitungTotalBarang() {
        CartManager.totalHarga = 0
        CartManager.keranjang.forEach {
            CartManager.totalHarga += (it.product?.hargaJual ?: 0) * it.quantity
        }
    }

    private fun simpanPerubahanHutang() {
        binding.btnLanjutCheckout.isEnabled = false
        val db = Firebase.database.reference
        
        val allProductIds = (CartManager.keranjang.map { it.product?.id } + 
                            CartManager.oldItems.map { it.product?.id })
                            .filterNotNull()
                            .distinct()

        var completedUpdates = 0
        val totalProductsToUpdate = allProductIds.size

        if (totalProductsToUpdate == 0) {
            finalisasiUpdate()
            return
        }

        allProductIds.forEach { productId ->
            val oldQty = CartManager.oldItems.find { it.product?.id == productId }?.quantity ?: 0
            val newQty = CartManager.keranjang.find { it.product?.id == productId }?.quantity ?: 0
            val diff = newQty - oldQty

            if (diff != 0) {
                db.child("products").child(productId).child("stok").get().addOnSuccessListener { snapshot ->
                    val currentStok = snapshot.getValue(Int::class.java) ?: 0
                    val newStok = currentStok - diff
                    
                    db.child("products").child(productId).child("stok").setValue(newStok).addOnCompleteListener {
                        completedUpdates++
                        if (completedUpdates == totalProductsToUpdate) {
                            finalisasiUpdate()
                        }
                    }
                }.addOnFailureListener {
                    completedUpdates++
                    if (completedUpdates == totalProductsToUpdate) {
                        finalisasiUpdate()
                    }
                }
            } else {
                completedUpdates++
                if (completedUpdates == totalProductsToUpdate) {
                    finalisasiUpdate()
                }
            }
        }
    }

    private fun finalisasiUpdate() {
        val db = Firebase.database.reference
        val update = hashMapOf<String, Any>(
            "items" to CartManager.keranjang,
            "totalHarga" to CartManager.totalHarga
        )

        db.child("transactions").child(CartManager.transaksiId).updateChildren(update)
            .addOnSuccessListener {
                Toast.makeText(this, "Berhasil diupdate!", Toast.LENGTH_SHORT).show()
                CartManager.bersihkanKeranjang()
                val intent = Intent(this, HutangActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                startActivity(intent)
                finish()
            }
            .addOnFailureListener {
                binding.btnLanjutCheckout.isEnabled = true
                Toast.makeText(this, "Gagal update transaksi", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onResume() {
        super.onResume()
        updateTotalUI()
    }
}