package com.example.android.amplacenta;

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

        Button hostButton = (Button) findViewById(R.id.host_button);
        Button guestButton = (Button) findViewById(R.id.guest_button);

        hostButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(SelectLayoutActivity.this, MainActivity.class);
                intent.putExtra(LAYOUT_TYPE, true);
                startActivity(intent);
            }
        });

        guestButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(SelectLayoutActivity.this, MainActivity.class);
                intent.putExtra(LAYOUT_TYPE, false);
                startActivity(intent);
            }
        });

    }

}
