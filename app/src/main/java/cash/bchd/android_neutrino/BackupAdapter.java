package cash.bchd.android_neutrino;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class BackupAdapter extends FragmentStateAdapter {
    private Context _context;
    public static int totalPage=3;
    public BackupAdapter(FragmentActivity activity) {
        super(activity);
        _context=activity.getApplicationContext();

    }
    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch(position){
            case 0:
                return BackupOne.newInstance();
            case 1:
                return BackupTwo.newInstance();
            default:
                return BackupThree.newInstance(_context);
        }
    }
    @Override
    public int getItemCount() {
        return totalPage;
    }

}
