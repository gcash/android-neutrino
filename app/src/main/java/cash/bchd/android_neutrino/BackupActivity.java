package cash.bchd.android_neutrino;

import android.os.Bundle;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.WindowManager;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager.widget.ViewPager;

public class BackupActivity extends FragmentActivity {
    private CustomViewPager _mViewPager;
    private BackupAdapter _adapter;
    private View _btn1,_btn2,_btn3;

    /**
     * Unlock remembers, while the app runs, that the screen lock is being asked for or was
     * confirmed, so that a screen Android recreates, as after a configuration change, neither
     * asks again nor starts over. A screen Android restores after it ended the app asks again.
     */
    public static class Unlock extends ViewModel {
        boolean asking;
        boolean confirmed;
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_backup);
        SystemBars.apply(this);
        // Keep the recovery phrase and PIN out of screenshots and the recent apps.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        // Show the recovery phrase only to someone who can unlock the phone.
        Unlock unlock = new ViewModelProvider(this).get(Unlock.class);
        ScreenLock.Prompt screenLock = ScreenLock.prompt(this, new ScreenLock.Callback() {
            @Override
            public void onConfirmed() {
                unlock.asking = false;
                unlock.confirmed = true;
                setUpView();
                setTab();
            }

            @Override
            public void onCancelled() {
                unlock.asking = false;
                finish();
            }
        });
        if (unlock.confirmed) {
            setUpView();
            setTab();
        } else if (!unlock.asking) {
            unlock.asking = true;
            screenLock.confirm(getString(R.string.screen_lock_show_recovery_phrase));
        }
    }
    // setUpView shows the pages, from the first, or from the one a recreated screen was on,
    // which the pager restores.
    private void setUpView(){
        _mViewPager = findViewById(R.id.viewPager);
        _adapter = new BackupAdapter(getApplicationContext(),getSupportFragmentManager());
        _mViewPager.setAdapter(_adapter);
        _mViewPager.setAllowedSwipeDirection(CustomViewPager.SwipeDirection.right);
        initButton();
    }
    private void setTab(){
        _mViewPager.setOnPageChangeListener(new ViewPager.OnPageChangeListener(){

            @Override
            public void onPageScrollStateChanged(int position) {}
            @Override
            public void onPageScrolled(int arg0, float arg1, int arg2) {}
            @Override
            public void onPageSelected(int position) {
                // TODO Auto-generated method stub
                btnAction(position);
            }

        });
        btnAction(_mViewPager.getCurrentItem());
    }
    private void btnAction(int action){
        switch(action){
            case 0: setButton(_btn1,true); setButton(_btn2,false); setButton(_btn3,false); break;

            case 1: setButton(_btn2,true); setButton(_btn1,false); setButton(_btn3,false); break;

            case 2: setButton(_btn3,true); setButton(_btn1,false); setButton(_btn2,false); break;
        }
    }
    private void initButton(){
        _btn1= findViewById(R.id.backupCircle1);
        _btn2= findViewById(R.id.backupCircle2);
        _btn3= findViewById(R.id.backupCircle3);
        setButton(_btn1,true);
        setButton(_btn2,false);
        setButton(_btn3,false);
    }
    // Fills the page's dot, darker for the page shown. Its background is an oval, which a color
    // set as the background replaced with a square.
    private void setButton(View btn, boolean active) {
        GradientDrawable dot = (GradientDrawable) btn.getBackground().mutate();
        dot.setColor(ContextCompat.getColor(this, active ? R.color.darkGreyCircle : R.color.lightGreyCircle));
    }
}