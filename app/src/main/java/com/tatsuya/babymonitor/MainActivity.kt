package com.tatsuya.babymonitor

import android.content.Intent
import android.content.IntentSender.SendIntentException
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.material.navigation.NavigationView
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.google.photos.library.v1.PhotosLibraryClient
import com.google.photos.library.v1.PhotosLibrarySettings
import com.tatsuya.babymonitor.databinding.ActivityMainBinding
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private val REQUEST_AUTHORIZE = 123
    private val Scope : String = "https://www.googleapis.com/auth/photoslibrary.readonly"
    private val TAG = "LOGIN"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

//        binding.appBarMain.fab.setOnClickListener { view ->
//            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                .setAction("Action", null).show()
//        }
        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow, R.id.nav_live
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
//        navController.setGraph()

//        GoogleSignIn.getClient(this, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
//        signIn()
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_settings -> {
            startActivity(Intent(applicationContext, SettingsActivity::class.java))
            true
        }

        else -> {
            // If we got here, the user's action was not recognized.
            // Invoke the superclass to handle it.
            super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        initMenu()
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun initMenu() {
        val birthday = LocalDate.of(2023, 9, 18)
        val today = LocalDate.now()
        val period = Period.between(birthday, today)
        val days = ChronoUnit.DAYS.between(birthday, today)
        val age = findViewById<TextView>(R.id.textAge)
        age.text = "${period.years}歳${period.months}か月${period.days}日(${days}日目)"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        Log.d(TAG, "requestCode:${requestCode}")
        when (requestCode) {
            REQUEST_AUTHORIZE -> {
                val authorizationResult = Identity.getAuthorizationClient(this)
                    .getAuthorizationResultFromIntent(data)
                Log.d(TAG, "authorizationResult:${authorizationResult.accessToken}")
                test(authorizationResult.accessToken!!)
            }
        }
    }

    private fun signIn() {
        val requestedScopes = listOf(Scope(Scope))
        val authorizationRequest =
            AuthorizationRequest.builder().setRequestedScopes(requestedScopes).build()
        Identity.getAuthorizationClient(this)
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
                    } catch (e: SendIntentException) {
                        Log.e(
                            TAG,
                            "Couldn't start Authorization UI: " + e.localizedMessage
                        )
                    }
                } else {
                    // Access already granted, continue with user action
                    Log.d(TAG, "Access already granted:${authorizationResult.accessToken}")
                    test(authorizationResult.accessToken!!)
                }
            }
            .addOnFailureListener { e: Exception? ->
                Log.e(
                    TAG,
                    "Failed to authorize",
                    e
                )
            }
    }

    private fun test(token: String) {
        val accessToken = AccessToken.newBuilder().setTokenValue(token).build()
        Log.d(TAG, "accessToken:${accessToken.tokenValue}")
        val credentials = GoogleCredentials.newBuilder()
            .setAccessToken(accessToken)
            .build()
        val settings = PhotosLibrarySettings.newBuilder()
            .setCredentialsProvider(
                FixedCredentialsProvider.create(credentials)
            )
            .build()

        try {
            PhotosLibraryClient.initialize(settings).use { photosLibraryClient ->

                // Create a new Album  with at title
                val albums = photosLibraryClient.listAlbums()
                Toast.makeText(this, "Done!", Toast.LENGTH_SHORT).show()
                Log.d("Photos", "${albums.iterateAll().count()}")
//                            albums.iterateAll().forEach { album -> Log.d("test", album.title) }
            }
        } catch (e: ApiException) {
            // Error during album creation
        }
    }
}