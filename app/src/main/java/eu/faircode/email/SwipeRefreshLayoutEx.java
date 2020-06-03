package eu.faircode.email;

/*
    This file is part of FairEmail.

    FairEmail is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    FairEmail is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with FairEmail.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2018-2020 by Marcel Bokhorst (M66B)
*/

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class SwipeRefreshLayoutEx extends SwipeRefreshLayout {
    private boolean muted = false;
    private boolean refreshing = false;

    private static final int DELAY_MUTE = 45 * 1000; // milliseconds
    private static final int DELAY_DISABLE = 1500; // milliseconds

    public SwipeRefreshLayoutEx(@NonNull Context context) {
        super(context);
    }

    public SwipeRefreshLayoutEx(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setRefreshing(boolean refreshing) {
        if (this.refreshing == refreshing)
            return;

        Log.i("Refreshing=" + this.refreshing + "/" + refreshing + " muted=" + muted + " event=set");

        this.refreshing = refreshing;

        removeCallbacks(delayedDisable);
        removeCallbacks(delayedMute);

        if (refreshing) {
            if (!muted) {
                super.setRefreshing(true);
                postDelayed(delayedMute, DELAY_MUTE);
            }
        } else {
            muted = false;
            postDelayed(delayedDisable, DELAY_DISABLE);
        }
    }

    @Override
    public boolean isRefreshing() {
        return this.refreshing;
    }

    public void onRefresh() {
        // User initiated
        this.refreshing = true;
        setRefreshing(false); // disable, unless confirmed by folder update
    }

    public void resetRefreshing() {
        // Restart spinner after screen off, etc
        if (super.isRefreshing()) {
            Log.i("Refreshing=" + refreshing + " muted=" + muted + " event=reset");
            super.setRefreshing(false);
            super.setRefreshing(true);
        }
    }

    private final Runnable delayedDisable = new Runnable() {
        @Override
        public void run() {
            Log.i("Refreshing=" + refreshing + " muted=" + muted + " event=disable");
            if (!refreshing)
                SwipeRefreshLayoutEx.super.setRefreshing(refreshing);
        }
    };

    private final Runnable delayedMute = new Runnable() {
        @Override
        public void run() {
            Log.i("Refreshing=" + refreshing + " muted=" + muted + " event=mute");
            if (refreshing) {
                muted = true;
                SwipeRefreshLayoutEx.super.setRefreshing(false);
            }
        }
    };
}
