package cash.bchd.android_neutrino;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cash.bchd.android_neutrino.tokens.NftArt;
import cash.bchd.android_neutrino.tokens.TokenIcons;
import cash.bchd.android_neutrino.tokens.TokenLabels;
import cash.bchd.android_neutrino.wallet.Amount;
import cash.bchd.android_neutrino.wallet.Network;
import cash.bchd.android_neutrino.wallet.TokenBalance;
import cash.bchd.android_neutrino.wallet.TokenChange;
import cash.bchd.android_neutrino.wallet.TokenMeta;
import cash.bchd.android_neutrino.wallet.TransactionData;
import cash.bchd.android_neutrino.wallet.Wallet;

/**
 * TransactionDetailsPopup shows a transaction's details in a popup over a screen's layout: its
 * amount, how it changed the wallet's tokens, its date and status, whom it paid, its memo, its
 * ID and its page on an explorer.
 */
final class TransactionDetailsPopup {

    // The most tokens and NFTs that get a row.
    private static final int MAX_NFT_ROWS = 50;

    private TransactionDetailsPopup() {
    }

    /**
     * show shows the transaction's details, with its confirmations at the block height, and
     * returns the popup.
     */
    static PopupWindow show(Context ctx, ViewGroup layout, TransactionData tx, int blockHeight, Network network) {
        View customView = LayoutInflater.from(ctx).inflate(R.layout.txdetailspopup, layout, false);

        TextView bchAmount = customView.findViewById(R.id.txDetailsBchAmount);
        String detailsAmount = ctx.getString(R.string.bch_amount, new Amount(tx.shownAmount()).toString());
        bchAmount.setText(detailsAmount);

        TextView fiatAmount = customView.findViewById(R.id.txDetailsFiatAmount);
        fiatAmount.setText(tx.getFiatAmount());
        if (!tx.showsBch()) {
            bchAmount.setVisibility(View.GONE);
            fiatAmount.setVisibility(View.GONE);
            customView.findViewById(R.id.txDetailsAmountSpace).setVisibility(View.GONE);
        }

        // A row for the fungible amount of each token the transaction moved, with the token's icon,
        // and one for each of its NFTs, with its art, each saying what came or went on the right.
        LinearLayout tokens = customView.findViewById(R.id.txDetailsTokens);
        int iconSize = ctx.getResources().getDimensionPixelSize(R.dimen.tx_details_token_icon);
        List<Runnable> draws = new ArrayList<>();
        Map<String, List<ImageView>> artViews = new HashMap<>();
        NftArt art = new NftArt(ctx, (category, commitment) -> {
            for (ImageView view : artViews.getOrDefault(category + "-" + commitment, Collections.emptyList())) {
                TokenIcons.showListNft(view, category, commitment, iconSize);
            }
        });
        // A stranger's transaction can hold any number, and each row draws an image.
        int rows = 0;
        // The tokens whose NFTs have rows, which the names are asked for.
        Set<String> nftTokens = new HashSet<>();
        for (TokenChange change : tx.getTokens()) {
            String amount = TokenTexts.details(ctx, change);
            // The NFTs' rows say which token they're of, so the token has a row of its own only
            // for what they don't say.
            boolean nftRows = !change.nftsReceived().isEmpty() || !change.nftsSent().isEmpty();
            boolean tokenRow = !amount.isEmpty() || !nftRows;
            if (rows >= MAX_NFT_ROWS) {
                rows += (tokenRow ? 1 : 0) + change.nftsReceived().size() + change.nftsSent().size();
                continue;
            }
            if (tokenRow) {
                rows++;
                addToken(ctx, tokens, change, amount, iconSize);
            }
            for (String commitment : change.nftsReceived()) {
                if (rows++ < MAX_NFT_ROWS) {
                    addNft(ctx, tokens, change.category, commitment, true, art, artViews, draws);
                    nftTokens.add(change.category);
                }
            }
            for (String commitment : change.nftsSent()) {
                if (rows++ < MAX_NFT_ROWS) {
                    addNft(ctx, tokens, change.category, commitment, false, art, artViews, draws);
                    nftTokens.add(change.category);
                }
            }
        }
        if (rows > MAX_NFT_ROWS) {
            addMore(ctx, tokens, rows - MAX_NFT_ROWS);
        }
        tokens.setVisibility(tx.getTokens().isEmpty() ? View.GONE : View.VISIBLE);

        TextView dateView = customView.findViewById(R.id.txDetailsDate);
        // In the phone's language and its 12 or 24 hour clock.
        dateView.setText(DateUtils.formatDateTime(ctx, tx.getTimestamp() * 1000, DateUtils.FORMAT_SHOW_DATE
                | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_ABBREV_MONTH));

        TextView status = customView.findViewById(R.id.txDetailsStatus);
        ImageView circle = customView.findViewById(R.id.confirmationCircle);

        int confirmations = TransactionRows.confirmations(tx, blockHeight);
        if (confirmations <= 0) {
            status.setText(R.string.unconfirmed);
            circle.setImageResource(R.drawable.red_circle);
        } else if (confirmations < 10) {
            status.setText(R.string.pending);
            circle.setImageResource(R.drawable.yellow_circle);
        } else {
            status.setText(R.string.confirmed);
            circle.setImageResource(R.drawable.green_circle);
        }

        String to = tx.getIncoming() ? null : recipients(ctx, tx);
        if (to == null) {
            customView.findViewById(R.id.txDetailsToDivider).setVisibility(View.GONE);
            customView.findViewById(R.id.txDetailsToRow).setVisibility(View.GONE);
        } else {
            TextView toView = customView.findViewById(R.id.txDetailsTo);
            toView.setText(to);
        }

        TextView memo = customView.findViewById(R.id.txDetailsMemo);
        memo.setText(tx.getMemo());
        if (tx.getMemo() == null || tx.getMemo().isEmpty()) {
            customView.findViewById(R.id.txDetailsMemoRow).setVisibility(View.GONE);
            customView.findViewById(R.id.divider5).setVisibility(View.GONE);
        }

        TextView txid = customView.findViewById(R.id.txDetailsTxid);
        txid.setText(tx.getTxid());

        TextView link = customView.findViewById(R.id.txDetailsLink);
        link.setText(network.explorerUrl(tx.getTxid()));
        PopupWindow popupWindow = open(ctx, layout, customView);
        popupWindow.setOnDismissListener(art::stop);
        showNftNames(ctx, layout, popupWindow, customView, nftTokens, draws);
        return popupWindow;
    }

