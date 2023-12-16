package com.tatsuya.babymonitor.ui.gallery

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.signin.GoogleSignIn.getClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.google.photos.library.v1.PhotosLibraryClient
import com.google.photos.library.v1.PhotosLibrarySettings
import com.google.photos.library.v1.internal.InternalPhotosLibraryClient
import com.google.photos.library.v1.proto.SearchMediaItemsRequest
import com.google.photos.types.proto.MediaItem
import com.tatsuya.babymonitor.R
import com.tatsuya.babymonitor.databinding.FragmentGalleryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class GalleryFragment : Fragment() {

    private var _binding: FragmentGalleryBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private lateinit var _context: Context

    private val imageUris = mutableListOf<Uri>()


    companion object {
        const val REQUEST_AUTHORIZE = 123
        const val Scope = "https://www.googleapis.com/auth/photoslibrary.readonly"
        const val TAG = "SignIn"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _context = requireContext()

        val galleryViewModel =
            ViewModelProvider(this).get(GalleryViewModel::class.java)

        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        val root: View = binding.root

//        val textView: TextView = binding.textGallery
//        galleryViewModel.text.observe(viewLifecycleOwner) {
//            textView.text = it
//        }

//        signOut()
        signIn()
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        Log.d(TAG, "requestCode:${requestCode}")
        when (requestCode) {
            REQUEST_AUTHORIZE -> {
                val authorizationResult = Identity.getAuthorizationClient(_context)
                    .getAuthorizationResultFromIntent(data)
                Log.d(TAG, "authorizationResult:${authorizationResult.accessToken}")
                load(authorizationResult.accessToken!!)
            }
        }
    }

    private fun signIn() {
        Log.d(TAG, "start")
        val requestedScopes = listOf(Scope(Scope))
        val authorizationRequest =
            AuthorizationRequest.builder().setRequestedScopes(requestedScopes).build()
        Identity.getAuthorizationClient(_context)
            .authorize(authorizationRequest)
            .addOnSuccessListener { authorizationResult: AuthorizationResult ->
                if (authorizationResult.hasResolution()) {
                    // Access needs to be granted by the user
                    val pendingIntent = authorizationResult.pendingIntent
                    try {
                        startIntentSenderForResult(
                            pendingIntent!!.intentSender,
                            REQUEST_AUTHORIZE, null, 0, 0, 0, null
                        )
                    } catch (e: IntentSender.SendIntentException) {
                        Log.e(TAG, "Couldn't start Authorization UI: " + e.localizedMessage)
                    }
                } else {
                    // Access already granted, continue with user action
                    Log.d(TAG, "Access already granted:${authorizationResult.accessToken}")
                    load(authorizationResult.accessToken!!)
                }
            }
            .addOnFailureListener { e: Exception? ->
                Log.e(TAG, "Failed to authorize", e)
            }
    }

    private fun signOut() {
        val client = getClient(_context, GoogleSignInOptions.DEFAULT_SIGN_IN)
        client.signOut()
    }

    private fun load(token: String) {

        lifecycleScope.launch {
            Log.d("Load", "start")
//            var response = async { get(token)!! }.await()
            var response = get(token)!!
            Log.d("Get", "called")
//            var nextPageToken = response.nextPageToken
//            Log.d("NextPageToken", nextPageToken)
            var nextPageToken = response.second
            Log.d("Next", nextPageToken)
            val items = response.first.toMutableList()
            Log.d("Items", items.size.toString())
//            Toast.makeText(_context, "result:${items.size}", Toast.LENGTH_SHORT).show()
            _binding!!.recyclerView.apply {
                setHasFixedSize(true)
                layoutManager = GridLayoutManager(_context, 3)
                adapter = MediaItemAdapter(items, _context)
                while (nextPageToken.isNotEmpty()) {
                    response = get(token, nextPageToken)!!
                    val positionStart = items.size
                    val addItems = response.first
                    items.addAll(addItems)
                    adapter?.notifyItemRangeInserted(positionStart, addItems.size)
                    Log.d("Insert", "positionStart:${positionStart}")
                    Log.d("Insert", "itemCount:${addItems.size}")
                    nextPageToken = response.second
                }
            }
            Log.d("Load", "finish")
        }
        Log.d("Scope", "end")
    }

    private suspend fun get(
        token: String,
        nextPageToken: String = ""
    ): Pair<List<MediaItem>, String> =
        withContext(Dispatchers.IO) {
            val accessToken = AccessToken.newBuilder().setTokenValue(token).build()
            Log.d("Photos", "accessToken:${accessToken.tokenValue}")
            val credentials = GoogleCredentials.newBuilder()
                .setAccessToken(accessToken)
                .build()
            val settings = PhotosLibrarySettings.newBuilder()
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                .build()

//        val result = withContext(Dispatchers.IO) {
            var response: InternalPhotosLibraryClient.SearchMediaItemsPagedResponse? = null
//            var result = listOf<MediaItem>()
//            try {
            PhotosLibraryClient.initialize(settings).use { photosLibraryClient ->

                Log.d("Photos", "Start")
                // Create a new Album  with at title
                val albumId =
                    "AJpk6ZhLuc2F3-wf7jyYbuJh2d5JD8o2vvHqjJw4FUUOpzn6gw0y3XqNv64xllpjM2paBKhPRk1Z"
                val albumId2 =
                    "AJpk6ZiS8T1CmcKN1h9NeB7F0YtjP-5uSXj0akY11v71wdk06Gvlc_y7bmF88xAFwCBhTAKOrQtG"
                val albumId3 =
                    "AJpk6ZjtYKAMOdsUpC-cRgp9Vy0ZTgsRKSbUNn1uPKagVrb1njGAhMAsvikkpPBcEhOKgMG7LJf-"

                val options = SearchMediaItemsRequest.newBuilder()
                    .setAlbumId(albumId)
                    .setPageSize(10)
                    .setPageToken(nextPageToken)
                    .build()

                val response = photosLibraryClient.searchMediaItems(options)!!
                Log.d("Page", "count:${response?.page?.pageElementCount}")
                return@withContext Pair<List<MediaItem>, String>(
                    response.page.response.mediaItemsList,
                    response.nextPageToken
                )
            }
        }
}

