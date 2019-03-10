package cash.bchd.android_neutrino;

import android.content.Context;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

public class TorAdapter extends FragmentPagerAdapter {
    private Context _context;
    public static int totalPage=5;
    public TorAdapter(Context context, FragmentManager fm) {
        super(fm);
        _context=context;

    }
    @Override
    public Fragment getItem(int position) {
        Fragment f = new Fragment();
        switch(position){
            case 0:
                f=TorOne.newInstance(_context);
                break;
            case 1:
                f=TorTwo.newInstance(_context);
                break;
            case 2:
                f=TorThree.newInstance(_context);
                break;
            case 3:
                f=TorFour.newInstance(_context);
                break;
            case 4:
                f=TorFive.newInstance(_context);
                break;
        }
        return f;
    }
    @Override
    public int getCount() {
        return totalPage;
    }

}