    // addToken adds the row of a token's fungible amount: its icon, what to call it, with its
    // symbol under, and the amount.
    private static void addToken(Context ctx, LinearLayout tokens, TokenChange change, String amount, int iconSize) {
        View row = LayoutInflater.from(ctx).inflate(R.layout.txdetails_token_row, tokens, false);
        TokenIcons.showListIcon(row.findViewById(R.id.txDetailsTokenIcon), change.category, iconSize);
        TokenLabels.showTitle(row.findViewById(R.id.txDetailsTokenTitle), change.category,
                TokenLabels.listTitle(change.category), false);
        showSubtitle(row.findViewById(R.id.txDetailsTokenSubtitle), TokenLabels.listSymbol(change.category));
        showChange(ctx, row.findViewById(R.id.txDetailsTokenChange), amount,
                change.amount != 0 ? change.amount > 0 : change.nfts > 0);
        addRow(ctx, tokens, row);
    }

    private static void addMore(Context ctx, LinearLayout tokens, int more) {
        View row = LayoutInflater.from(ctx).inflate(R.layout.txdetails_token_row, tokens, false);
        row.findViewById(R.id.txDetailsTokenIcon).setVisibility(View.GONE);
        TextView text = row.findViewById(R.id.txDetailsTokenTitle);
        text.setText(ctx.getResources().getQuantityString(R.plurals.tx_details_more_nfts, more, more));
        addRow(ctx, tokens, row);
    }

    // addNft adds the row of an NFT, and what draws it, as its name and art can come later: its art,
    // its name, with its token's under, and whether it came or went.
    private static void addNft(Context ctx, LinearLayout tokens, String category, String commitment, boolean in,
                               NftArt art, Map<String, List<ImageView>> artViews, List<Runnable> draws) {
        View row = LayoutInflater.from(ctx).inflate(R.layout.txdetails_token_row, tokens, false);
        ImageView icon = row.findViewById(R.id.txDetailsTokenIcon);
        TextView name = row.findViewById(R.id.txDetailsTokenTitle);
        TextView token = row.findViewById(R.id.txDetailsTokenSubtitle);
        int size = ctx.getResources().getDimensionPixelSize(R.dimen.tx_details_token_icon);
        artViews.computeIfAbsent(category + "-" + commitment, key -> new ArrayList<>()).add(icon);
        showChange(ctx, row.findViewById(R.id.txDetailsTokenChange), TokenTexts.nftChange(ctx, in), in);
        token.setVisibility(View.VISIBLE);
        Runnable draw = () -> {
            TokenIcons.showListNft(icon, category, commitment, size);
            name.setText(TokenTexts.nftTitle(ctx, category, commitment));
            TokenLabels.showTitle(token, category, TokenLabels.listTitle(category), false);
            if (!Settings.get(ctx).getHiddenTokens().contains(category)) {
                art.request(category, commitment);
            }
        };
        draw.run();
        draws.add(draw);
        addRow(ctx, tokens, row);
    }

