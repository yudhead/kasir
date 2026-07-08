package com.kasir

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.kasir.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inisialisasi View Binding
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inisialisasi Firebase Auth
        auth = Firebase.auth

        // Cek apakah user sudah login sebelumnya (Sesi aktif)
        if (auth.currentUser != null) {
            moveToMainActivity()
        }

        // Aksi ketika tombol login ditekan
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            // Validasi Input
            if (email.isEmpty()) {
                binding.etEmail.error = "Email tidak boleh kosong"
                binding.etEmail.requestFocus()
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                binding.etPassword.error = "Password tidak boleh kosong"
                binding.etPassword.requestFocus()
                return@setOnClickListener
            }

            // Jalankan proses login jika validasi lolos
            loginProcess(email, password)
        }
    }

    private fun loginProcess(email: String, pass: String) {
        // Tampilkan loading screen, sembunyikan tombol login sementara
        showLoading(true)

        auth.signInWithEmailAndPassword(email, pass)
            .addOnCompleteListener(this) { task ->
                showLoading(false) // Matikan loading screen setelah respon diterima

                if (task.isSuccessful) {
                    Toast.makeText(this, "Login Berhasil", Toast.LENGTH_SHORT).show()
                    moveToMainActivity()
                } else {
                    // Tampilkan pesan error spesifik dari Firebase jika gagal
                    Toast.makeText(
                        this,
                        "Gagal masuk: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun showLoading(isLoading: Boolean) {
        if (isLoading) {
            binding.progressBar.visibility = View.VISIBLE
            binding.btnLogin.isEnabled = false
        } else {
            binding.progressBar.visibility = View.GONE
            binding.btnLogin.isEnabled = true
        }
    }

    private fun moveToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish() // Menghancurkan LoginActivity agar tidak bisa di-back kembali oleh user
    }
}
