package cash.bchd.android_neutrino;

import android.content.Context;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

public class BackupAdapter extends FragmentPagerAdapter {
    private Context _context;
    public static int totalPage=3;
    public BackupAdapter(Context context, FragmentManager fm) {
        super(fm);
        _context=context;

    }
    @Override
    public Fragment getItem(int position) {
        Fragment f = new Fragment();
        switch(position){
            case 0:
                f=BackupOne.newInstance(_context);
                break;
            case 1:
                f=BackupTwo.newInstance(_context);
                break;
            case 2:
                f=BackupThree.newInstance(_context);
                break;
        }
        return f;
    }
    @Override
    public int getCount() {
        return totalPage;
    }

}