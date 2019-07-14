package cash.bchd.android_neutrino;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

public class TorAdapter extends FragmentPagerAdapter {

    public static int totalPage = 5;

    public TorAdapter(FragmentManager fm) {
        super(fm);
    }

    @Override
    public Fragment getItem(int position) {
        Fragment f = new Fragment();
        switch (position) {
            case 0:
                f = TorOne.newInstance();
                break;
            case 1:
                f = TorTwo.newInstance();
                break;
            case 2:
                f = TorThree.newInstance();
                break;
            case 3:
                f = TorFour.newInstance();
                break;
            case 4:
                f = TorFive.newInstance();
                break;
        }
        return f;
    }

    @Override
    public int getCount() {
        return totalPage;
    }

}
