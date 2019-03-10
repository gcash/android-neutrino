package cash.bchd.android_neutrino;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class TorOne extends Fragment {

    public static Fragment newInstance(Context context) {
        TorOne f = new TorOne();

        return f;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup root = (ViewGroup) inflater.inflate(R.layout.tor_one, null);
        return root;
    }

}
