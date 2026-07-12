package com.kasir

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.kasir.databinding.ActivityHutangBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class BayarNantiActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHutangBinding
    private val listBayarNanti = mutableListOf<TransactionModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHutangBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvBayarNanti.layoutManager = LinearLayoutManager(this)

        // Query tanpa filter statusBayar di level database agar isDeleted bisa dicek di sisi client dengan benar
        Firebase.database.reference.child("transactions")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    listBayarNanti.clear()
                    for (data in snapshot.children) {
                        val t = data.getValue(TransactionModel::class.java)
                        // Filter manual: Harus BELUM_BAYAR DAN tidak dihapus
                        if (t != null && t.statusBayar == "BELUM_BAYAR" && t.deleted == false) {
                            listBayarNanti.add(t)
                        }
                    }
                    // Gunakan adapter sederhana (bisa pakai adapter list biasa)
                    val adapter = BayarNantiAdapter(listBayarNanti) { transaksi ->
                        val intent = Intent(this@BayarNantiActivity, DetailBayarNantiActivity::class.java)
                        intent.putExtra("ID", transaksi.id)
                        startActivity(intent)
                    }
                    binding.rvBayarNanti.adapter = adapter
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }
}