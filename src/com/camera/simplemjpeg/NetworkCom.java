package com.camera.simplemjpeg;

import android.os.NetworkOnMainThreadException;
import android.util.Log;

/**
 * Created by mskovgaard on 2/2/15.
 */
public class NetworkCom {
    /**
     * Constants for communicating with and controlling the cam rover
     */
    private static final byte SPEED_COMMAND = 0x01;

    private NetworkEngine _communicationEngine;

    private String _ipAddress;
    private int _port;

    public NetworkCom(String ipAddress, int port)
    {
        _ipAddress = ipAddress;
        _port = port;
    }

    public boolean startCommunication()
    {
        //setup an engine instance for sending packets
        _communicationEngine = new NetworkEngine(_ipAddress, _port);
        return _communicationEngine.openSocket();
    }

    public static byte[] toByteA(short data) {
        return new byte[] {
                (byte)((data >> 8) & 0xff),
                (byte)((data >> 0) & 0xff),
        };
    }

    public static byte[] toByteA(short[] data) {
        if (data == null) return null;
        // ----------
        byte[] byts = new byte[data.length * 2];
        for (int i = 0; i < data.length; i++)
            System.arraycopy(toByteA(data[i]), 0, byts, i * 2, 2);
        return byts;
    }


    /**
     * Sends the Speed command to the physical cam rover.
     *
     * @param leftWheel speed +/- 255 max for left wheel
     * @param rightWheel speed +/- 255 max for right wheel
     */
    public void sendSpeed(int leftWheel, int rightWheel)
    {
        //Log.e("SEND SPEED", "Speed: "+speed + " Steer: "+steer);
        int size = 3;
        byte[] msg = new byte[size];
        msg[0] = SPEED_COMMAND;
        msg[1] = (byte)leftWheel;
        msg[2] = (byte)rightWheel;
        try {
            _communicationEngine.write(msg);
        } catch (Exception e) {
            Log.e("Network Com","Caught exception",e);
        }
    }
}
