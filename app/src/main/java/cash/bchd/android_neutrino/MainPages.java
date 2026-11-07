package cash.bchd.android_neutrino;

import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.os.BundleCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

/**
 * MainPages makes the main screen's two pages, Activity and Tokens, which its tabs switch
 * between: each a list, which pull to refresh reads again. The pager doesn't keep its pages'
 * state for a recreated screen, so MainPages keeps where each list was scrolled to.
 */
final class MainPages extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    static final int ACTIVITY = 0;
    static final int TOKENS = 1;
    private static final int PAGES = 2;
    private static final String STATE_ROWS = "pageRows";

    private final RecyclerView.Adapter<?>[] lists;
    private final SwipeRefreshLayout.OnRefreshListener onRefresh;
    // Each page's list and pull to refresh, once the page is made.
    private final LinearLayoutManager[] rows = new LinearLayoutManager[PAGES];
    private final SwipeRefreshLayout[] refreshers = new SwipeRefreshLayout[PAGES];
    // Where the lists of a recreated screen were scrolled to, until its pages are made.
    private final Parcelable[] savedRows = new Parcelable[PAGES];

    MainPages(RecyclerView.Adapter<?> activity, RecyclerView.Adapter<?> tokens,
              SwipeRefreshLayout.OnRefreshListener onRefresh, Bundle savedState) {
        lists = new RecyclerView.Adapter<?>[]{activity, tokens};
        this.onRefresh = onRefresh;
        if (savedState != null) {
            for (int page = 0; page < PAGES; page++) {
                savedRows[page] = BundleCompat.getParcelable(savedState, STATE_ROWS + page, Parcelable.class);
            }
        }
    }

    void saveState(Bundle outState) {
        for (int page = 0; page < PAGES; page++) {
            Parcelable state = rows[page] != null ? rows[page].onSaveInstanceState() : savedRows[page];
            if (state != null) {
                outState.putParcelable(STATE_ROWS + page, state);
            }
        }
    }

    /**
     * rows returns the layout of a page's list, or null until the page is made.
     */
    LinearLayoutManager rows(int page) {
        return rows[page];
    }

    /**
     * stopRefreshing ends pull to refresh on both pages.
     */
    void stopRefreshing() {
        for (SwipeRefreshLayout refresher : refreshers) {
            if (refresher != null) {
                refresher.setRefreshing(false);
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        return position;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int page) {
        SwipeRefreshLayout refresher = (SwipeRefreshLayout) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.main_page, parent, false);
        refresher.setOnRefreshListener(onRefresh);
        RecyclerView list = refresher.findViewById(R.id.pageList);
        list.setHasFixedSize(true);
        // Rows that come and go animate, while one that changes, as when its art arrives, just
        // updates, as a cross-fade flashes while scrolling.
        if (list.getItemAnimator() instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) list.getItemAnimator()).setSupportsChangeAnimations(false);
        }
        // With the application's context, as the screen's list had before it had pages, whose
        // theme draws the dividers lighter than the screen's.
        list.addItemDecoration(new DividerItemDecoration(parent.getContext().getApplicationContext(),
                DividerItemDecoration.VERTICAL));
        LinearLayoutManager layout = new LinearLayoutManager(parent.getContext());
        if (savedRows[page] != null) {
            layout.onRestoreInstanceState(savedRows[page]);
            savedRows[page] = null;
        }
        list.setLayoutManager(layout);
        list.setAdapter(lists[page]);
        rows[page] = layout;
        refreshers[page] = refresher;
        return new RecyclerView.ViewHolder(refresher) {
        };
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
    }

    @Override
    public int getItemCount() {
        return PAGES;
    }
}
