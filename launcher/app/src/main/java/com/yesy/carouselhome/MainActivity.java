package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

public final class MainActivity extends Activity {
    private CarouselView carouselView;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUi();

        // The window background is what shows before the first frame, and it fills the bars on a
        // screen that is not 16:9. The theme puts white there; in dark mode that flashed white at
        // every cold start, so it follows the saved theme instead.
        boolean dark = getSharedPreferences("carousel-home", MODE_PRIVATE).getBoolean("dark", false);
        getWindow().setBackgroundDrawable(new ColorDrawable(dark ? Color.BLACK : 0xffffffff));

        carouselView = new CarouselView(this);
        carouselView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setContentView(carouselView);
        carouselView.requestFocus();
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override protected void onResume() {
        super.onResume();
        hideSystemUi();
        if (carouselView != null) {
            // Apps can be installed from a store while we are behind another screen.
            carouselView.refreshAppsIfChanged();
            carouselView.requestFocus();
        }
    }
}
