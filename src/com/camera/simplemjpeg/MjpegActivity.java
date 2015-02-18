package com.camera.simplemjpeg;

/*

MjPEG Code is by the nice folks at Intel:

Copyright (C) 2000, Intel Corporation, all rights reserved.
Third party copyrights are property of their respective owners.
Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:
Redistribution's of source code must retain the above copyright notice,
this list of conditions and the following disclaimer.
Redistribution's in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.
The name of Intel Corporation may not be used to endorse or promote products
derived from this software without specific prior written permission.
This software is provided by the copyright holders and contributors "as is" and
any express or implied warranties, including, but not limited to, the implied
warranties of merchantability and fitness for a particular purpose are disclaimed.
In no event shall the Intel Corporation or contributors be liable for any direct,
indirect, incidental, special, exemplary, or consequential damages
(including, but not limited to, procurement of substitute goods or services;
loss of use, data, or profits; or business interruption) however caused
and on any theory of liability, whether in contract, strict liability,
or tort (including negligence or otherwise) arising in any way out of
the use of this software, even if advised of the possibility of such damage.

*/

import com.camera.simplemjpeg.NetworkEngine;
import com.camera.simplemjpeg.NetworkCom;
import java.io.IOException;
import java.net.URI;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import android.app.Activity;
import android.hardware.SensorEventListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.Menu;
import android.view.View;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.widget.TextView;
import android.content.Context;
import android.content.pm.PackageManager;
import android.widget.ToggleButton;

public class MjpegActivity extends Activity implements SensorEventListener {

    private TextView tv;
    private SensorManager mSensorManager;
    private Sensor mSensor;

	private static final boolean DEBUG=true;
    private static final String TAG = "MJPEG";

    private MjpegView mv = null;
    String URL;
    
    // for settings (network and resolution)
    private static final int REQUEST_SETTINGS = 0;

    private float MAX_WHEEL_VALUE = 100;

    // Values for up/down control
    private float INITIAL_Z_ANGLE = -30;
    private float MAX_Z = INITIAL_Z_ANGLE + 30; // full forward
    private float MAX_Z_ZERO = INITIAL_Z_ANGLE + 5;
    private float MIN_Z_ZERO = INITIAL_Z_ANGLE - 5;
    private float MIN_Z = INITIAL_Z_ANGLE - 30; // full back
    private float FORWARD_RANGE_MULTIPLIER = MAX_WHEEL_VALUE / (MAX_Z - MAX_Z_ZERO);
    private float BACKWARD_RANGE_MULTIPLIER = MAX_WHEEL_VALUE / (MIN_Z - MIN_Z_ZERO);

    // Values for left-right control
    private float INITIAL_Y_ANGLE = 0;
    private float MAX_Y = INITIAL_Y_ANGLE + 40; // full right
    private float MAX_Y_ZERO = INITIAL_Y_ANGLE + 5;
    private float MIN_Y_ZERO = INITIAL_Y_ANGLE - 5;
    private float MIN_Y = INITIAL_Y_ANGLE - 40; // full left
    private float LEFT_RANGE_MULTIPLIER = MAX_WHEEL_VALUE / (MIN_Y - MIN_Y_ZERO);
    private float RIGHT_RANGE_MULTIPLIER = MAX_WHEEL_VALUE / (MAX_Y - MAX_Y_ZERO);

    private int width = 320;
    private int height = 240;
    private int ip_ad1 = 192;
    private int ip_ad2 = 168;
    private int ip_ad3 = 0;
    private int ip_ad4 = 23;
    private int ip_port = 8080;
    private int ip_control_port = 5005;
    //private String ip_command = "?action=stream";
    private String ip_command = "stream/video.mjpeg";

	final Handler handler = new Handler();

    private NetworkCom _rover;
    private RoverLoop _roverLoop;
    private int _leftWheel, _rightWheel;
    private boolean _runCommunication = false;
    private boolean _roverIsStopped = true;
    private DoRead _streamReader;


    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences preferences = getSharedPreferences("SAVED_VALUES", MODE_PRIVATE);
        width = preferences.getInt("width", width);
        height = preferences.getInt("height", height);
        ip_ad1 = preferences.getInt("ip_ad1", ip_ad1);
        ip_ad2 = preferences.getInt("ip_ad2", ip_ad2);
        ip_ad3 = preferences.getInt("ip_ad3", ip_ad3);
        ip_ad4 = preferences.getInt("ip_ad4", ip_ad4);
        ip_port = preferences.getInt("ip_port", ip_port);
        ip_command = preferences.getString("ip_command", ip_command);
                
