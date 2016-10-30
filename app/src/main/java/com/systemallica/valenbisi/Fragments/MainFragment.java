package com.systemallica.valenbisi.Fragments;

import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.maps.android.geojson.GeoJsonFeature;
import com.google.maps.android.geojson.GeoJsonLayer;
import com.google.maps.android.geojson.GeoJsonPointStyle;
import com.systemallica.valenbisi.R;
import com.systemallica.valenbisi.WebRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;




public class MainFragment extends Fragment implements OnMapReadyCallback {

    private MapView mapView;
    private GoogleMap mMap;
    UiSettings mapSettings;
    int MY_LOCATION_REQUEST_CODE = 1;
    int carrilLayer = 0;
    boolean estacionesLayer = true;
    int onFoot=1;
    int counter = 0;
    View view;
    private final static String mLogTag = "GeoJsonDemo";
    private final static String url = "https://api.jcdecaux.com/vls/v1/stations?contract=Valence&apiKey=adcac2d5b367dacef9846586d12df1bf7e8c7fcd"; // api request of all valencia stations' data
    public static final String PREFS_NAME = "MyPrefsFile";

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        view = inflater.inflate(R.layout.fragment_main, container, false);
        return view;
    }
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState){

        mapView = (MapView) view.findViewById(R.id.map);
        mapView.onCreate(savedInstanceState);
        mapView.onResume();

        //Change toolbar title
        getActivity().setTitle(R.string.nav_map);

        //Check internet
        final ConnectivityManager cm =
                (ConnectivityManager)getActivity().getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        final boolean isConnected = activeNetwork != null &&
                activeNetwork.isConnected();

        //React to the check
        if(isConnected) {
            mapView.getMapAsync(this);
        }


        /*mAdView.setAdListener(new AdListener() {
            @Override
            public void onAdLoaded() {
                Toast.makeText(getActivity().getApplicationContext(), "Ad is loaded!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAdClosed() {
                Toast.makeText(getActivity().getApplicationContext(), "Ad is closed!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAdFailedToLoad(int errorCode) {
                Toast.makeText(getActivity().getApplicationContext(), "Ad failed to load! error code: " + errorCode, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAdLeftApplication() {
                Toast.makeText(getActivity().getApplicationContext(), "Ad left application!", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAdOpened() {
                Toast.makeText(getActivity().getApplicationContext(), "Ad is opened!", Toast.LENGTH_SHORT).show();
            }
        });*/
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {

        mMap = googleMap;
        final SharedPreferences settings = getActivity().getApplicationContext().getSharedPreferences(PREFS_NAME, 0);
        boolean mapView = settings.getBoolean("mapView", false);

        //Check for sdk >= 23

        if (Build.VERSION.SDK_INT >= 23) {
            //Check location permission
            if (getActivity().checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_LOCATION_REQUEST_CODE);

            } else {
                mMap.setMyLocationEnabled(true);
            }
        } else {
            mMap.setMyLocationEnabled(true);
        }


        ///////here


        //Set map zoom controls
        mapSettings = mMap.getUiSettings();
        mapSettings.setZoomControlsEnabled(false);

        //Set type of map and min zoom
        if(mapView){
            mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
        }
        else {
            mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        }
        mMap.setMinZoomPreference(12);

        // Move the camera to Valencia
        LatLng valencia = new LatLng(39.469, -0.378);
        mMap.moveCamera(CameraUpdateFactory.newLatLng(valencia));

        //Add GeoJSON layer of bike lanes and handle errors

        try {
            //lanes layer

            final Button btn_carril = (Button) view.findViewById(R.id.btnCarrilToggle);
            final GeoJsonLayer carril = new GeoJsonLayer(mMap, R.raw.oficialcarril, getActivity().getApplicationContext());
            final Drawable myDrawableLaneOn = ContextCompat.getDrawable(getActivity().getApplicationContext(), R.drawable.ic_road_variant_black_24dp);
            final Drawable myDrawableLaneOff = ContextCompat.getDrawable(getActivity().getApplicationContext(), R.drawable.ic_road_variant_off_black_24dp);

            btn_carril.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (carrilLayer == 0) {
                        btn_carril.setCompoundDrawablesWithIntrinsicBounds(myDrawableLaneOn, null, null, null);
                        carril.addLayerToMap();
                        carrilLayer = 1;
                    } else {
                        btn_carril.setCompoundDrawablesWithIntrinsicBounds(myDrawableLaneOff, null, null, null);
                        carril.removeLayerFromMap();
                        carrilLayer = 0;
                    }

                }
            });

        } catch (IOException e) {

            Log.e(mLogTag, "GeoJSON file could not be read");

        } catch (JSONException e) {

            Log.e(mLogTag, "GeoJSON file could not be converted to a JSONObject");
        }


        new GetStations().execute();

    }
    //React to permission dialog
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == MY_LOCATION_REQUEST_CODE) {
            if (ContextCompat.checkSelfPermission(getActivity().getApplicationContext(), android.Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                mMap.setMyLocationEnabled(true);
            } else {
                Toast.makeText(getActivity().getApplicationContext(), R.string.no_location_permission, Toast.LENGTH_SHORT).show();

            }
        }
    }

    private class GetStations extends AsyncTask<Void, Void, GeoJsonLayer> {

        //Load default marker icons
        BitmapDescriptor icon_green = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN);
        BitmapDescriptor icon_orange = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE);
        BitmapDescriptor icon_yellow = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW);
        BitmapDescriptor icon_red = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED);

        //BitmapDescriptor icon_reed = (BitmapDescriptorFactory.fromResource(R.drawable.splash_inverted));

        final Button btn_estaciones = (Button) view.findViewById(R.id.btnEstacionesToggle);
        final Button btnOnFootToggle = (Button) view.findViewById(R.id.btnOnFootToggle);
        final Button btnRefresh = (Button) view.findViewById(R.id.btnRefresh);
        final Drawable myDrawableBike = ContextCompat.getDrawable(getActivity().getApplicationContext(), R.drawable.ic_directions_bike_black_24dp);
        final Drawable myDrawableWalk = ContextCompat.getDrawable(getActivity().getApplicationContext(), R.drawable.ic_directions_walk_black_24dp);
        final Drawable myDrawableStationsOn = ContextCompat.getDrawable(getActivity().getApplicationContext(), R.drawable.ic_place_black_24dp);
        final Drawable myDrawableStationsOff = ContextCompat.getDrawable(getActivity().getApplicationContext(), R.drawable.ic_map_marker_off_black_24dp);

        @Override
        protected GeoJsonLayer doInBackground(Void... params) {


            // Creating service handler class instance
            WebRequest webreq = new WebRequest();

            // Making a request to url and getting response
            String jsonStr = webreq.makeWebServiceCall(url, WebRequest.GET);

            try {
                if(!jsonStr.equals("")) {
                    JSONArray array = new JSONArray(jsonStr);

                    final GeoJsonLayer layer = new GeoJsonLayer(mMap, R.raw.valencia, getActivity().getApplicationContext());

                    for (counter = 0; counter < array.length(); counter++) {

                        JSONObject object = array.getJSONObject(counter);

                        for (GeoJsonFeature feature : layer.getFeatures()) {  //loop through features
                            //Add each number and address to its correspondent marker

                            if (object.getString("number").equals(feature.getProperty("Number"))) {

                                GeoJsonPointStyle pointStyle = new GeoJsonPointStyle();
                                pointStyle.setTitle(feature.getProperty("Address"));
                                pointStyle.setSnippet("Huecos: " + object.getInt("available_bike_stands") + " - Bicis: " + object.getInt("available_bikes"));
                                pointStyle.setAlpha((float)0.5);
                                //set markers colors
                                if (onFoot == 1) {
                                    if (object.getInt("available_bikes") == 0) {
                                        pointStyle.setIcon(icon_red);
                                    } else if (object.getInt("available_bikes") < 5) {
                                        pointStyle.setIcon(icon_orange);
                                    } else if (object.getInt("available_bikes") < 10) {
                                        pointStyle.setIcon(icon_yellow);
                                    } else {
                                        pointStyle.setIcon(icon_green);
                                    }
                                } else {
                                    if (object.getInt("available_bike_stands") == 0) {
                                        pointStyle.setIcon(icon_red);
                                    } else if (object.getInt("available_bike_stands") < 5) {
                                        pointStyle.setIcon(icon_orange);
                                    } else if (object.getInt("available_bike_stands") < 10) {
                                        pointStyle.setIcon(icon_yellow);
                                    } else {
                                        pointStyle.setIcon(icon_green);
                                    }
                                }
                                SharedPreferences settings = getActivity().getApplicationContext().getSharedPreferences(PREFS_NAME, 0);
                                boolean showFavorites = settings.getBoolean("showFavorites", false);
                                boolean currentStationIsFav = settings.getBoolean(feature.getProperty("Address"), false);
                                if(currentStationIsFav){
                                    pointStyle.setAlpha(1);
                                }
                                if(showFavorites){
                                    if(!currentStationIsFav){
                                        pointStyle.setVisible(false);
                                    }
                                }
                                feature.setPointStyle(pointStyle);

                            }
                        }
                    }
                    return layer;
                }
            } catch (JSONException e) {

                Log.e(mLogTag, "JSONArray could not be created");


            } catch (IOException e) {

                Log.e(mLogTag, "GeoJSON file could not be read");

            }

            return null;
        }

        protected void onPostExecute(final GeoJsonLayer layer) {

            if (layer != null) {
                if (estacionesLayer) {
                    layer.addLayerToMap();
                }


                mMap.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {

                    // Use default InfoWindow frame
                    @Override
                    public View getInfoWindow(Marker marker) {
                        return null;
                    }

                    // Defines the contents of the InfoWindow
                    @Override
                    public View getInfoContents(Marker marker) {

                        final Drawable myDrawableFavOn = ContextCompat.getDrawable(getActivity().getApplicationContext(), R.drawable.ic_star_black_24dp);
                        final Drawable myDrawableFavOff = ContextCompat.getDrawable(getActivity().getApplicationContext(), R.drawable.ic_star_outline_black_24dp);

                        // Getting view from the layout file info_window_layout
                        final View v = getActivity().getLayoutInflater().inflate(R.layout.windowlayout, null);

                        // Getting reference to the ImageView/title/snippet
                        TextView title = (TextView) v.findViewById(R.id.title);
                        TextView snippet = (TextView) v.findViewById(R.id.snippet);
                        ImageView btn_star = (ImageView) v.findViewById(R.id.btn_star);

                        title.setText(marker.getTitle());
                        snippet.setText(marker.getSnippet());

                        //Checking if current station is favorite
                        SharedPreferences settings = getActivity().getApplicationContext().getSharedPreferences(PREFS_NAME, 0);
                        boolean currentStationIsFav = settings.getBoolean(marker.getTitle(), false);

                        //Setting correspondent icon
                        if(currentStationIsFav) {
                            btn_star.setImageDrawable(myDrawableFavOn);
                        }else {
                            btn_star.setImageDrawable(myDrawableFavOff);
                        }

                        mMap.setOnInfoWindowClickListener(new GoogleMap.OnInfoWindowClickListener() {
                            @Override
                            public void onInfoWindowClick(Marker marker) {

                            SharedPreferences settings = getActivity().getApplicationContext().getSharedPreferences(PREFS_NAME, 0);
                            boolean currentStationIsFav = settings.getBoolean(marker.getTitle(), false);
                            boolean showFavorites = settings.getBoolean("showFavorites", false);


                            if(currentStationIsFav){
                                marker.setAlpha((float)0.5);
                                if(showFavorites) {
                                    marker.setVisible(false);
                                }
                                marker.showInfoWindow();
                                SharedPreferences.Editor editor = settings.edit();
                                editor.putBoolean(marker.getTitle(), false);
                                editor.apply();
                            } else {
                                marker.setAlpha(1);
                                marker.showInfoWindow();
                                SharedPreferences.Editor editor = settings.edit();
                                editor.putBoolean(marker.getTitle(), true);
                                editor.apply();
                            }
                                marker.showInfoWindow();
                                Log.e("map marker", "marker is  " + marker.getTitle());

                            }
                        });

                        // Returning the view containing InfoWindow contents
                        return v;
                    }
                });

                //Toggle Stations
                btn_estaciones.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        final SharedPreferences settings = getActivity().getApplicationContext().getSharedPreferences(PREFS_NAME, 0);
                        boolean showFavorites = settings.getBoolean("showFavorites", false);

                        for (GeoJsonFeature feature : layer.getFeatures()) {
                            GeoJsonPointStyle pointStyle = feature.getPointStyle();
                            boolean currentStationIsFav = settings.getBoolean(feature.getProperty("Address"), false);
                               if (estacionesLayer) {
                                   pointStyle.setVisible(false);
                                }else {
                                   if(showFavorites && currentStationIsFav){
                                      pointStyle.setVisible(true);
                                   }else if(!showFavorites){
                                      pointStyle.setVisible(true);
                                   }
                                }
                                feature.setPointStyle(pointStyle);
                        }

                        if (estacionesLayer) {
                            btn_estaciones.setCompoundDrawablesWithIntrinsicBounds(myDrawableStationsOff, null, null, null);
                            estacionesLayer = false;
                        }else{
                            btn_estaciones.setCompoundDrawablesWithIntrinsicBounds(myDrawableStationsOn, null, null, null);
                            estacionesLayer = true;
                        }
                    }
                });

                //Toggle onFoot/onBike
                btnOnFootToggle.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        layer.removeLayerFromMap();
                        if (onFoot == 1) {
                            onFoot = 0;
                            btnOnFootToggle.setCompoundDrawablesWithIntrinsicBounds(myDrawableBike, null, null, null);
                            new GetStations().execute();

                        } else {
                            onFoot = 1;
                            btnOnFootToggle.setCompoundDrawablesWithIntrinsicBounds(myDrawableWalk, null, null, null);
                            new GetStations().execute();
                        }

                    }
                });

                //Reload data
                btnRefresh.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        layer.removeLayerFromMap();
                        new GetStations().execute();
                    }
                });
            }
            else{
                if(isAdded()) {
                    Toast toast = Toast.makeText(getActivity().getApplicationContext(), "No se han podido cargar los datos, prueba más tarde", Toast.LENGTH_LONG);
                    toast.show();
                }
            }
        }

    }
}
