package cash.bchd.android_neutrino;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class TorAdapter extends FragmentStateAdapter {

    public static int totalPage = 5;

    public TorAdapter(FragmentActivity activity) {
        super(activity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return TorOne.newInstance();
            case 1:
                return TorTwo.newInstance();
            case 2:
                return TorThree.newInstance();
            case 3:
                return TorFour.newInstance();
            default:
                return TorFive.newInstance();
        }
    }

    @Override
    public int getItemCount() {
        return totalPage;
    }

}