        StringBuilder sb = new StringBuilder();
        String s_http = "http://";
        String s_dot = ".";
        String s_colon = ":";
        String s_slash = "/";
        sb.append(s_http);
        sb.append(ip_ad1);
        sb.append(s_dot);
        sb.append(ip_ad2);
        sb.append(s_dot);
        sb.append(ip_ad3);
        sb.append(s_dot);
        sb.append(ip_ad4);
        sb.append(s_colon);
        sb.append(ip_port);
        sb.append(s_slash);
        sb.append(ip_command);
        URL = new String(sb);

        setContentView(R.layout.main);
        mv = (MjpegView) findViewById(R.id.mv);  
        if(mv != null){
        	mv.setResolution(width, height);
        }
        mv.setVisibility(mv.VISIBLE);
        mv.setKeepScreenOn(true);

        setTitle(R.string.title_connecting);

        tv= (TextView)findViewById(R.id.debug_text);
        // Get an instance of the sensor service
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        Log.d(TAG,"onCreate");
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something if sensor accuracy changes.
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {
        //
        // This code basically translates the orientation sensor
        // data in to left and right wheel values
        //
        //float x_rotation = event.values[0]; //not used!
        float y_rotation = (0 - event.values[1]); // left & right
        if (y_rotation < MIN_Y) { y_rotation = MIN_Y; }
        else if (y_rotation > MAX_Y) { y_rotation = MAX_Y; }
        float z_rotation = (0 - event.values[2]); // forward & back
        if (z_rotation < MIN_Z) { z_rotation = MIN_Z; }
        else if (z_rotation > MAX_Z) { z_rotation = MAX_Z; }
        boolean z_zero = false;
        boolean y_zero = false;
        if (z_rotation <= MAX_Z_ZERO && z_rotation >= MIN_Z_ZERO) {
            z_zero = true;
        }
        if (y_rotation <= MAX_Y_ZERO && y_rotation >= MIN_Y_ZERO) {
            y_zero = true;
        }
        if (z_zero && y_zero) {
            _leftWheel = 0;
            _rightWheel = 0;
        }
        else if (z_zero) {
            // if Z is at zero, then we will rotate the wheels in opposite directions
            float val = 0;
            if (y_rotation < INITIAL_Y_ANGLE) {
                // Turn left
                val = 0 - ((y_rotation - MIN_Y_ZERO) * LEFT_RANGE_MULTIPLIER);
                _leftWheel = 0;
                _rightWheel = (int)(0 - val);
            }
            else {
                // Turn Right
                val = (y_rotation - MAX_Y_ZERO) * RIGHT_RANGE_MULTIPLIER;
                _leftWheel = (int)(val);
                _rightWheel = 0;
            }

        }
        else {
            // Z if outside 0 - Therefore we rotate left and right wheels in same direction
            if (z_rotation < INITIAL_Z_ANGLE) {
                // Go backwards
                float val = 0 - ((z_rotation - MIN_Z_ZERO) * BACKWARD_RANGE_MULTIPLIER);
                float val2 = 0;
                if (y_zero) {
                    _leftWheel = (int)val;
                    _rightWheel = (int)val;
                }
                else {
                    if (y_rotation < INITIAL_Y_ANGLE) {
                        // back and to the left
                        val2 = val * (1 - (((y_rotation - MIN_Y_ZERO) * LEFT_RANGE_MULTIPLIER) / MAX_WHEEL_VALUE));
                        _leftWheel = (int)val2;
                        _rightWheel = (int)val;
                    }
                    else {
                        // back and to the right
                        val2 = val * (1 - ((( y_rotation - MAX_Y_ZERO) * RIGHT_RANGE_MULTIPLIER) / MAX_WHEEL_VALUE));
                        _leftWheel = (int)val;
                        _rightWheel = (int)val2;
                    }
                }
            }
            else {
                // Go forwards
                float val = (z_rotation - MAX_Z_ZERO) * FORWARD_RANGE_MULTIPLIER;
                float val2 = 0;
                if (y_zero) {
                    _leftWheel = (int)val;
                    _rightWheel = (int)val;
                }
                else if (y_rotation < INITIAL_Y_ANGLE) {
                    // forward and to the left
                    val2 = val * (1 - (((y_rotation - MIN_Y_ZERO) * LEFT_RANGE_MULTIPLIER) / MAX_WHEEL_VALUE));
                    _leftWheel = (int)val2;
                    _rightWheel = (int)val;
                }
                else {
                    // forward and to the right
                    val2 = val * (1 - ((( y_rotation - MAX_Y_ZERO) * RIGHT_RANGE_MULTIPLIER) / MAX_WHEEL_VALUE));
                    _leftWheel = (int)val;
                    _rightWheel = (int)val2;
                }
            }
        }
        //Log.d(TAG,"Left Motor: " + _leftWheel+"\n"+
        //        "Right Motor: " + _rightWheel);
        tv.setText("Left Motor: " + _leftWheel+"\n"+
                   "Right Motor: " + _rightWheel);
    }
    
