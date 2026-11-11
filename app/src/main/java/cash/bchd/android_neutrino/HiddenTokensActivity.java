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

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import cash.bchd.android_neutrino.tokens.TokenIcons;
import cash.bchd.android_neutrino.tokens.TokenLabels;
import cash.bchd.android_neutrino.wallet.TokenBalance;
import cash.bchd.android_neutrino.wallet.TokenMeta;
import cash.bchd.android_neutrino.wallet.Wallet;

/**
 * HiddenTokensActivity lists the tokens the user hid, which Settings opens while there are any:
 * each one's icon and name as far as the wallet already knows them, by a peek that reaches no
 * host, and what it holds of it, with Show, which puts the token back on the main screen. The
 * screen closes once none is hidden.
 */
public class HiddenTokensActivity extends AppCompatActivity {

    private Settings settings;
    // The tokens as the wallet last told them, for what it holds of those hidden.
    private List<TokenBalance> tokens;
    private final List<String> hidden = new ArrayList<>();
    private final Rows rows = new Rows();
    // The wallet can still be starting when the screen opens.
    private final Runnable walletReady = () -> runOnUiThread(this::peekLabels);
    private static final int LABELS_PER_CALL = 50;

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

        // The one hidden last first, as it's the one most likely looked for.
        hidden.addAll(settings.getHiddenTokensNewestFirst());
        RecyclerView list = findViewById(R.id.hiddenTokens);
        list.setLayoutManager(new LinearLayoutManager(this));
        // With the application's context, as the main screen's lists have.
        list.addItemDecoration(new DividerItemDecoration(getApplicationContext(), DividerItemDecoration.VERTICAL));
        list.setAdapter(rows);
    }

    @Override
    protected void onStart() {
        super.onStart();
        WalletStarter.restartIfGone(this);
        TokenIcons.configure(settings.getTokenImages(), settings.getLoadedIcons());
        Wallet.addReadyListener(walletReady);
        peekLabels();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Wallet.removeReadyListener(walletReady);
    }

    // peekLabels asks what the wallet already knows of the tokens, which it doesn't follow while
    // they're hidden, so that they can be told apart.
    private void peekLabels() {
        Wallet wallet = Wallet.getInstance();
        if (wallet == null || !wallet.isRunning()) {
            return;
        }
        for (List<String> part : Lists.partition(new ArrayList<>(hidden), LABELS_PER_CALL)) {
            Futures.addCallback(wallet.peekTokenMetadataAsync(part, false), new FutureCallback<List<TokenMeta>>() {
                @Override
                public void onSuccess(List<TokenMeta> tokens) {
                    if (TokenLabels.update(tokens)) {
                        runOnUiThread(rows::notifyDataSetChanged);
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    t.printStackTrace();
                }
            }, MoreExecutors.directExecutor());
        }
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
        String amount = TokenLabels.amount(token.category, new BigInteger(Long.toUnsignedString(token.amount)));
        return nfts == null ? amount : getString(R.string.token_amount_and_nfts, amount, nfts);
    }

    private static final class Row extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView id;
        final TextView badge;
        final TextView amount;
        final TextView show;

        Row(View v) {
            super(v);
            icon = v.findViewById(R.id.hiddenTokenIcon);
            id = v.findViewById(R.id.hiddenTokenId);
            badge = v.findViewById(R.id.hiddenTokenBadge);
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
            // Only what was saved, as on the Tokens page, the badge saying how far to trust it.
            TokenIcons.showIcon(row.icon, category, getResources().getDimensionPixelSize(R.dimen.token_row_icon));
            TokenLabels.showTitle(row.id, category, TokenLabels.title(category), true);
            int badge = TokenLabels.badge(category);
            row.badge.setVisibility(badge == 0 ? View.GONE : View.VISIBLE);
            if (badge != 0) {
                row.badge.setText(badge);
            }
            row.amount.setText(amount(TokenBalance.find(tokens, category)));
            row.show.setOnClickListener(v -> show(category));
        }

        @Override
        public int getItemCount() {
            return hidden.size();
        }
    }
}