class MediaItemAdapter(private val mediaItems: List<MediaItem>, private val context: Context) :
    RecyclerView.Adapter<MediaItemAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.item_media,
            parent,
            false
        )
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val mediaItem = mediaItems[position]
        Log.d("View", "mimeType:${mediaItem.mimeType}")
        val uri = Uri.parse(mediaItem.baseUrl)
        Log.d("View", "uri:${uri}")

        val imageView = holder.imageView
//        val videoView = holder.videoView
        val isImage = mediaItem.mimeType.startsWith("image") || true
        if (isImage) {
            Glide.with(context)
                .load(uri)
                .into(holder.imageView)
//            imageView.visibility = View.VISIBLE
//            videoView.visibility = View.INVISIBLE
        } else {
//            videoView.setVideoURI(Uri.parse("${mediaItem.baseUrl}=dv"))
//            videoView.start()
//            imageView.visibility = View.INVISIBLE
//            videoView.visibility = View.VISIBLE
        }

        holder.itemView.setOnClickListener {
            val dialog = Dialog(context)
            dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            if (mediaItem.mimeType.startsWith("image")) {
                dialog.setContentView(R.layout.dialog_image)
                val dialogImageView = dialog.findViewById<ImageView>(R.id.imageView)
                Glide.with(context)
                    .load(uri)
                    .into(dialogImageView)
            } else {
                dialog.setContentView(R.layout.dialog_video)
//                val dialogVideoView = dialog.findViewById<VideoView>(R.id.videoView)
//                dialogVideoView.setVideoURI(Uri.parse("${mediaItem.baseUrl}=dv"))
//                dialogVideoView.start()
                // VideoView を取得する
                val playerView = dialog.findViewById<PlayerView>(R.id.videoView)
                val player = ExoPlayer.Builder(context).build()
                playerView.player = player;
                // .mov ファイルの URL を設定する
                val videoUri = Uri.parse("${mediaItem.baseUrl}=dv")

                val mediaItem: androidx.media3.common.MediaItem = androidx.media3.common.MediaItem.fromUri(videoUri)
                player.setMediaItem(mediaItem)
                player.prepare()
                player.play()
            }
            dialog.show()
        }
    }

    override fun getItemCount(): Int {
        return mediaItems.size
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.imageView)
//        val videoView: VideoView = view.findViewById(R.id.videoView)
    }
}
