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

import cash.bchd.android_neutrino.tokens.Identicon;
import cash.bchd.android_neutrino.tokens.TokenLabels;
import cash.bchd.android_neutrino.wallet.Amount;
import cash.bchd.android_neutrino.wallet.Network;
import cash.bchd.android_neutrino.wallet.TokenBalance;
import cash.bchd.android_neutrino.wallet.TokenChange;
import cash.bchd.android_neutrino.wallet.TransactionData;

/**
 * TransactionDetailsPopup shows a transaction's details in a popup over a screen's layout: its
 * amount, how it changed the wallet's tokens, its date and status, whom it paid, its memo, its
 * ID and its page on an explorer.
 */
final class TransactionDetailsPopup {

    private TransactionDetailsPopup() {
    }

    /**
     * show shows the transaction's details, with its confirmations at the block height, and
     * returns the popup.
     */
    static PopupWindow show(Context ctx, ViewGroup layout, TransactionData tx, int blockHeight, Network network) {
        View customView = LayoutInflater.from(ctx).inflate(R.layout.txdetailspopup, layout, false);

        ImageView arrow = customView.findViewById(R.id.txDetailsArrow);
        if (tx.getIncoming()) {
            arrow.setImageResource(R.drawable.receive_arrow);
        } else {
            arrow.setImageResource(R.drawable.send_arrow);
        }
        TextView bchAmount = customView.findViewById(R.id.txDetailsBchAmount);
        String detailsAmount = ctx.getString(R.string.bch_amount, new Amount(tx.getAmount()).toString());
        bchAmount.setText(detailsAmount);

        TextView fiatAmount = customView.findViewById(R.id.txDetailsFiatAmount);
        fiatAmount.setText(tx.getFiatAmount());

        // A row for each token the transaction moved: its identicon and short ID, and how much.
        LinearLayout tokens = customView.findViewById(R.id.txDetailsTokens);
        int iconSize = ctx.getResources().getDimensionPixelSize(R.dimen.token_details_icon);
        for (TokenChange change : tx.getTokens()) {
            View row = LayoutInflater.from(ctx).inflate(R.layout.txdetails_token_row, tokens, false);
            ImageView icon = row.findViewById(R.id.txDetailsTokenIcon);
            icon.setImageBitmap(Identicon.of(change.category).bitmap(iconSize));
            TextView id = row.findViewById(R.id.txDetailsTokenId);
            TokenLabels.showTitle(id, change.category, TokenLabels.listTitle(change.category), false);
            TextView amount = row.findViewById(R.id.txDetailsTokenChange);
            amount.setText(TokenTexts.details(ctx, change));
            tokens.addView(row);
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

        TextView txid = customView.findViewById(R.id.txDetailsTxid);
        txid.setText(tx.getTxid());

        TextView link = customView.findViewById(R.id.txDetailsLink);
        link.setText(network.explorerUrl(tx.getTxid()));
        return open(ctx, layout, customView);
    }

    // open shows the details in the middle of the layout, as wide as a phone allows, and as tall
    // as they are up to what fits, where they scroll.
    private static PopupWindow open(Context ctx, ViewGroup layout, View details) {
        int margin = ctx.getResources().getDimensionPixelSize(R.dimen.tx_details_margin);
        int width = Math.min(ctx.getResources().getDimensionPixelSize(R.dimen.tx_details_width),
                layout.getWidth() - 2 * margin);
        details.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int height = Math.min(details.getMeasuredHeight(), layout.getHeight() - 2 * margin);
        PopupWindow popupWindow = new PopupWindow(details, width, height, true);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.showAtLocation(layout, Gravity.CENTER, 0, 0);
        return popupWindow;
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
