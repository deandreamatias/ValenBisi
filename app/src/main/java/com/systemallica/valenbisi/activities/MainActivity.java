package com.systemallica.valenbisi.activities;

import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.systemallica.valenbisi.BuildConfig;
import com.systemallica.valenbisi.ContextWrapper;
import com.systemallica.valenbisi.fragments.AboutFragment;
import com.systemallica.valenbisi.fragments.DonateFragment;
import com.systemallica.valenbisi.fragments.MapsFragment;
import com.systemallica.valenbisi.fragments.SettingsFragment;
import com.systemallica.valenbisi.R;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static android.support.v7.preference.PreferenceManager.getDefaultSharedPreferences;
import static android.view.View.GONE;
import static com.systemallica.valenbisi.R.layout.activity_main;


public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, PurchasesUpdatedListener {

    NavigationView navigationView;
    FragmentManager mFragmentManager;
    public static final String PREFS_NAME = "MyPrefsFile";
    Context context = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = this;
        final SharedPreferences userSettings = getDefaultSharedPreferences(context);
        boolean navBar = userSettings.getBoolean("navBar", true);

        int colorPrimary = ContextCompat.getColor(context, R.color.colorPrimary);

        //Apply preferences navBar preference
        if (navBar && android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setNavigationBarColor(colorPrimary);
        }

        //Recents implementation
        //String title = null;  // You can either set the title to whatever you want or just use null and it will default to your app/activity name
        Bitmap recentsIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.splash_inverted);//Choose the icon

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ActivityManager.TaskDescription description = new ActivityManager.TaskDescription(null, recentsIcon, colorPrimary);
            this.setTaskDescription(description);
        }

        //set view to main
        setContentView(activity_main);

        //init toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //init drawer
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        //init navigation view
        navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        //Inflate main fragment
        mFragmentManager = getSupportFragmentManager();
        FragmentTransaction ft = mFragmentManager.beginTransaction();

        if (savedInstanceState == null) {
            // Change fragment
            ft.replace(R.id.containerView, new MapsFragment()).commit();

            navigationView.getMenu().getItem(0).setChecked(true);
        }

        //Check internet
        final ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            final boolean isConnected = activeNetwork != null &&
                    activeNetwork.isConnected();

            //React to the check
            if (!isConnected) {
                //Prompt an alert dialog to the user
                new AlertDialog.Builder(context)
                        .setTitle(R.string.no_internet)
                        .setMessage(R.string.no_internet_message)
                        .setPositiveButton(R.string.close, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                System.exit(0);
                            }
                        })

                        .setNegativeButton(R.string.continuer, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                //Do nothing
                            }
                        })

                        .setIcon(R.drawable.ic_report_problem_black_24dp)
                        .show();
            } else {
                getLatestVersion();
            }
        }

        final SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, 0);
        boolean donationPurchased = settings.getBoolean("donationPurchased", false);

        // Ads management
        MobileAds.initialize(this, "ca-app-pub-7754892948346904/1371669271");
        final AdView mAdView = findViewById(R.id.adView);

        if (!donationPurchased) {
            // Ad request and load
            AdRequest adRequest = new AdRequest.Builder().build();
            mAdView.loadAd(adRequest);
            mAdView.setVisibility(GONE);
        }

        // Check license
        final BillingClient mBillingClient;

        mBillingClient = BillingClient.newBuilder(MainActivity.this).setListener(this).build();
        mBillingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@BillingClient.BillingResponse int billingResponseCode) {
                if (billingResponseCode == BillingClient.BillingResponse.OK) {
                    // The billing client is ready
                    // Get past purchases
                    Purchase.PurchasesResult purchasesResult = mBillingClient.queryPurchases(BillingClient.SkuType.INAPP);
                    List purchases = purchasesResult.getPurchasesList();
                    for (Object purchase : purchases) {
                        Purchase mPurchase = (Purchase) purchase;
                        String purchaseSku = mPurchase.getSku();
                        // The donation package is already bought, apply license
                        if (purchaseSku.equals("donation_upgrade")) {
                            SharedPreferences.Editor editor = settings.edit();
                            editor.putBoolean("donationPurchased", true);
                            editor.apply();
                            mAdView.setVisibility(GONE);
                            mAdView.destroy();
                            // Consume purchase
                        }
                    }
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                // Try to restart the connection on the next request to
                // Google Play by calling the startConnection() method.
            }

        });
    }

    @Override
    public void onPurchasesUpdated(@BillingClient.BillingResponse int responseCode, List<Purchase> purchases) {
        //if (responseCode == BillingClient.BillingResponse.OK && purchases != null) {
        // Success
        //} else if (responseCode == BillingClient.BillingResponse.USER_CANCELED) {
        // Handle an error caused by a user cancelling the purchase flow.
        //} else {
        // Handle any other error codes.
        //}
    }

    @Override
    protected void attachBaseContext(Context newBase) {

        // Changing language
        final SharedPreferences settings = newBase.getSharedPreferences(PREFS_NAME, 0);
        String locale = settings.getString("locale", "default_locale");

        // Get default system locale
        Configuration config = newBase.getResources().getConfiguration();
        Locale sysLocale;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            sysLocale = ContextWrapper.Companion.getSystemLocale(config);
        } else {
            sysLocale = ContextWrapper.Companion.getSystemLocaleLegacy(config);
        }

        // Apply default locale if user didn't specify a locale
        if (locale.equals("default_locale")) {
            super.attachBaseContext(ContextWrapper.Companion.wrap(newBase, sysLocale.getLanguage()));
            // Else apply user choice
        } else {
            super.attachBaseContext(ContextWrapper.Companion.wrap(newBase, locale));
        }
    }


    @Override
    public void onBackPressed() {
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();
        AdView mAdView = findViewById(R.id.adView);

        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        boolean removedAds = settings.getBoolean("removedAds", false);

        // Load fragment transaction
        FragmentTransaction transaction = mFragmentManager.beginTransaction();

        if (id == R.id.nav_map) {

            mAdView.setVisibility(GONE);

            // Change toolbar title
            this.setTitle(R.string.nav_map);

            transaction.replace(R.id.containerView, new MapsFragment());

        } else if (id == R.id.nav_settings) {

            if (!removedAds) {
                mAdView.setVisibility(View.VISIBLE);
            }

            // Change fragment
            transaction.replace(R.id.containerView, new SettingsFragment());

        } else if (id == R.id.nav_donate) {

            if (!removedAds) {
                mAdView.setVisibility(View.VISIBLE);
            }

            // Change fragment
            transaction.replace(R.id.containerView, new DonateFragment());

        } else if (id == R.id.nav_share) {

            try {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                String sAux = "https://play.google.com/store/apps/details?id=com.systemallica.valenbisi";
                i.putExtra(Intent.EXTRA_TEXT, sAux);
                startActivity(i);
            } catch (Exception e) {
                //e.toString();
            }

        } else if (id == R.id.nav_about) {

            if (!removedAds) {
                mAdView.setVisibility(View.VISIBLE);
            }

            // Change fragment
            transaction.replace(R.id.containerView, new AboutFragment());

        }

        // Commit fragment
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        transaction.commit();

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    public void getLatestVersion() {
        final OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url("https://raw.githubusercontent.com/systemallica/ValenBisi/master/VersionCode")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    ResponseBody responseBody = response.body();
                    if (!response.isSuccessful())
                        throw new IOException("Unexpected code " + response);

                    String latestVersionTemp = "";

                    if (responseBody != null) {
                        latestVersionTemp = responseBody.string();
                    }

                    String latestVersion = latestVersionTemp;
                    checkUpdate(latestVersion.trim());

                } finally {
                    if (response != null) {
                        response.close();
                    }
                }
            }
        });
    }

    public void checkUpdate(final String latestVersion) {

        int versionCode = BuildConfig.VERSION_CODE;
        int versionGit = Integer.parseInt(latestVersion);

        if (versionCode < versionGit) {

            SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
            boolean noUpdate = settings.getBoolean("noUpdate", false);

            if (!noUpdate) {
                runOnUiThread(new Runnable() {
                    public void run() {

                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                        builder.setTitle(R.string.update_available)
                                .setMessage(R.string.update_message)
                                .setIcon(R.drawable.ic_system_update_black_24dp)
                                .setPositiveButton(R.string.update_ok, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.systemallica.valenbisi"));
                                        startActivity(browserIntent);
                                    }
                                })
                                .setNegativeButton(R.string.update_not_now, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        // Do nothing
                                    }
                                })
                                .setNeutralButton(R.string.update_never, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        final SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
                                        SharedPreferences.Editor editor = settings.edit();
                                        editor.putBoolean("noUpdate", true);
                                        editor.apply();
                                    }
                                });
                        AlertDialog dialog = builder.create();
                        dialog.show();

                    }
                });
            }
        } else if (versionCode > versionGit) {
            runOnUiThread(new Runnable() {
                public void run() {

                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setTitle(R.string.alpha_title)
                            .setMessage(R.string.alpha_message)
                            //.setIcon(R.drawable.ic_system_update_black_24dp)
                            .setPositiveButton(R.string.update_ok, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    // Do nothing
                                }
                            });
                    AlertDialog dialog = builder.create();
                    dialog.show();

                }
            });
        }
    }

    @Override
    public void onDestroy() {

        super.onDestroy();
    }
}
