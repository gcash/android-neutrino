package cash.bchd.android_neutrino;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.widget.ViewPager2;

public class TorActivity extends FragmentActivity {

    private ViewPager2 _mViewPager;
    private TorAdapter _adapter;
    private View _btn1, _btn2, _btn3, _btn4, _btn5;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tor);
        SystemBars.apply(this);
        setUpView();
        setTab();
    }

    private void setUpView() {
        _mViewPager = findViewById(R.id.torViewPager);
        _adapter = new TorAdapter(this);
        _mViewPager.setAdapter(_adapter);
        _mViewPager.setCurrentItem(0);
        initButton();
    }

    private void setTab() {
        _mViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                btnAction(position);
            }
        });

    }

    private void btnAction(int action) {
        switch (action) {
            case 0:
                setButton(_btn1, true);
                setButton(_btn2, false);
                setButton(_btn3, false);
                setButton(_btn4, false);
                setButton(_btn5, false);
                break;

            case 1:
                setButton(_btn2, true);
                setButton(_btn1, false);
                setButton(_btn3, false);
                setButton(_btn4, false);
                setButton(_btn5, false);
                break;

            case 2:
                setButton(_btn3, true);
                setButton(_btn1, false);
                setButton(_btn2, false);
                setButton(_btn4, false);
                setButton(_btn5, false);
                break;

            case 3:
                setButton(_btn4, true);
                setButton(_btn1, false);
                setButton(_btn2, false);
                setButton(_btn3, false);
                setButton(_btn5, false);
                break;

            case 4:
                setButton(_btn5, true);
                setButton(_btn1, false);
                setButton(_btn2, false);
                setButton(_btn4, false);
                setButton(_btn3, false);
                break;
        }
    }

    private void initButton() {
        _btn1 = findViewById(R.id.torCircle1);
        _btn2 = findViewById(R.id.torCircle2);
        _btn3 = findViewById(R.id.torCircle3);
        _btn4 = findViewById(R.id.torCircle4);
        _btn5 = findViewById(R.id.torCircle5);
        setButton(_btn1, true);
        setButton(_btn2, false);
        setButton(_btn3, false);
        setButton(_btn4, false);
        setButton(_btn5, false);
    }

    // Fills the page's dot, darker for the page shown. Its background is an oval, which a color
    // set as the background replaced with a square.
    private void setButton(View btn, boolean active) {
        GradientDrawable dot = (GradientDrawable) btn.getBackground().mutate();
        dot.setColor(ContextCompat.getColor(this, active ? R.color.darkGreyCircle : R.color.lightGreyCircle));
    }
}