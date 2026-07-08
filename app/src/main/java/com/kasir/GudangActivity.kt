package com.kasir

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.kasir.databinding.ActivityGudangBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class GudangActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGudangBinding
    private lateinit var database: DatabaseReference
    private val fullList = mutableListOf<Product>()
    private val displayList = mutableListOf<Product>()
    private lateinit var adapter: GudangAdapter
    
    private var selectedCategory = "Semua Kategori"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGudangBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = Firebase.database.reference
        
        setupRecyclerView()
        setupSearchAndFilter()

        ambilDataGudang()
    }

    private fun setupRecyclerView() {
        adapter = GudangAdapter(displayList)
        binding.rvGudang.layoutManager = LinearLayoutManager(this)
        binding.rvGudang.adapter = adapter
    }

    private fun setupSearchAndFilter() {
        // Search by Name
        binding.etSearchGudang.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterData()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Filter by Category
        binding.actvCategory.setOnItemClickListener { parent, _, position, _ ->
            selectedCategory = parent.getItemAtPosition(position).toString()
            filterData()
        }
    }

    private fun updateCategoryDropdown() {
        val categories = mutableListOf("Semua Kategori")
        categories.addAll(fullList.map { it.kategori }.filter { it.isNotEmpty() }.distinct().sorted())
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        binding.actvCategory.setAdapter(adapter)
        
        // Reset to "Semua Kategori" if current selection is not in list (except for default)
        if (!categories.contains(selectedCategory)) {
            selectedCategory = "Semua Kategori"
            binding.actvCategory.setText("Semua Kategori", false)
        } else {
            binding.actvCategory.setText(selectedCategory, false)
        }
    }

    private fun filterData() {
        val query = binding.etSearchGudang.text.toString().lowercase()
        
        val filtered = fullList.filter { product ->
            val matchName = product.namaBarang.lowercase().contains(query)
            val matchCategory = if (selectedCategory == "Semua Kategori") {
                true
            } else {
                product.kategori == selectedCategory
            }
            matchName && matchCategory
        }

        displayList.clear()
        displayList.addAll(filtered)
        adapter.notifyDataSetChanged()
        
        // Update Total yang tampil (Opsional, tapi bagus untuk UX)
        // Hitung total stok dari hasil filter
        val totalStokFiltered = filtered.sumOf { it.stok }
        binding.tvTotalStok.text = "Total Stok: $totalStokFiltered"
    }

    private fun ambilDataGudang() {
        database.child("products").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                fullList.clear()
                var totalStokGudang = 0
                for (data in snapshot.children) {
                    val product = data.getValue(Product::class.java)
                    if (product != null) {
                        fullList.add(product)
                        totalStokGudang += product.stok
                    }
                }
                
                // Update total stok keseluruhan di pojok kanan (saat data awal dimuat)
                // Note: filterData() juga akan mengupdate ini berdasarkan filter yang aktif
                updateCategoryDropdown()
                filterData()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@GudangActivity, "Gagal: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
}