    private static void showSubtitle(TextView view, String text) {
        view.setText(text);
        view.setVisibility(text == null ? View.GONE : View.VISIBLE);
    }

    // showChange shows what came in the list's green, and what went in its purple.
    private static void showChange(Context ctx, TextView view, String text, boolean in) {
        view.setText(text);
        view.setTextColor(ContextCompat.getColor(ctx, in ? R.color.darkGreen : R.color.neonPurple));
    }

    // addRow adds a row of the tokens, under a thin line if it isn't the first.
    private static void addRow(Context ctx, LinearLayout tokens, View row) {
        if (tokens.getChildCount() > 0) {
            LayoutInflater.from(ctx).inflate(R.layout.txdetails_token_divider, tokens, true);
        }
        tokens.addView(row);
    }

    // showNftNames asks the wallet what the NFTs that have rows are called and where their art is,
    // if it hasn't said, as it only says for a few tokens at a time, and has the details show it.
    // Each token is asked for alone, as a large collection's types can take most of what a reply
    // can hold.
    private static void showNftNames(Context ctx, ViewGroup layout, PopupWindow popupWindow, View details,
                                     Set<String> nftTokens, List<Runnable> draws) {
        Wallet wallet = Wallet.getInstance();
        if (wallet == null || !wallet.isRunning()) {
            return;
        }
        for (String category : nftTokens) {
            if (!TokenLabels.hasNftTypes(category)) {
                showNftNames(ctx, layout, popupWindow, details, wallet, category, draws);
            }
        }
    }

    private static void showNftNames(Context ctx, ViewGroup layout, PopupWindow popupWindow, View details,
                                     Wallet wallet, String category, List<Runnable> draws) {
        Futures.addCallback(wallet.peekTokenMetadataAsync(Collections.singletonList(category), true),
                new FutureCallback<List<TokenMeta>>() {
                    @Override
                    public void onSuccess(List<TokenMeta> tokens) {
                        TokenLabels.update(tokens, true);
                        details.post(() -> {
                            if (!popupWindow.isShowing()) {
                                return;
                            }
                            for (Runnable draw : draws) {
                                draw.run();
                            }
                            popupWindow.update(popupWindow.getWidth(), fit(ctx, layout, details, popupWindow.getWidth()));
                        });
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        t.printStackTrace();
                    }
                }, MoreExecutors.directExecutor());
    }

    // open shows the details in the middle of the layout, as wide as a phone allows, and as tall
    // as they are up to what fits, where they scroll.
    private static PopupWindow open(Context ctx, ViewGroup layout, View details) {
        int margin = ctx.getResources().getDimensionPixelSize(R.dimen.tx_details_margin);
        int width = Math.min(ctx.getResources().getDimensionPixelSize(R.dimen.tx_details_width),
                layout.getWidth() - 2 * margin);
        PopupWindow popupWindow = new PopupWindow(details, width, fit(ctx, layout, details, width), true);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.showAtLocation(layout, Gravity.CENTER, 0, 0);
        return popupWindow;
    }

    // fit returns the height of the details at the width, up to what fits in the layout.
    private static int fit(Context ctx, ViewGroup layout, View details, int width) {
        int margin = ctx.getResources().getDimensionPixelSize(R.dimen.tx_details_margin);
        details.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return Math.min(details.getMeasuredHeight(), layout.getHeight() - 2 * margin);
    }

    // recipients says in full whom a sent transaction paid, or is null if no output that pays
    // someone else pays an address.
    private static String recipients(Context ctx, TransactionData tx) {
        switch (tx.getSentTo()) {
            case YOURSELF:
                return ctx.getString(R.string.tx_details_to_yourself);
            case NO_ADDRESS:
                return null;
            case ADDRESS_AND_MORE:
                return ctx.getResources().getQuantityString(R.plurals.tx_details_to_and_more,
                        tx.getMoreRecipients(), tx.getToAddress(), tx.getMoreRecipients());
            default:
                return tx.getToAddress();
        }
    }
}