    public void onResume() {
    	if(DEBUG) Log.d(TAG,"onResume()");
        super.onResume();
        _streamReader = new DoRead();
        Log.d(TAG, "connecting to: "+URL);
        _streamReader.execute(URL);
        mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
        StringBuilder sb = new StringBuilder();
        sb.append(ip_ad1);
        sb.append('.');
        sb.append(ip_ad2);
        sb.append('.');
        sb.append(ip_ad3);
        sb.append('.');
        sb.append(ip_ad4);
        _initializeCamRover(new String(sb), ip_control_port);
        _runRoverLoop();
    }

    public void onStart() {
    	if(DEBUG) Log.d(TAG,"onStart()");
        super.onStart();
    }
    public void onPause() {
    	if(DEBUG) Log.d(TAG,"onPause()");
        super.onPause();
		mv.stopPlayback();
        _streamReader.cancel(true);
        mSensorManager.unregisterListener(this);
    }

    public void onStop() {
    	if(DEBUG) Log.d(TAG,"onStop()");
        super.onStop();
        _stopRoverLoop();
        _roverLoop = null;
    }

    public void onDestroy() {
    	if(DEBUG) Log.d(TAG,"onDestroy()");
    	
    	//if(mv!=null){
    		//mv.freeCameraMemory();
    	//}
    	
        super.onDestroy();
        _stopRoverLoop();
    }

    private void _initializeCamRover(String ipAddress, int port)
    {
        // only create a new instance if one has not already been created
        if(_rover == null)
        {
            _rover = new NetworkCom(ipAddress, port);
            if(_rover.startCommunication())
            {
                Log.e("Controller", "Did start comm");
            }
        }
    }

    private void _runRoverLoop()
    {
        if(_roverLoop == null)
        {
            _runCommunication = true;
            _roverLoop = new RoverLoop();
            Thread thread = new Thread(_roverLoop);
            thread.start();
        }
    }


    private void _stopRoverLoop()
    {
        _runCommunication = false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.layout.option_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch (item.getItemId()) {
    		case R.id.settings:
    			Intent settings_intent = new Intent(MjpegActivity.this, SettingsActivity.class);
    			settings_intent.putExtra("width", width);
    			settings_intent.putExtra("height", height);
    			settings_intent.putExtra("ip_ad1", ip_ad1);
    			settings_intent.putExtra("ip_ad2", ip_ad2);
    			settings_intent.putExtra("ip_ad3", ip_ad3);
    			settings_intent.putExtra("ip_ad4", ip_ad4);
    			settings_intent.putExtra("ip_port", ip_port);
    			settings_intent.putExtra("ip_command", ip_command);
    			startActivityForResult(settings_intent, REQUEST_SETTINGS);
    			return true;
    	}
    	return false;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    	switch (requestCode) {
    		case REQUEST_SETTINGS:
    			if (resultCode == Activity.RESULT_OK) {
    				width = data.getIntExtra("width", width);
    				height = data.getIntExtra("height", height);
    				ip_ad1 = data.getIntExtra("ip_ad1", ip_ad1);
    				ip_ad2 = data.getIntExtra("ip_ad2", ip_ad2);
    				ip_ad3 = data.getIntExtra("ip_ad3", ip_ad3);
    				ip_ad4 = data.getIntExtra("ip_ad4", ip_ad4);
    				ip_port = data.getIntExtra("ip_port", ip_port);
    				ip_command = data.getStringExtra("ip_command");

    				if(mv!=null){
    					mv.setResolution(width, height);
    				}
    				SharedPreferences preferences = getSharedPreferences("SAVED_VALUES", MODE_PRIVATE);
    				SharedPreferences.Editor editor = preferences.edit();
    				editor.putInt("width", width);
    				editor.putInt("height", height);
    				editor.putInt("ip_ad1", ip_ad1);
    				editor.putInt("ip_ad2", ip_ad2);
    				editor.putInt("ip_ad3", ip_ad3);
    				editor.putInt("ip_ad4", ip_ad4);
    				editor.putInt("ip_port", ip_port);
    				editor.putString("ip_command", ip_command);

    				editor.commit();

    				new RestartApp().execute();
    			}
    			break;
    	}
    }

