package cash.bchd.android_neutrino;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * SystemBars keeps an activity's content clear of the status bar, the navigation bar and display
 * cutouts. Android 15 and newer draw apps targeting them edge to edge, behind the system bars, and
 * no longer color the status bar, so there the content is padded by the bars' size and a view in
 * the theme's dark primary color is drawn behind the status bar, as the status bar looked before.
 * Older versions lay the app out within the bars themselves and are left as they are.
 */
public final class SystemBars {

    private SystemBars() {
    }

    /**
     * Call after setContentView.
     */
    public static void apply(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return;
        }

        View content = activity.findViewById(android.R.id.content);
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        View statusBarBackground = new View(activity);
        statusBarBackground.setBackgroundColor(ContextCompat.getColor(activity, R.color.colorPrimaryDark));
        decor.addView(statusBarBackground, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0));

        // White icons on the status bar's color, dark ones on the light content
        // behind the navigation bar.
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(activity.getWindow(), decor);
        controller.setAppearanceLightStatusBars(false);
        controller.setAppearanceLightNavigationBars(true);

        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            ViewGroup.LayoutParams lp = statusBarBackground.getLayoutParams();
            if (lp.height != bars.top) {
                lp.height = bars.top;
                statusBarBackground.setLayoutParams(lp);
            }
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
