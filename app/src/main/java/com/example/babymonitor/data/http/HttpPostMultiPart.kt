package com.example.babymonitor.data.http

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.android.volley.DefaultRetryPolicy
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.Volley
import org.apache.http.HttpEntity
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.entity.mime.content.StringBody
import java.io.ByteArrayOutputStream
import java.io.File

class HttpPostMultiPart(private val context: Context) {

    companion object {
        const val TAG = "HttpPost"
    }

    private var mQueue: RequestQueue = Volley.newRequestQueue(context)

    fun doUpload(url: String, fileMap: Map<String, File>, stringMap: Map<String, String>) {
        val multipartRequest = MultipartRequest(
            url,
            // Response.Listener、Upload成功
            { response ->
                Log.d(TAG, "アップロード成功:\n $response")
                Toast.makeText(context, "ファイルのアップロードが完了しました。:${url}", Toast.LENGTH_LONG).show()
            },
            // Response.ErrorListener、Upload失敗
            { e ->
                Log.d(TAG, "アップロード失敗:\n ${e.networkResponse}", e)
                Toast.makeText(context, "ファイルのアップロードに失敗しました。:${url}", Toast.LENGTH_LONG).show()
            },
            stringMap,
            fileMap
        )
        mQueue.add(multipartRequest)
    }

    inner class MultipartRequest(
        url: String,
        private val mListener: Response.Listener<String>,
        errorListener: Response.ErrorListener,
        private val stringParts: Map<String, String>,
        private val fileParts: Map<String, File>
    ) : Request<String?>(Method.POST, url, errorListener) {
        private lateinit var httpEntity: HttpEntity
        private var entity = MultipartEntityBuilder.create()
            .setCharset(charset("Shift_JIS"))

        init {
            buildMultipartEntity()
            // Connection timeout, Read Timeout
            retryPolicy =
                DefaultRetryPolicy(30000, DefaultRetryPolicy.DEFAULT_MAX_RETRIES, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
        }

        private fun buildMultipartEntity() {
            //File Data
            val contentType = ContentType.create("text/plain", charset("Shift_JIS"))
            fileParts.forEach { (k, v) ->
                entity.addBinaryBody(k, v, contentType, v.name)
            }
            stringParts.forEach { (k, v) ->
                entity.addPart(k, StringBody(v, contentType))
            }
            httpEntity = entity.build()
        }

        override fun getBodyContentType(): String {
            return httpEntity.contentType?.value ?: ""
        }

        override fun getBody(): ByteArray {
            // TODO ファイルのサイズが大きくて、OutOfMemoryが起きる場合はHurlStackの実装が必要
            // @see http://fly1tkg.github.io/2014/03/volley-multipart-form-data/
            val bos = ByteArrayOutputStream()
            httpEntity.writeTo(bos)
            return bos.toByteArray()
        }

        override fun parseNetworkResponse(response: NetworkResponse?): Response<String?>? {
            return Response.success("Uploaded", cacheEntry)
        }

        override fun deliverResponse(response: String?) {
            mListener.onResponse(response)
        }
    }
}
