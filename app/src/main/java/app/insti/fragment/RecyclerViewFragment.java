package app.insti.fragment;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import app.insti.ActivityBuffer;
import app.insti.R;
import app.insti.activity.MainActivity;
import app.insti.api.RetrofitInterface;
import app.insti.interfaces.Browsable;
import app.insti.interfaces.ItemClickListener;
import app.insti.interfaces.Readable;
import app.insti.interfaces.Writable;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static android.view.View.GONE;

public abstract class RecyclerViewFragment<T extends Browsable, S extends RecyclerView.Adapter<RecyclerView.ViewHolder> & Readable<T> & Writable<T>> extends BaseFragment {
    public static boolean showLoader = true;
    protected RecyclerView recyclerView;
    protected Class<T> postType;
    protected Class<S> adapterType;
    protected SwipeRefreshLayout swipeRefreshLayout;
    protected String searchQuery;
    private S adapter = null;
    boolean loading = false;
    private boolean allLoaded = false;

    /** Update the data clearing existing */
    protected void updateData() {
        // Skip if we're already destroyed
        if (getActivity() == null || getView() == null) return;

        // Clear variables
        allLoaded = false;

        // Make the request
        String sessionIDHeader = ((MainActivity) getActivity()).getSessionIDHeader();
        RetrofitInterface retrofitInterface = ((MainActivity) getActivity()).getRetrofitInterface();
        Call<List<T>> call = getCall(retrofitInterface, sessionIDHeader, 0);
        call.enqueue(new Callback<List<T>>() {
            @Override
            public void onResponse(Call<List<T>> call, Response<List<T>> response) {
                if (response.isSuccessful()) {
                    List<T> posts = response.body();
                    displayData(posts);
                }
                swipeRefreshLayout.setRefreshing(false);
            }

            @Override
            public void onFailure(Call<List<T>> call, Throwable t) {
                swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    abstract Call<List<T>> getCall(RetrofitInterface retrofitInterface, String sessionIDHeader, int postCount);

    private void displayData(final List<T> result) {
        /* Skip if we're already destroyed */
        if (getActivity() == null || getView() == null) return;

        if (adapter == null) {
            initAdapter(result);
        } else {
            adapter.setPosts(result);
            adapter.notifyDataSetChanged();
        }

        getActivity().findViewById(R.id.loadingPanel).setVisibility(GONE);
    }

    /** Initialize the adapter */
    private void initAdapter(final List<T> result) {
        try {
            adapter = adapterType.getDeclaredConstructor(List.class, ItemClickListener.class).newInstance(result, new ItemClickListener() {
                @Override
                public void onItemClick(View v, int position) {
                    String link = result.get(position).getLink();
                    if (link != null && !link.isEmpty())
                        openWebURL(link);
                }
            });
            initRecyclerView();

        } catch (java.lang.InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    /** Initialize scrolling on the adapter */
    private void initRecyclerView() {
        getActivityBuffer().safely(new ActivityBuffer.IRunnable() {
            @Override
            public void run(Activity pActivity) {
                recyclerView.setAdapter(adapter);
                recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
                recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                    @Override
                    public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                        if (dy > 0) {
                            LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                            if (((layoutManager.getChildCount() + layoutManager.findFirstVisibleItemPosition()) > (layoutManager.getItemCount() - 5)) && (!loading) && (!allLoaded)) {
                                loading = true;
                                String sessionIDHeader = ((MainActivity) getActivity()).getSessionIDHeader();
                                RetrofitInterface retrofitInterface = ((MainActivity) getActivity()).getRetrofitInterface();
                                Call<List<T>> call = getCall(retrofitInterface, sessionIDHeader, getPostCount());
                                call.enqueue(new Callback<List<T>>() {
                                    @Override
                                    public void onResponse(Call<List<T>> call, Response<List<T>> response) {
                                        if (getActivity() == null || getView() == null) return;
                                        loading = false;
                                        if (response.isSuccessful()) {
                                            List<T> posts = adapter.getPosts();
                                            posts.addAll(response.body());
                                            if (response.body().size() == 0) {
                                                showLoader = false;
                                                allLoaded = true;
                                            }
                                            adapter.setPosts(posts);
                                            adapter.notifyDataSetChanged();
                                        }
                                    }

                                    @Override
                                    public void onFailure(Call<List<T>> call, Throwable t) {
                                        loading = false;
                                    }
                                });
                            }
                        }
                    }
                });
            }
        });
    }

    protected int getPostCount() {
        if (adapter == null) { return 0; }
        return adapter.getPosts().size();
    }

    private void openWebURL(String URL) {
        Intent browse = new Intent(Intent.ACTION_VIEW, Uri.parse(URL));
        startActivity(browse);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.search_view_menu, menu);
        MenuItem item = menu.findItem(R.id.action_search);
        SearchView sv = new SearchView(((MainActivity) getActivity()).getSupportActionBar().getThemedContext());
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW | MenuItem.SHOW_AS_ACTION_IF_ROOM);
        item.setActionView(sv);
        sv.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                performSearch(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (TextUtils.isEmpty(newText)) {
                    searchQuery = null;
                    updateData();
                    showLoader = true;
                    return true;
                } else if (newText.length() >= 3) {
                    performSearch(newText);
                    return true;
                }
                return false;
            }
        });
    }

    private void performSearch(String query) {
        searchQuery = query;
        updateData();
        showLoader = false;
    }
}
