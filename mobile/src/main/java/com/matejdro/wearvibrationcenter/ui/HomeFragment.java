package com.matejdro.wearvibrationcenter.ui;

import android.app.Fragment;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.matejdro.wearvibrationcenter.R;

public class HomeFragment extends Fragment implements TitleUtils.TitledFragment {
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public String getTitle() {
        return getString(R.string.tips_and_tricks);
    }
}
