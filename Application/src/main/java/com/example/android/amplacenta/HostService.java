package com.example.android.amplacenta;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HostService {
    // Debugging
    private static final String TAG = "HostService";

    // Name for the SDP record when creating server socket
    private static final String NAME_INSECURE = "BluetoothChatInsecure";

    // Unique UUID for this application
    static final UUID HOST_UUID = UUID.fromString("3E8FF50E-5A6E-4F11-86D6-BBB94301BC70");

    // Member fields
    private final BluetoothAdapter bluetoothAdapter;
    private final Handler handler;
    private AcceptThread acceptThread;
    private List<ConnectedThread> connectedThreads;
    private HostState state;

    public enum HostState {
        NONE,
        LISTENING,
    }

    /**
     * Constructor. Prepares a new Party session.
     *
     * @param context The UI Activity Context
     * @param handler A Handler to send messages back to the UI Activity
     */
    public HostService(Context context, Handler handler) {
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.state = HostState.NONE;
        this.handler = handler;
        this.connectedThreads = new ArrayList<>();
    }

    /**
     * Set the current state of the chat connection
     *
     * @param state An integer defining the current connection state
     */
    private synchronized void setState(HostState state) {
        this.state = state;

        // Give the new state to the Handler so the UI Activity can update
        handler.obtainMessage(Constants.MESSAGE_STATE_CHANGE, state.ordinal(), -1).sendToTarget();
    }

    /**
     * Return the current connection state.
     */
    public synchronized HostState getState() {
        return state;
    }

    /**
     * Start hosting the party. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    public synchronized void start() {
        // Cancel any thread currently running a connection
        for (ConnectedThread cThread : connectedThreads) {
            cThread.cancel();
        }
        connectedThreads = new ArrayList<>();

        // Start the thread to listen on a BluetoothServerSocket
        if (acceptThread == null) {
            acceptThread = new AcceptThread();
            acceptThread.start();
        }

        setState(HostState.LISTENING);
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        for (ConnectedThread connectedThread : connectedThreads) {
            connectedThread.cancel();
        }
        connectedThreads = new ArrayList<>();

        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }

        setState(HostState.NONE);
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *
     * @param socket The BluetoothSocket on which the connection was made
     * @param device The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        // Start the thread to manage the connection and perform transmissions
        ConnectedThread connectedThread = new ConnectedThread(socket, device);
        connectedThreads.add(connectedThread);
        connectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = handler.obtainMessage(Constants.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        handler.sendMessage(msg);

        setState(HostState.LISTENING);
    }

    /**
     * Write to the ConnectedThread in an unsynchronized manner
     *
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out) {
        for (ConnectedThread connectedThread : connectedThreads) {
            connectedThread.write(out);
        }
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        // Send a failure message back to the Activity
        Message msg = handler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Device connection was lost");
        msg.setData(bundle);
        handler.sendMessage(msg);

        setState(HostState.LISTENING);
    }

    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private class AcceptThread extends Thread {
        // The local server socket
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp;

            // Create a new listening server socket
            try {
                tmp = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(NAME_INSECURE, HOST_UUID);
            } catch (IOException e) {
                throw new RuntimeException("listen() failed");
            }
            mmServerSocket = tmp;
        }

        public void run() {
            // Listen to the server socket if we're not connected
            while (true) {
                boolean quit = false;
                BluetoothSocket socket;
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized (HostService.this) {
                        switch (state) {
                            case LISTENING:
                                // Situation normal. Start the connected thread.
                                connected(socket, socket.getRemoteDevice());
                                break;
                            case NONE:
                                try {
                                    quit = true;
                                    socket.close();
                                    mmServerSocket.close();
                                } catch (IOException e) {
                                    throw new RuntimeException("socket failed to close");
                                }
                                break;
                        }
                    }
                }
                if (quit) {
                    break;
                }
            }
        }

        public void cancel() {
            try {
                mmServerSocket.close();
            } catch (IOException e) {
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket bluetoothSocket;
        private final BluetoothDevice bluetoothDevice;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public ConnectedThread(BluetoothSocket socket, BluetoothDevice device) {
            bluetoothSocket = socket;
            bluetoothDevice = device;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                // TODO: Something should happen here this shouldn't just fail quietly
            }

            inputStream = tmpIn;
            outputStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            // Keep listening to the InputStream while connected
            while (state == HostState.LISTENING) {
                try {
                    // Read from the InputStream
                    bytes = inputStream.read(buffer);

                    // Send the obtained bytes to the UI Activity
                    handler.obtainMessage(Constants.MESSAGE_READ, bytes, -1, buffer)
                            .sendToTarget();
                } catch (IOException e) {
                    connectionLost();
                    // Start the service over to restart listening mode
                    HostService.this.start();
                    break;
                }
            }
        }

        /**
         * Write to the connected OutStream.
         *
         * @param buffer The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                outputStream.write(buffer);

                // Share the sent message back to the UI Activity
                handler.obtainMessage(Constants.MESSAGE_WRITE, -1, -1, buffer)
                        .sendToTarget();
            } catch (IOException e) {
            }
        }

        public void cancel() {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
            }
        }
    }

    public int numConnections() {
        return connectedThreads.size();
    }

}
