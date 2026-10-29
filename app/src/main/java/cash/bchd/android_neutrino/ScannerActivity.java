package cash.bchd.android_neutrino;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;

import java.util.Collections;

/**
 * ScannerActivity scans a QR code with the camera and returns its text as the "qrdata" extra
 * with RESULT_OK. It decodes with ZXing on the device, so it needs no Google Play services.
 */
public class ScannerActivity extends AppCompatActivity {

    private static final int REQ_CAMERA = 1;

    private DecoratedBarcodeView barcodeView;
    private boolean scanned;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scanner);
        SystemBars.apply(this);

        barcodeView = findViewById(R.id.scanPreview);
        barcodeView.getBarcodeView().setDecoderFactory(
                new DefaultDecoderFactory(Collections.singletonList(BarcodeFormat.QR_CODE)));
        barcodeView.setStatusText("");
        barcodeView.decodeContinuous(result -> {
            if (scanned || result.getText() == null) {
                return;
            }
            scanned = true;
            Vibrations.buzz(getApplicationContext());
            Intent data = new Intent();
            data.putExtra("qrdata", result.getText());
            setResult(RESULT_OK, data);
            finish();
        });

        if (!hasCameraPermission()) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasCameraPermission()) {
            barcodeView.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        barcodeView.pause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_CAMERA) {
            return;
        }
        if (hasCameraPermission()) {
            barcodeView.resume();
        } else {
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }
}
