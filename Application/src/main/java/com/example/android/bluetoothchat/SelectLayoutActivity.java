package com.example.android.bluetoothchat;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class SelectLayoutActivity extends Activity {

    public static final String LAYOUT_TYPE = "isMasterLayoutExtra";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.select_layout);

        Button masterButton = (Button) findViewById(R.id.master_button);
        Button slaveButton = (Button) findViewById(R.id.slave_button);

        masterButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(SelectLayoutActivity.this, MainActivity.class);
                intent.putExtra(LAYOUT_TYPE, true);
                startActivity(intent);
            }
        });

        slaveButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(SelectLayoutActivity.this, MainActivity.class);
                intent.putExtra(LAYOUT_TYPE, false);
                startActivity(intent);
            }
        });

    }

}
