package cash.bchd.android_neutrino;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cash.bchd.android_neutrino.tokens.Identicon;
import cash.bchd.android_neutrino.wallet.TokenBalance;

/**
 * HiddenTokensActivity lists the tokens the user hid, which Settings opens while there are any:
 * each one's identicon, short token ID, and what the wallet holds of it, with Show, which puts
 * the token back on the main screen. The screen closes once none is hidden.
 */
public class HiddenTokensActivity extends AppCompatActivity {

    private Settings settings;
    // The tokens as the wallet last told them, for what it holds of those hidden.
    private List<TokenBalance> tokens;
    private final List<String> hidden = new ArrayList<>();
    private final Rows rows = new Rows();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hidden_tokens);
        SystemBars.apply(this);
        settings = Settings.get(this);
        tokens = settings.getLastTokens();

        Toolbar toolbar = findViewById(R.id.hiddenTokensToolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        // By token ID, as the Tokens page lists them.
        hidden.addAll(settings.getHiddenTokens());
        Collections.sort(hidden);
        RecyclerView list = findViewById(R.id.hiddenTokens);
        list.setLayoutManager(new LinearLayoutManager(this));
        // With the application's context, as the main screen's lists have.
        list.addItemDecoration(new DividerItemDecoration(getApplicationContext(), DividerItemDecoration.VERTICAL));
        list.setAdapter(rows);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // show puts the token back on the main screen, and closes the screen once none is hidden.
    private void show(String category) {
        int position = hidden.indexOf(category);
        if (position < 0) {
            return;
        }
        settings.showToken(category);
        hidden.remove(position);
        rows.notifyItemRemoved(position);
        if (hidden.isEmpty()) {
            finish();
        }
    }

    // amount says what the wallet holds of the token, as the Tokens page does, or nothing for a
    // token it no longer holds.
    private String amount(TokenBalance token) {
        if (token == null) {
            return null;
        }
        String nfts = token.nfts == 0 ? null
                : getResources().getQuantityString(R.plurals.token_nfts, token.nfts, token.nfts);
        if (token.amount == 0) {
            return nfts;
        }
        // The amount is a uint64, as the API has it.
        String amount = NumberFormat.getIntegerInstance().format(new BigInteger(Long.toUnsignedString(token.amount)));
        return nfts == null ? amount : getString(R.string.token_amount_and_nfts, amount, nfts);
    }

    private static final class Row extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView id;
        final TextView amount;
        final TextView show;

        Row(View v) {
            super(v);
            icon = v.findViewById(R.id.hiddenTokenIcon);
            id = v.findViewById(R.id.hiddenTokenId);
            amount = v.findViewById(R.id.hiddenTokenAmount);
            show = v.findViewById(R.id.hiddenTokenShow);
        }
    }

    private final class Rows extends RecyclerView.Adapter<Row> {

        @NonNull
        @Override
        public Row onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Row(LayoutInflater.from(parent.getContext()).inflate(R.layout.hidden_token_row, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Row row, int position) {
            String category = hidden.get(position);
            row.icon.setImageBitmap(Identicon.of(category)
                    .bitmap(getResources().getDimensionPixelSize(R.dimen.token_row_icon)));
            row.id.setText(TokenBalance.shortId(category));
            row.amount.setText(amount(TokenBalance.find(tokens, category)));
            row.show.setOnClickListener(v -> show(category));
        }

        @Override
        public int getItemCount() {
            return hidden.size();
        }
    }
}
