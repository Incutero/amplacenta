package com.example.android.amplacenta;

import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class HostFragment extends Fragment {
    private static final int REQUEST_ENABLE_BT = 1;

    // Layout Views
    private ListView conversationView;
    private EditText outEditText;
    private Button sendButton;

    // State Variables
    private List<String> connectedDeviceNames;
    private ArrayAdapter<String> conversationArray;
    private StringBuffer outBuffer;
    private BluetoothAdapter bluetoothAdapter;
    private HostService hostService;

    /**
     * The action listener for the EditText widget, to listen for the return key
     */
    private TextView.OnEditorActionListener returnKeyWriteListener
            = new TextView.OnEditorActionListener() {
        public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
            // If the action is a key-up event on the return key, send the message
            if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                String message = view.getText().toString();
                sendMessage(message);
            }
            return true;
        }
    };

    /**
     * The Handler that gets information back from the HostService
     */
    private final Handler hostHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            FragmentActivity activity = getActivity();
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (HostService.HostState.values()[msg.arg1]) {
                        case LISTENING:
                            setActionBarSubtitle(getString(R.string.host_title_connected_to, connectedDeviceNames.size()));
                            break;
                        case NONE:
                            setActionBarSubtitle(getString(R.string.title_not_connected));
                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    conversationArray.add("Me:  " + writeMessage);
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    conversationArray.add(connectedDeviceNames.size() + ":  " + readMessage);
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    connectedDeviceNames.add(msg.getData().getString(Constants.DEVICE_NAME));
                    if (activity != null) {
                        Toast.makeText(activity, "Connected to " + connectedDeviceNames, Toast.LENGTH_SHORT).show();
                    }
                    break;
                case Constants.MESSAGE_TOAST:
                    if (activity != null) {
                        Toast.makeText(activity, msg.getData().getString(Constants.TOAST), Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        }
    };

    public HostFragment() {
        this.connectedDeviceNames = new ArrayList<>();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        // If the adapter is null, then Bluetooth is not supported
        if (bluetoothAdapter == null) {
            FragmentActivity activity = getActivity();
            Toast.makeText(activity, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            activity.finish();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the chat session
        } else if (hostService == null) {
            setupParty();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (hostService != null) {
            hostService.stop();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (hostService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (hostService.getState() == HostService.HostState.NONE) {
                // Start the party
                hostService.start();
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bluetooth_chat, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        conversationView = (ListView) view.findViewById(R.id.in);
        outEditText = (EditText) view.findViewById(R.id.edit_text_out);
        sendButton = (Button) view.findViewById(R.id.button_send);
    }

    /**
     * Set up the UI and background operations for the party.
     */
    private void setupParty() {
        // Initialize the array adapter for the conversation thread
        conversationArray = new ArrayAdapter<>(getActivity(), R.layout.message);
        conversationView.setAdapter(conversationArray);

        // Initialize the compose field with a listener for the return key
        outEditText.setOnEditorActionListener(returnKeyWriteListener);
        // Initialize the send button with a listener that for click events
        sendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                View view = getView();
                if (null != view) {
                    TextView textView = (TextView) view.findViewById(R.id.edit_text_out);
                    String message = textView.getText().toString();
                    sendMessage(message);
                }
            }
        });

        // Initialize the GuestService to perform bluetooth connections
        hostService = new HostService(getActivity(), hostHandler);
        // Initialize the buffer for outgoing messages
        outBuffer = new StringBuffer("");
    }

    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (hostService.getState() == HostService.HostState.NONE || hostService.numConnections() <= 0) {
            Toast.makeText(getActivity(), R.string.host_cannot_send_message, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the HostService to write
            byte[] send = message.getBytes();
            hostService.write(send);

            // Reset out string buffer to zero and clear the edit text field
            outBuffer.setLength(0);
            outEditText.setText(outBuffer);
        }
    }

    private void setActionBarSubtitle(CharSequence subTitle) {
        FragmentActivity activity = getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(subTitle);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    setupParty();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Toast.makeText(getActivity(), R.string.bt_not_enabled_leaving,
                            Toast.LENGTH_SHORT).show();
                    getActivity().finish();
                }
        }
    }
}
