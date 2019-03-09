package cash.bchd.android_neutrino;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.text.Html;
import android.widget.Button;
import android.widget.TextView;

public class BackupActivity extends FragmentActivity {
    private CustomViewPager _mViewPager;
    private BackupAdapter _adapter;
    private Button _btn1,_btn2,_btn3;

    public static Activity fa;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_backup);
        setUpView();
        setTab();
        fa = this;
    }
    private void setUpView(){
        _mViewPager = (CustomViewPager) findViewById(R.id.viewPager);
        _adapter = new BackupAdapter(getApplicationContext(),getSupportFragmentManager());
        _mViewPager.setAdapter(_adapter);
        _mViewPager.setCurrentItem(0);
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

    }
    private void btnAction(int action){
        switch(action){
            case 0: setButton(_btn1,true); setButton(_btn2,false); setButton(_btn3,false); break;

            case 1: setButton(_btn2,true); setButton(_btn1,false); setButton(_btn3,false); break;

            case 2: setButton(_btn3,true); setButton(_btn1,false); setButton(_btn2,false); break;
        }
    }
    private void initButton(){
        _btn1=(Button)findViewById(R.id.backupCircle1);
        _btn2=(Button)findViewById(R.id.backupCircle2);
        _btn3=(Button)findViewById(R.id.backupCircle3);
        setButton(_btn1,true);
        setButton(_btn2,false);
        setButton(_btn3,false);
    }
    private void setButton(Button btn, boolean active){
        if (active) {
            btn.setBackgroundColor(getResources().getColor(R.color.darkGreyCircle));
        } else {
            btn.setBackgroundColor(getResources().getColor(R.color.lightGreyCircle));
        }
    }
}