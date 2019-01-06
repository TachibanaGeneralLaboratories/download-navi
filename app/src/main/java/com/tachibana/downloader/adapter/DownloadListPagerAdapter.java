package com.tachibana.downloader.adapter;

import android.content.Context;

import com.tachibana.downloader.R;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

public class DownloadListPagerAdapter extends FragmentPagerAdapter
{
    public static final int NUM_FRAGMENTS = 2;
    public static final int QUEUED_FRAG_POS = 0;
    public static final int COMPLETED_FRAG_POS = 1;

    private Context context;

    public DownloadListPagerAdapter(Context context, FragmentManager fm)
    {
        super(fm);

        this.context = context;
    }

    @Override
    public Fragment getItem(int position)
    {
        switch (position) {
            case QUEUED_FRAG_POS:
                return new Fragment();
            case COMPLETED_FRAG_POS:
                return new Fragment();
            default:
                return null;
        }
    }

    @Override
    public CharSequence getPageTitle(int position)
    {
        if (position < 0 || position >= getCount())
            return null;

        switch (position) {
            case QUEUED_FRAG_POS:
                return context.getString(R.string.fragment_title_queued);
            case COMPLETED_FRAG_POS:
                return context.getString(R.string.fragment_title_completed);
            default:
                return null;
        }
    }

    @Override
    public int getCount()
    {
        return NUM_FRAGMENTS;
    }
}
