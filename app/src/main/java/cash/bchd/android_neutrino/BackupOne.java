package cash.bchd.android_neutrino;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;

public class BackupOne extends Fragment {

    public static Fragment newInstance() {
        return new BackupOne();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.backup_one, null);
    }
}
