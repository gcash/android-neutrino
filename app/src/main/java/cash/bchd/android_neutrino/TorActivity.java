package cash.bchd.android_neutrino;


import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.text.Html;
import android.widget.Button;
import android.widget.TextView;

public class TorActivity extends FragmentActivity {
    private CustomViewPager _mViewPager;
    private TorAdapter _adapter;
    private Button _btn1,_btn2,_btn3,_btn4,_btn5;

    public static Activity fa;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tor);
        setUpView();
        setTab();
        fa = this;
    }
    private void setUpView(){
        _mViewPager = (CustomViewPager) findViewById(R.id.torViewPager);
        _adapter = new TorAdapter(getApplicationContext(),getSupportFragmentManager());
        _mViewPager.setAdapter(_adapter);
        _mViewPager.setCurrentItem(0);
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
            case 0: setButton(_btn1,true); setButton(_btn2,false); setButton(_btn3,false); setButton(_btn4,false); setButton(_btn5,false); break;

            case 1: setButton(_btn2,true); setButton(_btn1,false); setButton(_btn3,false); setButton(_btn4,false); setButton(_btn5,false); break;

            case 2: setButton(_btn3,true); setButton(_btn1,false); setButton(_btn2,false); setButton(_btn4,false); setButton(_btn5,false); break;

            case 3: setButton(_btn4,true); setButton(_btn1,false); setButton(_btn2,false); setButton(_btn3,false); setButton(_btn5,false); break;

            case 4: setButton(_btn5,true); setButton(_btn1,false); setButton(_btn2,false); setButton(_btn4,false); setButton(_btn3,false); break;
        }
    }
    private void initButton(){
        _btn1=(Button)findViewById(R.id.torCircle1);
        _btn2=(Button)findViewById(R.id.torCircle2);
        _btn3=(Button)findViewById(R.id.torCircle3);
        _btn4=(Button)findViewById(R.id.torCircle4);
        _btn5=(Button)findViewById(R.id.torCircle5);
        setButton(_btn1,true);
        setButton(_btn2,false);
        setButton(_btn3,false);
        setButton(_btn4,false);
        setButton(_btn5,false);
    }

    private void setButton(Button btn, boolean active){
        if (active) {
            btn.setBackgroundColor(getResources().getColor(R.color.darkGreyCircle));
        } else {
            btn.setBackgroundColor(getResources().getColor(R.color.lightGreyCircle));
        }
    }
}