package com.camera.simplemjpeg;

import android.util.Log;

import java.net.InetAddress;
import java.net.DatagramSocket;
import java.net.DatagramPacket;

/**
 * Created by mskovgaard on 2/2/15.
 */
public class NetworkEngine {
    private String _ipAddress;
    private int _port;

    private InetAddress _serverAddr;
    private DatagramSocket _socket;


    public NetworkEngine(String ipAddress, int port)
    {
        _ipAddress = ipAddress;
        _port = port;

    }


    public boolean openSocket()
    {

        try
        {
            _serverAddr = InetAddress.getByName(_ipAddress);
            _socket = new DatagramSocket();
            _socket.setSoTimeout(1000);
        }
        catch(Exception e)
        {
            Log.e("Engine", "Exception", e);
            return false;
        }

        return true;
    }


    /**
     *
     */
    public boolean write(byte[] data)
    {
        try
        {
            DatagramPacket out = new DatagramPacket(data, data.length, _serverAddr, _port);
            _socket.send(out);
            return true;
        }

        catch(Exception e)
        {
            Log.e("Engine", "write", e);
        }

        return false;
    }

}