    public void setImageError(){
    	handler.post(new Runnable() {
    		@Override
    		public void run() {
    			setTitle(R.string.title_imageerror);
    			return;
    		}
    	});
    }
    
    public class DoRead extends AsyncTask<String, Void, MjpegInputStream> {
        protected MjpegInputStream doInBackground(String... url) {
            //TODO: if camera has authentication deal with it and don't just not work
            HttpResponse res = null;         
            DefaultHttpClient httpclient = new DefaultHttpClient(); 
            HttpParams httpParams = httpclient.getParams();
            HttpConnectionParams.setConnectionTimeout(httpParams, 5*1000);
            HttpConnectionParams.setSoTimeout(httpParams, 5*1000);
            if(DEBUG) Log.d(TAG, "1. Sending http request");
            try {
                res = httpclient.execute(new HttpGet(URI.create(url[0])));
                if(DEBUG) Log.d(TAG, "2. Request finished, status = " + res.getStatusLine().getStatusCode());
                if(res.getStatusLine().getStatusCode()==401){
                    //You must turn off camera User Access Control before this will work
                    return null;
                }
                return new MjpegInputStream(res.getEntity().getContent());  
            } catch (ClientProtocolException e) {
            	if(DEBUG){
	                e.printStackTrace();
	                Log.d(TAG, "Request failed-ClientProtocolException", e);
            	}
                //Error connecting to camera
            } catch (IOException e) {
            	if(DEBUG){
	                e.printStackTrace();
	                Log.d(TAG, "Request failed-IOException", e);
            	}
                //Error connecting to camera
            }
            return null;
        }

        protected void onPostExecute(MjpegInputStream result) {
            mv.setSource(result);
            if(result!=null){
            	result.setSkip(1);
            	setTitle(R.string.app_name);
            }else{
            	setTitle(R.string.title_disconnected);
            }
            mv.setDisplayMode(MjpegView.SIZE_BEST_FIT);
            if (DEBUG) {
                mv.showFps(true);
            } else {
                mv.showFps(false);
            }
        }

    }
    
    public class RestartApp extends AsyncTask<Void, Void, Void> {
        protected Void doInBackground(Void... v) {
        	MjpegActivity.this.finish();
            return null;
        }

        protected void onPostExecute(Void v) {
        	startActivity((new Intent(MjpegActivity.this, MjpegActivity.class)));
        }
    }

    private class RoverLoop implements Runnable
    {
        public void run()
        {
            while(_runCommunication)
            {
                _sendSteeringValues();

                try
                {
                    Thread.sleep(100);
                } catch (Exception e) {};
            }
            try {
                // ensure we are stopped before the tread dies
                for (int x=0; x<3; x++) {
                    _rover.sendSpeed(0, 0);
                }
            } catch (Exception e) {}

        }


        private void _sendSteeringValues()
        {
            if (_roverIsStopped) {
                _rover.sendSpeed(0, 0);

            } else {
                _rover.sendSpeed(_leftWheel, _rightWheel);
            }
        }

    }

    public void onRoveClicked(View view) {
        // Is the toggle on?
        boolean on = ((ToggleButton) view).isChecked();

        if (on) {
            if(DEBUG) Log.d(TAG, "Rove: on");
            _runRoverLoop();
            _roverIsStopped = false;
        } else {
            if(DEBUG) Log.d(TAG, "Rove: off");
            _roverIsStopped = true;
            _stopRoverLoop();
            _roverLoop = null;

        }
    }

    public void onSpinClicked(View view) {
        // Is the toggle on?
        boolean on = ((ToggleButton) view).isChecked();

        if (on) {
            if(DEBUG) Log.d(TAG, "Spin: on");
        } else {
            if(DEBUG) Log.d(TAG, "Spin: off");
        }
    }
}
