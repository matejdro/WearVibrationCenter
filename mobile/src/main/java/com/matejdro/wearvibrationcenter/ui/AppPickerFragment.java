package com.matejdro.wearvibrationcenter.ui;

import android.app.Fragment;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.matejdro.wearutils.miscutils.BitmapUtils;
import com.matejdro.wearvibrationcenter.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AppPickerFragment extends Fragment implements TitleUtils.TitledFragment {

    private RecyclerView recycler;
    private AppListAdapter adapter;

    private View loadingView;

    private boolean systemApps;

    private List<AppInfoStorage> apps = Collections.emptyList();
    private LruCache<String, Bitmap> iconCache;

    public static AppPickerFragment newInstance(boolean systemApps) {
        AppPickerFragment fragment = new AppPickerFragment();

        Bundle args = new Bundle();
        args.putBoolean("systemApps", systemApps);
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        systemApps = getArguments().getBoolean("systemApps", false);

        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        iconCache = new LruCache<String, Bitmap>(maxMemory / 16) // 1/16th of device's RAM should be far enough for all icons
        {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }

        };

        adapter = new AppListAdapter();
    }

    @Override
    public void onResume() {
        super.onResume();

        new AppLoadingTask().execute();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup view = (ViewGroup) inflater.inflate(R.layout.fragment_app_list, container, false);

        loadingView = view.findViewById(R.id.loadingBar);

        recycler = view.findViewById(R.id.recycler);
        recycler.setAdapter(adapter);
        recycler.setLayoutManager(new LinearLayoutManager(getActivity()));
        recycler.addItemDecoration(new DividerItemDecoration(getActivity(), DividerItemDecoration.VERTICAL));
        recycler.setItemAnimator(new DefaultItemAnimatorNoChange());

        return view;
    }

    private void showList() {
        loadingView.setVisibility(View.GONE);
        recycler.setVisibility(View.VISIBLE);
    }

    @Override
    public String getTitle() {
        return getString(systemApps ? R.string.system_apps : R.string.user_apps);
    }


    private class AppListAdapter extends RecyclerView.Adapter<ViewHolder> {
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            ViewGroup view = (ViewGroup) LayoutInflater.from(getActivity()).inflate(R.layout.fragment_app_list_item, parent, false);
            final ViewHolder holder = new ViewHolder(view);

            holder.icon = view.findViewById(R.id.appImage);
            holder.name = view.findViewById(R.id.appName);

            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AppInfoStorage pickedApp = apps.get(holder.getAdapterPosition());
                    ((AppPickerCallback) getActivity()).onAppPicked(pickedApp.packageName, pickedApp.label.toString());
                }
            });

            return holder;
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            final AppInfoStorage appInfo = apps.get(position);

            holder.name.setText(appInfo.label);

            Bitmap icon = iconCache.get(appInfo.packageName);
            if (icon == null) {
                new IconLoadingTask().execute(appInfo.packageName, position);
                holder.icon.setImageDrawable(null);
            } else {
                holder.icon.setImageBitmap(icon);
            }

        }

        @Override
        public int getItemCount() {
            return apps.size();
        }
    }

    private class AppLoadingTask extends AsyncTask<Void, Void, List<AppInfoStorage>> {
        @Override
        protected List<AppInfoStorage> doInBackground(Void... params) {
            if (getActivity() == null) {
                return null;
            }

            Context context = getActivity();

            if (context == null) {
                return null;
            }

            final PackageManager pm = context.getPackageManager();
            List<PackageInfo> packages = pm.getInstalledPackages(0);

            List<AppInfoStorage> newApps = new ArrayList<>();

            for (PackageInfo packageInfo : packages) {
                try {
                    ApplicationInfo appInfo = pm.getApplicationInfo(packageInfo.packageName, 0);

                    boolean isSystemApp = ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
                    if (isSystemApp == systemApps) {
                        AppInfoStorage storage = new AppInfoStorage(appInfo.packageName, pm.getApplicationLabel(appInfo));

                        newApps.add(storage);
                    }
                } catch (PackageManager.NameNotFoundException ignored) {
                }

            }

            Collections.sort(newApps);

            return newApps;
        }

        @Override
        protected void onPostExecute(List<AppInfoStorage> result) {
            if (result != null) {
                apps = result;
                adapter.notifyDataSetChanged();
                showList();
            }
        }
    }

    private class IconLoadingTask extends AsyncTask<Object, Void, Integer> {
        @Override
        protected void onPostExecute(Integer position) {
            if (position != null) {
                adapter.notifyItemChanged(position);
            }
        }

        @Override
        protected Integer doInBackground(Object... params) {
            if (getActivity() == null) {
                return null;
            }

            final PackageManager pm = getActivity().getPackageManager();

            try {
                Drawable icon = pm.getApplicationIcon((String) params[0]);

                Bitmap iconBitmap = BitmapUtils.getBitmap(icon);
                if (iconBitmap != null) {
                    iconCache.put((String) params[0], iconBitmap);
                }
            } catch (PackageManager.NameNotFoundException ignored) {
            }

            return (Integer) params[1];
        }
    }

    public static class AppInfoStorage implements Comparable<AppInfoStorage> {
        public AppInfoStorage(String packageName, CharSequence label) {
            this.packageName = packageName;
            this.label = label;
            this.labelLower = label.toString().toLowerCase();
        }

        public final String packageName;
        public final CharSequence label;
        private final CharSequence labelLower;

        @Override
        public int compareTo(@NonNull AppInfoStorage other) {
            return labelLower.toString().compareTo(other.labelLower.toString());
        }
    }

    private static class ViewHolder extends RecyclerView.ViewHolder {
        public ImageView icon;
        public TextView name;

        public ViewHolder(View itemView) {
            super(itemView);
        }
    }

    private static class DefaultItemAnimatorNoChange extends DefaultItemAnimator {
        public DefaultItemAnimatorNoChange() {
            // Item change animation causes glitches while scrolling. Disable.
            setSupportsChangeAnimations(false);
        }
    }


    public interface AppPickerCallback
    {
        void onAppPicked(String appPackage, String appLabel);
    }
}