package cash.bchd.android_neutrino;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class BackupOne extends Fragment {

    public static Fragment newInstance(Context context) {
        BackupOne f = new BackupOne();

        return f;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.backup_one, null);
        return root;
    }

}
