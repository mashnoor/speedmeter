package fly.speedmeter.grub;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.gc.materialdesign.views.ProgressBarCircularIndeterminate;
import com.gc.materialdesign.widgets.Dialog;
import com.google.gson.Gson;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;
import com.melnykov.fab.FloatingActionButton;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.Locale;





public class MainActivity extends Activity implements LocationListener, GpsStatus.Listener {

    private SharedPreferences  sharedPreferences;
    private LocationManager mLocationManager;
    private static Data data;

    private FloatingActionButton fab;
    private FloatingActionButton refresh;
    private ProgressBarCircularIndeterminate progressBarCircularIndeterminate;
    private TextView satellite;
    private TextView status;
    private TextView accuracy;
    private TextView currentSpeed;
    private TextView maxSpeed;
    private TextView averageSpeed;

    private boolean logData = false;

    private Data.OnGpsServiceUpdate onGpsServiceUpdate;

    private boolean firstfix, firstLog = true;

    String log = "";
    int generation = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Dexter.withActivity(this).withPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE).withListener(new PermissionListener() {
            @Override
            public void onPermissionGranted(PermissionGrantedResponse response) {

            }

            @Override
            public void onPermissionDenied(PermissionDeniedResponse response) {

            }

            @Override
            public void onPermissionRationaleShouldBeShown(PermissionRequest permission, PermissionToken token) {

            }
        }).check();

        data = new Data(onGpsServiceUpdate);

        fab = findViewById(R.id.fab);
        fab.setVisibility(View.VISIBLE);

        refresh = (FloatingActionButton) findViewById(R.id.refresh);
        refresh.setVisibility(View.INVISIBLE);


        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        satellite = findViewById(R.id.satellite);
        status = findViewById(R.id.status);
        accuracy = findViewById(R.id.accuracy);
        maxSpeed = findViewById(R.id.maxSpeed);
        averageSpeed = findViewById(R.id.averageSpeed);

        currentSpeed = findViewById(R.id.currentSpeed);
        progressBarCircularIndeterminate = (ProgressBarCircularIndeterminate) findViewById(R.id.progressBarCircularIndeterminate);



        onGpsServiceUpdate = new Data.OnGpsServiceUpdate() {
            @Override
            public void update() {
                double maxSpeedTemp = data.getMaxSpeed();
                double distanceTemp = data.getDistance();
                double averageTemp;
                if (sharedPreferences.getBoolean("auto_average", false)){
                    averageTemp = data.getAverageSpeedMotion();
                }else{
                    averageTemp = data.getAverageSpeed();
                }

                String speedUnits;
                String distanceUnits;
                if (sharedPreferences.getBoolean("miles_per_hour", false)) {
                    maxSpeedTemp *= 0.62137119;
                    distanceTemp = distanceTemp / 1000.0 * 0.62137119;
                    averageTemp *= 0.62137119;
                    speedUnits = "mi/h";
                    distanceUnits = "mi";
                } else {
                    speedUnits = "km/h";
                    if (distanceTemp <= 1000.0) {
                        distanceUnits = "m";
                    } else {
                        distanceTemp /= 1000.0;
                        distanceUnits = "km";
                    }
                }

                SpannableString s = new SpannableString(String.format("%.0f %s", maxSpeedTemp, speedUnits));
                s.setSpan(new RelativeSizeSpan(0.5f), s.length() - speedUnits.length() - 1, s.length(), 0);
                maxSpeed.setText(s);

                s = new SpannableString(String.format("%.0f %s", averageTemp, speedUnits));
                s.setSpan(new RelativeSizeSpan(0.5f), s.length() - speedUnits.length() - 1, s.length(), 0);
                averageSpeed.setText(s);

                s = new SpannableString(String.format("%.3f %s", distanceTemp, distanceUnits));
                s.setSpan(new RelativeSizeSpan(0.5f), s.length() - distanceUnits.length() - 1, s.length(), 0);

            }
        };



    }

    public void onFabClick(View v){
        if (!data.isRunning()) {
            fab.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_pause));
            data.setRunning(true);
            data.setFirstTime(true);
            startService(new Intent(getBaseContext(), GpsServices.class));
            refresh.setVisibility(View.INVISIBLE);
        }else{
            fab.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_play));
            data.setRunning(false);
            status.setText("");
            stopService(new Intent(getBaseContext(), GpsServices.class));
            refresh.setVisibility(View.VISIBLE);
        }
    }

    public void onRefreshClick(View v){
        resetData();
        stopService(new Intent(getBaseContext(), GpsServices.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        firstfix = true;
        if (!data.isRunning()){
            Gson gson = new Gson();
            String json = sharedPreferences.getString("data", "");
            data = gson.fromJson(json, Data.class);
        }
        if (data == null){
            data = new Data(onGpsServiceUpdate);
        }else{
            data.setOnGpsServiceUpdate(onGpsServiceUpdate);
        }

        if (mLocationManager.getAllProviders().indexOf(LocationManager.GPS_PROVIDER) >= 0) {
            Dexter.withActivity(MainActivity.this).withPermission(Manifest.permission.ACCESS_FINE_LOCATION).withListener(new PermissionListener() {
                @Override
                public void onPermissionGranted(PermissionGrantedResponse response) {
                    mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500, 0, MainActivity.this);
                }

                @Override
                public void onPermissionDenied(PermissionDeniedResponse response) {

                }

                @Override
                public void onPermissionRationaleShouldBeShown(PermissionRequest permission, PermissionToken token) {

                }
            }).check();

        } else {
            Log.w("MainActivity", "No GPS location provider found. GPS data display will not be available.");
        }

        if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            showGpsDisabledDialog();
        }

        Dexter.withActivity(MainActivity.this).withPermission(Manifest.permission.ACCESS_FINE_LOCATION).withListener(new PermissionListener() {
            @Override
            public void onPermissionGranted(PermissionGrantedResponse response) {
                mLocationManager.addGpsStatusListener(MainActivity.this);
            }

            @Override
            public void onPermissionDenied(PermissionDeniedResponse response) {

            }

            @Override
            public void onPermissionRationaleShouldBeShown(PermissionRequest permission, PermissionToken token) {

            }
        }).check();

    }

    @Override
    protected void onPause() {
        super.onPause();
        mLocationManager.removeUpdates(this);
        mLocationManager.removeGpsStatusListener(this);
        SharedPreferences.Editor prefsEditor = sharedPreferences.edit();
        Gson gson = new Gson();
        String json = gson.toJson(data);
        prefsEditor.putString("data", json);
        prefsEditor.commit();
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        stopService(new Intent(getBaseContext(), GpsServices.class));
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent intent = new Intent(this, Settings.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location.hasAccuracy()) {
            double acc = location.getAccuracy();
            String units;
            if (sharedPreferences.getBoolean("miles_per_hour", false)) {
                units = "ft";
                acc *= 3.28084;
            } else {
                units = "m";
            }
            SpannableString s = new SpannableString(String.format("%.0f %s", acc, units));
            s.setSpan(new RelativeSizeSpan(0.75f), s.length()-units.length()-1, s.length(), 0);
            accuracy.setText(s);

            if (firstfix){
                status.setText("");
                fab.setVisibility(View.VISIBLE);
                if (!data.isRunning() && !TextUtils.isEmpty(maxSpeed.getText())) {
                    refresh.setVisibility(View.VISIBLE);
                }
                firstfix = false;
            }
        }else{
            firstfix = true;
        }

        if (location.hasSpeed()) {
            progressBarCircularIndeterminate.setVisibility(View.GONE);
            double speed = location.getSpeed() * 3.6;
            String units;
            if (sharedPreferences.getBoolean("miles_per_hour", false)) { // Convert to MPH
                speed *= 0.62137119;
                units = "mi/h";
            } else {
                units = "km/h";
            }
            SpannableString s = new SpannableString(String.format(Locale.ENGLISH, "%.0f %s", speed, units));
            s.setSpan(new RelativeSizeSpan(0.25f), s.length()-units.length()-1, s.length(), 0);
            currentSpeed.setText(s);
            if(logData)
            {
                if(firstLog)
                {

                    String headers = "Generation" + ",";

                    headers += "Timestamp" + ",";
                    headers += "Speed" + ",";
                    log = headers + "\n";
                    firstLog = false;
                }
                else
                {
                    Calendar c = Calendar.getInstance();
                    String currTime = "";
                    currTime += c.get(Calendar.HOUR) + ":";
                    currTime += c.get(Calendar.MINUTE) + ":";
                    currTime += c.get(Calendar.SECOND);


                    log += System.getProperty("line.separator");
                    log += generation++ + ",";
                    //log += System.currentTimeMillis() - logTime + ",";
                    log += currTime + ",";
                    log += speed + "\n";

                }
            }
        }

    }

    @Override
    public void onGpsStatusChanged (int event) {
        switch (event) {
            case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
                GpsStatus gpsStatus = mLocationManager.getGpsStatus(null);
                int satsInView = 0;
                int satsUsed = 0;
                Iterable<GpsSatellite> sats = gpsStatus.getSatellites();
                for (GpsSatellite sat : sats) {
                    satsInView++;
                    if (sat.usedInFix()) {
                        satsUsed++;
                    }
                }
                satellite.setText(String.valueOf(satsUsed) + "/" + String.valueOf(satsInView));
                if (satsUsed == 0) {
                    fab.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_play));
                    data.setRunning(false);
                    status.setText("");
                    stopService(new Intent(getBaseContext(), GpsServices.class));
                    fab.setVisibility(View.INVISIBLE);
                    refresh.setVisibility(View.INVISIBLE);
                    accuracy.setText("");
                    status.setText(getResources().getString(R.string.waiting_for_fix));
                    firstfix = true;
                }
                break;

            case GpsStatus.GPS_EVENT_STOPPED:
                if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    showGpsDisabledDialog();
                }
                break;
            case GpsStatus.GPS_EVENT_FIRST_FIX:
                break;
        }
    }

    public void showGpsDisabledDialog(){
        Dialog dialog = new Dialog(this, getResources().getString(R.string.gps_disabled), getResources().getString(R.string.please_enable_gps));

        dialog.setOnAcceptButtonClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent("android.settings.LOCATION_SOURCE_SETTINGS"));
            }
        });
        dialog.show();
    }

    public void resetData(){
        fab.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_play));
        refresh.setVisibility(View.INVISIBLE);

        maxSpeed.setText("");
        averageSpeed.setText("");

        data = new Data(onGpsServiceUpdate);
    }

    public static Data getData() {
        return data;
    }

    @Override
    public void onBackPressed(){
        Intent a = new Intent(Intent.ACTION_MAIN);
        a.addCategory(Intent.CATEGORY_HOME);
        a.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(a);
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {}

    @Override
    public void onProviderEnabled(String s) {}

    @Override
    public void onProviderDisabled(String s) {}


    public void goLog(View v)
    {
        Button btn = (Button) v;

        if(btn.getText().equals("STRT"))
        {
            logData = true;
            firstLog = true;
            btn.setText("STOP");


        }
        else
        {
            writeLogToFile(log);
            logData = false;
            firstLog = true;
            btn.setText("STRT");
            generation = 0;
        }
    }
    private void writeLogToFile(String log) {

        Log.d("--------log", log);

        Calendar c = Calendar.getInstance();
        String filename = "SpeedMeter-" + c.get(Calendar.YEAR)
                + "-" + c.get(Calendar.DAY_OF_WEEK_IN_MONTH) + "-"
                + c.get(Calendar.HOUR) + "-" + c.get(Calendar.HOUR) + "-"
                + c.get(Calendar.MINUTE) + "-" + c.get(Calendar.SECOND)
                + ".csv";

        File dir = new File(Environment.getExternalStorageDirectory()
                + File.separator + "SpeedMeter" + File.separator
                + "Logs" + File.separator + "Acceleration");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File file = new File(dir, filename);

        FileOutputStream fos;
        byte[] data = log.getBytes();
        try {
            fos = new FileOutputStream(file);
            fos.write(data);
            fos.flush();
            fos.close();

            CharSequence text = "Log Saved";
            int duration = Toast.LENGTH_SHORT;

            Toast toast = Toast.makeText(this, text, duration);
            toast.show();
        } catch (FileNotFoundException e) {
            CharSequence text = e.toString();
            int duration = Toast.LENGTH_SHORT;

            Toast toast = Toast.makeText(this, text, duration);
            toast.show();
        } catch (IOException e) {
            // handle exception
        } finally {
            // Update the MediaStore so we can view the file without rebooting.
            // Note that it appears that the ACTION_MEDIA_MOUNTED approach is
            // now blocked for non-system apps on Android 4.4.
            MediaScannerConnection.scanFile(this, new String[]
                            {"file://" + Environment.getExternalStorageDirectory()}, null,
                    new MediaScannerConnection.OnScanCompletedListener() {
                        @Override
                        public void onScanCompleted(final String path,
                                                    final Uri uri) {

                        }
                    });
        }
    }

}
