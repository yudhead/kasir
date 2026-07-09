package com.kasir

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import java.io.File

object ImageDialogUtil {
    fun showImageDialog(context: Context, imageSource: Any?) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_full_image, null)
        val ivFullImage = dialogView.findViewById<ImageView>(R.id.ivFullImage)

        Glide.with(context)
            .load(imageSource)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_report_image)
            .into(ivFullImage)

        AlertDialog.Builder(context)
            .setView(dialogView)
            .setPositiveButton("Tutup", null)
            .show()
    }
}