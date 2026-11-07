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
        return show(ctx, layout, tx, blockHeight, network, null);
    }

    /**
     * show shows the details as the row of one of the tokens the transaction moved has it, with
     * that token's direction.
     */
    static PopupWindow show(Context ctx, ViewGroup layout, TransactionData tx, int blockHeight, Network network,
                            TokenChange rowChange) {
        View customView = LayoutInflater.from(ctx).inflate(R.layout.txdetailspopup, layout, false);

        ImageView arrow = customView.findViewById(R.id.txDetailsArrow);
        boolean incoming = rowChange == null ? tx.shownIncoming() : rowChange.received();
        boolean swapped = rowChange == null ? tx.swapped() : rowChange.swapped();
        arrow.setImageResource(incoming ? R.drawable.receive_arrow : R.drawable.send_arrow);
        arrow.setVisibility(swapped ? View.GONE : View.VISIBLE);
        TextView bchAmount = customView.findViewById(R.id.txDetailsBchAmount);
        String detailsAmount = ctx.getString(R.string.bch_amount, new Amount(tx.shownAmount()).toString());
        bchAmount.setText(detailsAmount);

        TextView fiatAmount = customView.findViewById(R.id.txDetailsFiatAmount);
        fiatAmount.setText(tx.getFiatAmount());

        // A row for each token the transaction moved: its icon, what to call it and how much, then
        // a row for each of its NFTs with its art.
        LinearLayout tokens = customView.findViewById(R.id.txDetailsTokens);
        int iconSize = ctx.getResources().getDimensionPixelSize(R.dimen.token_details_icon);
        int nftIconSize = ctx.getResources().getDimensionPixelSize(R.dimen.tx_details_nft_icon);
        List<Runnable> draws = new ArrayList<>();
        Map<String, List<ImageView>> artViews = new HashMap<>();
        NftArt art = new NftArt(ctx, (category, commitment) -> {
            for (ImageView view : artViews.getOrDefault(category + "-" + commitment, Collections.emptyList())) {
                view.setImageBitmap(TokenIcons.listNftBitmap(ctx, category, commitment, nftIconSize));
            }
        });
        // A stranger's transaction can hold any number, and each row draws an image.
        int rows = 0;
        for (TokenChange change : tx.getTokens()) {
            if (rows >= MAX_NFT_ROWS) {
                rows += 1 + change.nftsReceived().size() + change.nftsSent().size();
                continue;
            }
            rows++;
            View row = LayoutInflater.from(ctx).inflate(R.layout.txdetails_token_row, tokens, false);
            ImageView icon = row.findViewById(R.id.txDetailsTokenIcon);
            icon.setImageBitmap(TokenIcons.listBitmap(ctx, change.category, iconSize));
            TextView id = row.findViewById(R.id.txDetailsTokenId);
            TokenLabels.showTitle(id, change.category, TokenLabels.listTitle(change.category), false);
            TextView amount = row.findViewById(R.id.txDetailsTokenChange);
            amount.setText(TokenTexts.details(ctx, change));
            tokens.addView(row);
            for (String commitment : change.nftsReceived()) {
                if (rows++ < MAX_NFT_ROWS) {
                    addNft(ctx, tokens, change.category, commitment, true, art, artViews, draws);
                }
            }
            for (String commitment : change.nftsSent()) {
                if (rows++ < MAX_NFT_ROWS) {
                    addNft(ctx, tokens, change.category, commitment, false, art, artViews, draws);
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

        int confirmations = 0;
        if (tx.getHeight() > 0) {
            confirmations = blockHeight - tx.getHeight() + 1;
        }
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
        showNftNames(ctx, layout, popupWindow, customView, tx, draws);
        return popupWindow;
    }

    private static void addMore(Context ctx, LinearLayout tokens, int more) {
        View row = LayoutInflater.from(ctx).inflate(R.layout.txdetails_nft_row, tokens, false);
        row.findViewById(R.id.txDetailsNftIcon).setVisibility(View.GONE);
        TextView text = row.findViewById(R.id.txDetailsNftName);
        text.setText(ctx.getResources().getQuantityString(R.plurals.tx_details_more_nfts, more, more));
        tokens.addView(row);
    }

    // addNft adds the row of an NFT, and what draws it, as its name and art can come later.
    private static void addNft(Context ctx, LinearLayout tokens, String category, String commitment, boolean in,
                               NftArt art, Map<String, List<ImageView>> artViews, List<Runnable> draws) {
        View row = LayoutInflater.from(ctx).inflate(R.layout.txdetails_nft_row, tokens, false);
        ImageView icon = row.findViewById(R.id.txDetailsNftIcon);
        TextView name = row.findViewById(R.id.txDetailsNftName);
        int size = ctx.getResources().getDimensionPixelSize(R.dimen.tx_details_nft_icon);
        artViews.computeIfAbsent(category + "-" + commitment, key -> new ArrayList<>()).add(icon);
        Runnable draw = () -> {
            icon.setImageBitmap(TokenIcons.listNftBitmap(ctx, category, commitment, size));
            name.setText(in ? TokenTexts.nftIn(ctx, category, commitment) : TokenTexts.nftOut(ctx, category, commitment));
            if (!Settings.get(ctx).getHiddenTokens().contains(category)) {
                art.request(category, commitment);
            }
        };
        draw.run();
        draws.add(draw);
        tokens.addView(row);
    }

    // showNftNames asks the wallet what the NFTs are called and where their art is, if it hasn't
    // said, as it only says for a few tokens at a time, and has the details show it.
    private static void showNftNames(Context ctx, ViewGroup layout, PopupWindow popupWindow, View details,
                                     TransactionData tx, List<Runnable> draws) {
        Set<String> categories = new HashSet<>();
        for (TokenChange change : tx.getTokens()) {
            boolean nfts = !change.nftsReceived().isEmpty() || !change.nftsSent().isEmpty();
            if (nfts && !TokenLabels.hasNftTypes(change.category)) {
                categories.add(change.category);
            }
        }
        Wallet wallet = Wallet.getInstance();
        if (categories.isEmpty() || wallet == null || !wallet.isRunning()) {
            return;
        }
        Futures.addCallback(wallet.peekTokenMetadataAsync(new ArrayList<>(categories), true),
                new FutureCallback<List<TokenMeta>>() {
                    @Override
                    public void onSuccess(List<TokenMeta> tokens) {
                        TokenLabels.update(tokens);
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
