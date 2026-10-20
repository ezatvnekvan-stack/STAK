package app.dmmstudio.cinema;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.Display;
import android.view.WindowManager;
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;
public class MainActivity extends BridgeActivity {
    @Override public void onCreate(Bundle savedInstanceState) {
        registerPlugin(DMMVaultPlugin.class);
        registerPlugin(DMMTorrentUploadPlugin.class);
        super.onCreate(savedInstanceState);
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
    }
    @Override public void onResume() {
        super.onResume();
        preferSmoothDisplay();
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) preferSmoothDisplay();
    }
    // A hint, not a forced mode: Android retains user, thermal and battery policies.
    // Keep the current physical resolution and never request unsupported rates.
    @SuppressWarnings("deprecation")
    private void preferSmoothDisplay() {
        Display display = getWindowManager().getDefaultDisplay();
        if (display == null) return;
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        float rate = 0f;
        if (power == null || !power.isPowerSaveMode()) {
            Display.Mode current = display.getMode();
            for (Display.Mode mode : display.getSupportedModes()) {
                if (mode.getPhysicalWidth() == current.getPhysicalWidth()
                        && mode.getPhysicalHeight() == current.getPhysicalHeight()
                        && mode.getRefreshRate() <= 120.5f && mode.getRefreshRate() > rate) {
                    rate = mode.getRefreshRate();
                }
            }
        }
        WindowManager.LayoutParams params = getWindow().getAttributes();
        if (Math.abs(params.preferredRefreshRate - rate) > 0.1f) {
            params.preferredRefreshRate = rate;
            getWindow().setAttributes(params);
        }
    }
}
