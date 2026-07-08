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

class HutangActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHutangBinding
    private val listHutang = mutableListOf<TransactionModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHutangBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvHutang.layoutManager = LinearLayoutManager(this)

        // Query khusus data yang belum lunas
        Firebase.database.reference.child("transactions")
            .orderByChild("statusBayar")
            .equalTo("BELUM_BAYAR")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    listHutang.clear()
                    for (data in snapshot.children) {
                        val t = data.getValue(TransactionModel::class.java)
                        if (t != null) listHutang.add(t)
                    }
                    // Gunakan adapter sederhana (bisa pakai adapter list biasa)
                    val adapter = HutangAdapter(listHutang) { transaksi ->
                        val intent = Intent(this@HutangActivity, DetailHutangActivity::class.java)
                        intent.putExtra("ID", transaksi.id)
                        startActivity(intent)
                    }
                    binding.rvHutang.adapter = adapter
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }
}