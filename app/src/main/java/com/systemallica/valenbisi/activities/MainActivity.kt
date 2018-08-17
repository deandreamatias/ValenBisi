package com.systemallica.valenbisi.activities

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.support.design.widget.NavigationView
import android.support.v4.app.FragmentTransaction
import android.support.v4.content.ContextCompat
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.MenuItem
import android.view.View

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.systemallica.valenbisi.BuildConfig
import com.systemallica.valenbisi.ContextWrapper
import com.systemallica.valenbisi.fragments.AboutFragment
import com.systemallica.valenbisi.fragments.DonateFragment
import com.systemallica.valenbisi.fragments.MapsFragment
import com.systemallica.valenbisi.fragments.SettingsFragment
import com.systemallica.valenbisi.R

import java.io.IOException
import java.util.Locale

import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import android.support.v7.preference.PreferenceManager.getDefaultSharedPreferences
import android.view.View.GONE
import com.systemallica.valenbisi.R.layout.activity_main


const val PREFS_NAME = "MyPrefsFile"

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener, PurchasesUpdatedListener {

    private var context: Context? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        context = this
        val userSettings = getDefaultSharedPreferences(context!!)
        val navBar = userSettings.getBoolean("navBar", true)

        val colorPrimary = ContextCompat.getColor(context!!, R.color.colorPrimary)

        //Apply preferences navBar preference
        if (navBar && android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.navigationBarColor = colorPrimary
        }

        //Recents implementation
        val recentsIcon = BitmapFactory.decodeResource(context!!.resources, R.drawable.splash_inverted)//Choose the icon

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val description = ActivityManager.TaskDescription(null, recentsIcon, colorPrimary)
            this.setTaskDescription(description)
        }

        //set view to main
        setContentView(activity_main)

        //init toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        //init drawer
        val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)
        val toggle = ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer.addDrawerListener(toggle)
        toggle.syncState()

        //init navigation view
        val navigationView: NavigationView = findViewById(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener(this)

        //Inflate main fragment
        val fragmentTransaction = supportFragmentManager.beginTransaction()

        if (savedInstanceState == null) {
            // Change fragment
            fragmentTransaction.replace(R.id.containerView, MapsFragment()).commit()

            navigationView.menu.getItem(0).isChecked = true
        }

        //Check internet
        val cm = context!!.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val activeNetwork = cm.activeNetworkInfo
        val isConnected = activeNetwork != null && activeNetwork.isConnected

        //React to the check
        if (!isConnected) {
            //Prompt an alert dialog to the user
            AlertDialog.Builder(context!!)
                    .setTitle(R.string.no_internet)
                    .setMessage(R.string.no_internet_message)
                    .setPositiveButton(R.string.close) { _, _ -> System.exit(0) }

                    .setNegativeButton(R.string.continuer) { _, _ ->
                        //Do nothing
                    }

                    .setIcon(R.drawable.ic_report_problem_black_24dp)
                    .show()
        } else {
            getLatestVersion()
        }

        val settings = context!!.getSharedPreferences(PREFS_NAME, 0)
        val donationPurchased = settings.getBoolean("donationPurchased", false)

        // Ads management
        MobileAds.initialize(this, "ca-app-pub-7754892948346904/1371669271")
        val mAdView = findViewById<AdView>(R.id.adView)

        if (!donationPurchased) {
            // Ad request and load
            val adRequest = AdRequest.Builder().build()
            mAdView.loadAd(adRequest)
            mAdView.visibility = GONE
        }

        // Check license
        val mBillingClient: BillingClient = BillingClient.newBuilder(this@MainActivity).setListener(this).build()
        mBillingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(@BillingClient.BillingResponse billingResponseCode: Int) {
                if (billingResponseCode == BillingClient.BillingResponse.OK) {
                    // The billing client is ready
                    // Get past purchases
                    val purchasesResult = mBillingClient.queryPurchases(BillingClient.SkuType.INAPP)
                    val purchases = purchasesResult.purchasesList
                    for (purchase in purchases) {
                        val mPurchase = purchase as Purchase
                        val purchaseSku = mPurchase.sku
                        // The donation package is already bought, apply license
                        if (purchaseSku == "donation_upgrade") {
                            val editor = settings.edit()
                            editor.putBoolean("donationPurchased", true)
                            editor.apply()
                            mAdView.visibility = GONE
                            mAdView.destroy()
                            // Consume purchase
                        }
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                // Try to restart the connection on the next request to
                // Google Play by calling the startConnection() method.
            }

        })
    }

    override fun onPurchasesUpdated(@BillingClient.BillingResponse responseCode: Int, purchases: List<Purchase>?) {
        //if (responseCode == BillingClient.BillingResponse.OK && purchases != null) {
        // Success
        //} else if (responseCode == BillingClient.BillingResponse.USER_CANCELED) {
        // Handle an error caused by a user cancelling the purchase flow.
        //} else {
        // Handle any other error codes.
        //}
    }

    override fun attachBaseContext(newBase: Context) {
        // Changing language
        val settings = newBase.getSharedPreferences(PREFS_NAME, 0)
        val locale = settings.getString("locale", "default_locale")

        // Get default system locale
        val config = newBase.resources.configuration
        val sysLocale: Locale
        sysLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ContextWrapper.getSystemLocale(config)
        } else {
            ContextWrapper.getSystemLocaleLegacy(config)
        }

        // Apply default locale if user didn't specify a locale
        if (locale == "default_locale") {
            super.attachBaseContext(ContextWrapper.wrap(newBase, sysLocale.language))
            // Else apply user choice
        } else {
            super.attachBaseContext(ContextWrapper.wrap(newBase, locale!!))
        }
    }


    override fun onBackPressed() {
        val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        val id = item.itemId
        val mAdView = findViewById<AdView>(R.id.adView)

        val settings = getSharedPreferences(PREFS_NAME, 0)
        val removedAds = settings.getBoolean("removedAds", false)

        // Load fragment transaction
        val fragmentTransaction = supportFragmentManager.beginTransaction()

        when (id) {
            R.id.nav_map -> {
                mAdView.visibility = GONE

                // Change toolbar title
                this.setTitle(R.string.nav_map)

                fragmentTransaction.replace(R.id.containerView, MapsFragment())
            }
            R.id.nav_settings -> {
                if (!removedAds) {
                    mAdView.visibility = View.VISIBLE
                }

                // Change fragment
                fragmentTransaction.replace(R.id.containerView, SettingsFragment())
            }
            R.id.nav_donate -> {
                if (!removedAds) {
                    mAdView.visibility = View.VISIBLE
                }

                // Change fragment
                fragmentTransaction.replace(R.id.containerView, DonateFragment())
            }
            R.id.nav_share -> {
                try {
                    val intent = Intent(Intent.ACTION_SEND)
                    intent.type = "text/plain"
                    val sAux = "https://play.google.com/store/apps/details?id=com.systemallica.valenbisi"
                    intent.putExtra(Intent.EXTRA_TEXT, sAux)
                    startActivity(intent)
                } catch (e: Exception) {
                    e.toString()
                }
            }
            R.id.nav_about -> {
                if (!removedAds) {
                    mAdView.visibility = View.VISIBLE
                }

                // Change fragment
                fragmentTransaction.replace(R.id.containerView, AboutFragment())

            }
        }

        // Commit fragment
        fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
        fragmentTransaction.commit()

        val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)
        drawer.closeDrawer(GravityCompat.START)
        return true
    }

    private fun getLatestVersion() {
        val client = OkHttpClient()

        val request = Request.Builder()
                .url("https://raw.githubusercontent.com/systemallica/ValenBisi/master/VersionCode")
                .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {

            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response?) {
                response.use {
                    val responseBody = response!!.body()
                    if (!response.isSuccessful)
                        throw IOException("Unexpected code $response")

                    var latestVersionTemp = ""

                    if (responseBody != null) {
                        latestVersionTemp = responseBody.string()
                    }

                    val latestVersion = latestVersionTemp
                    checkUpdate(latestVersion.trim())
                }
            }
        })
    }

    fun checkUpdate(latestVersion: String) {
        val versionCode = BuildConfig.VERSION_CODE
        val versionGit = Integer.parseInt(latestVersion)

        if (versionCode < versionGit) {

            val settings = getSharedPreferences(PREFS_NAME, 0)
            val noUpdate = settings.getBoolean("noUpdate", false)

            if (!noUpdate) {
                runOnUiThread {
                    val builder = AlertDialog.Builder(this@MainActivity)
                    builder.setTitle(R.string.update_available)
                            .setMessage(R.string.update_message)
                            .setIcon(R.drawable.ic_system_update_black_24dp)
                            .setPositiveButton(R.string.update_ok) { _, _ ->
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.systemallica.valenbisi"))
                                startActivity(browserIntent)
                            }
                            .setNegativeButton(R.string.update_not_now) { _, _ ->
                                // Do nothing
                            }
                            .setNeutralButton(R.string.update_never) { _, _ ->
                                val editor = settings.edit()
                                editor.putBoolean("noUpdate", true)
                                editor.apply()
                            }
                    val dialog = builder.create()
                    dialog.show()
                }
            }
        } else if (versionCode > versionGit) {
            runOnUiThread {
                val builder = AlertDialog.Builder(this@MainActivity)
                builder.setTitle(R.string.alpha_title)
                        .setMessage(R.string.alpha_message)
                        .setPositiveButton(R.string.update_ok) { _, _ ->
                            // Do nothing
                        }
                val dialog = builder.create()
                dialog.show()
            }
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
    }
}
