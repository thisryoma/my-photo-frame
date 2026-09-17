package com.example.photoframe

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val PICK_FILES_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowContentAccess = true
            settings.allowFileAccess = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            addJavascriptInterface(AndroidBridge(this@MainActivity), "AndroidBridge")
            loadUrl("file:///android_asset/index.html")
        }
        setContentView(webView)
    }

    class AndroidBridge(private val activity: MainActivity) {
        @JavascriptInterface
        fun openFilePicker() {
            activity.runOnUiThread {
                activity.startFilePicker()
            }
        }
    }

    private fun startFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, PICK_FILES_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILES_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.let { intentData ->
                val jsonArray = JSONArray()
                val clipData = intentData.clipData

                if (clipData != null) {
                    for (i in 0 until clipData.itemCount) {
                        processUri(clipData.getItemAt(i).uri, jsonArray)
                    }
                } else {
                    intentData.data?.let { uri ->
                        processUri(uri, jsonArray)
                    }
                }

                val jsonString = jsonArray.toString()
                webView.evaluateJavascript("window.onAndroidFilesSelected && window.onAndroidFilesSelected('$jsonString');", null)
            }
        }
    }

    private fun processUri(uri: Uri, jsonArray: JSONArray) {
        try {
            // 再起動後も持続する読み取り権限を取得
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        var name = "unknown"
        var size = 0L
        val lastModified = System.currentTimeMillis()

        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) name = cursor.getString(nameIndex) ?: "unknown"
                if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
            }
        }

        val mimeType = contentResolver.getType(uri) ?: ""
        val isVideo = mimeType.startsWith("video") || name.endsWith(".mp4", true) || name.endsWith(".mov", true)

        val jsonObject = JSONObject().apply {
            put("uri", uri.toString())
            put("name", name)
            put("size", size)
            put("timestamp", lastModified)
            put("isVideo", isVideo)
        }
        jsonArray.put(jsonObject)
    }
}

