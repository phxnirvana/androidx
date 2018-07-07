/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.mediarouter.app;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.appcompat.app.AppCompatDialog;
import androidx.mediarouter.R;
import androidx.mediarouter.media.MediaRouteDescriptor;
import androidx.mediarouter.media.MediaRouteSelector;
import androidx.mediarouter.media.MediaRouter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * This class implements the route device picker dialog for {@link MediaRouter}.
 * <p>
 * This dialog allows the user to choose a route that matches a given selector.
 *
 * @see MediaRouteButton
 * @see MediaRouteActionProvider
 * @hide
 */
@RestrictTo(LIBRARY_GROUP)
public class MediaRouteDevicePickerDialog extends AppCompatDialog {
    private static final String TAG = "MediaRouteDevicePickerDialog";

    private static final int ITEM_TYPE_NONE = 0;
    private static final int ITEM_TYPE_HEADER = 1;
    private static final int ITEM_TYPE_ROUTE = 2;

    // Do not update the route list immediately to avoid unnatural dialog change.
    static final int MSG_UPDATE_ROUTES = 1;

    private final MediaRouter mRouter;
    private final MediaRouteDevicePickerDialog.MediaRouterCallback mCallback;

    Context mContext;
    private MediaRouteSelector mSelector = MediaRouteSelector.EMPTY;
    List<MediaRouter.RouteInfo> mRoutes;
    private ImageButton mCloseButton;
    private RecyclerAdapter mAdapter;
    private RecyclerView mRecyclerView;
    private boolean mAttachedToWindow;
    private long mUpdateRoutesDelayMs;
    private long mLastUpdateTime;
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message message) {
            switch (message.what) {
                case MSG_UPDATE_ROUTES:
                    updateRoutes((List<MediaRouter.RouteInfo>) message.obj);
                    break;
            }
        }
    };

    public MediaRouteDevicePickerDialog(Context context) {
        this(context, 0);
    }

    public MediaRouteDevicePickerDialog(Context context, int theme) {
        super(context = MediaRouterThemeHelper.createThemedDialogContext(context, theme, false),
                MediaRouterThemeHelper.createThemedDialogStyle(context));
        context = getContext();

        mRouter = MediaRouter.getInstance(context);
        mCallback = new MediaRouteDevicePickerDialog.MediaRouterCallback();
        mContext = context;
        mUpdateRoutesDelayMs = context.getResources().getInteger(
                R.integer.mr_update_routes_delay_ms);
    }

    /**
     * Gets the media route selector for filtering the routes that the user can select.
     *
     * @return The selector, never null.
     */
    @NonNull
    public MediaRouteSelector getRouteSelector() {
        return mSelector;
    }

    /**
     * Sets the media route selector for filtering the routes that the user can select.
     *
     * @param selector The selector, must not be null.
     */
    public void setRouteSelector(@NonNull MediaRouteSelector selector) {
        if (selector == null) {
            throw new IllegalArgumentException("selector must not be null");
        }

        if (!mSelector.equals(selector)) {
            mSelector = selector;

            if (mAttachedToWindow) {
                mRouter.removeCallback(mCallback);
                mRouter.addCallback(selector, mCallback,
                        MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);
            }

            refreshRoutes();
        }
    }

    /**
     * Called to filter the set of routes that should be included in the list.
     * <p>
     * The default implementation iterates over all routes in the provided list and
     * removes those for which {@link #onFilterRoute} returns false.
     *
     * @param routes The list of routes to filter in-place, never null.
     */
    public void onFilterRoutes(@NonNull List<MediaRouter.RouteInfo> routes) {
        for (int i = routes.size(); i-- > 0; ) {
            if (!onFilterRoute(routes.get(i))) {
                routes.remove(i);
            }
        }
    }

    /**
     * Called to filter a specific route. Returns {@code true} to include in the picker dialog
     * <p>
     * The default implementation returns true for enabled non-default routes that
     * match the selector. Subclasses can override this method to filter routes
     * differently.
     *
     * @param route The route to consider, never null.
     * @return {@code true} if the route should be included in the device picker dialog.
     */
    public boolean onFilterRoute(@NonNull MediaRouter.RouteInfo route) {
        return !route.isDefaultOrBluetooth() && route.isEnabled()
                && route.matchesSelector(mSelector);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.mr_picker_dialog);

        mRoutes = new ArrayList<>();
        mCloseButton = findViewById(R.id.mr_picker_close_button);
        mCloseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        mAdapter = new RecyclerAdapter();
        mRecyclerView = findViewById(R.id.mr_picker_list);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(mContext));

        updateLayout();
    }

    /**
     * Sets the width of the dialog. Also called when configuration changes.
     */
    void updateLayout() {
        // Set layout width and height to MATCH_PARENT to make full screen dialog
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    @CallSuper
    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        mAttachedToWindow = true;
        mRouter.addCallback(mSelector, mCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);
        refreshRoutes();
    }

    @CallSuper
    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        mAttachedToWindow = false;
        mRouter.removeCallback(mCallback);
        mHandler.removeMessages(MSG_UPDATE_ROUTES);
    }

    /**
     * Refreshes the list of routes that are shown in the device picker dialog.
     */
    public void refreshRoutes() {
        if (mAttachedToWindow) {
            ArrayList<MediaRouter.RouteInfo> routes = new ArrayList<>(mRouter.getRoutes());
            onFilterRoutes(routes);
            Collections.sort(routes, MediaRouteDevicePickerDialog.RouteComparator.sInstance);
            if (SystemClock.uptimeMillis() - mLastUpdateTime >= mUpdateRoutesDelayMs) {
                updateRoutes(routes);
            } else {
                mHandler.removeMessages(MSG_UPDATE_ROUTES);
                mHandler.sendMessageAtTime(mHandler.obtainMessage(MSG_UPDATE_ROUTES, routes),
                        mLastUpdateTime + mUpdateRoutesDelayMs);
            }
        }
    }

    void updateRoutes(List<MediaRouter.RouteInfo> routes) {
        mLastUpdateTime = SystemClock.uptimeMillis();
        mRoutes.clear();
        mRoutes.addAll(routes);
        mAdapter.setItems();
    }

    private final class MediaRouterCallback extends MediaRouter.Callback {
        MediaRouterCallback() {
        }

        @Override
        public void onRouteAdded(MediaRouter router, MediaRouter.RouteInfo info) {
            refreshRoutes();
        }

        @Override
        public void onRouteRemoved(MediaRouter router, MediaRouter.RouteInfo info) {
            refreshRoutes();
        }

        @Override
        public void onRouteChanged(MediaRouter router, MediaRouter.RouteInfo info) {
            refreshRoutes();
        }

        @Override
        public void onRouteSelected(MediaRouter router, MediaRouter.RouteInfo route) {
            dismiss();
        }
    }

    static final class RouteComparator implements Comparator<MediaRouter.RouteInfo> {
        public static final RouteComparator sInstance = new RouteComparator();

        @Override
        public int compare(MediaRouter.RouteInfo lhs, MediaRouter.RouteInfo rhs) {
            return lhs.getName().compareToIgnoreCase(rhs.getName());
        }
    }

    /**
     * This class stores a list of Item values that can contain information about both section
     * header(text of section header) and route(text of route name, icon of route type)
     */
    private final class RecyclerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final String TAG = "RecyclerAdapter";
        ArrayList<Item> mItems;

        private final LayoutInflater mInflater;
        private final Drawable mDefaultIcon;
        private final Drawable mTvIcon;
        private final Drawable mSpeakerIcon;
        private final Drawable mSpeakerGroupIcon;

        RecyclerAdapter() {
            mInflater = LayoutInflater.from(mContext);
            TypedArray styledAttributes = mContext.obtainStyledAttributes(new int[] {
                    R.attr.mediaRouteDefaultIconDrawable,
                    R.attr.mediaRouteTvIconDrawable,
                    R.attr.mediaRouteSpeakerIconDrawable,
                    R.attr.mediaRouteSpeakerGroupIconDrawable});
            mDefaultIcon = styledAttributes.getDrawable(0);
            mTvIcon = styledAttributes.getDrawable(1);
            mSpeakerIcon = styledAttributes.getDrawable(2);
            mSpeakerGroupIcon = styledAttributes.getDrawable(3);
            styledAttributes.recycle();
            setItems();
        }

        // Create a list of items with mRoutes and add them to mItems
        void setItems() {
            mItems = new ArrayList<>();
            ArrayList<MediaRouter.RouteInfo> routeGroups = new ArrayList<>();

            // Find route consists of multiple devices and add them to routeGroups
            for (int i = mRoutes.size(); i-- > 0; ) {
                MediaRouter.RouteInfo route = mRoutes.get(i);
                MediaRouteDescriptor descriptor = MediaRouteDescriptor.fromBundle(
                        route.getUniqueRouteDescriptorBundle());
                boolean isGroup = descriptor.getGroupMemberIds() != null;

                if (isGroup) {
                    routeGroups.add(route);
                    mRoutes.remove(i);
                }
            }

            // Add list items of single device section to mItems
            mItems.add(new Item(mContext.getString(R.string.mr_dialog_device_header)));
            for (MediaRouter.RouteInfo route : mRoutes) {
                mItems.add(new Item(route));
            }

            // Add list items of group section to mItems
            mItems.add(new Item(mContext.getString(R.string.mr_dialog_route_header)));
            for (MediaRouter.RouteInfo routeGroup : routeGroups) {
                mItems.add(new Item(routeGroup));
            }
            notifyDataSetChanged();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;

            switch (viewType) {
                case ITEM_TYPE_HEADER:
                    view = mInflater.inflate(R.layout.mr_picker_list_item_header, parent, false);
                    return new HeaderViewHolder(view);
                case ITEM_TYPE_ROUTE:
                    view = mInflater.inflate(R.layout.mr_picker_list_item_route, parent, false);
                    return new RouteViewHolder(view);
                default:
                    Log.w(TAG, "Cannot create ViewHolder because of wrong view type");
                    return null;
            }
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            int viewType = getItemViewType(position);
            Item item = getItem(position);

            switch (viewType) {
                case ITEM_TYPE_HEADER:
                    ((HeaderViewHolder) holder).binHeaderView(item);
                    break;
                case ITEM_TYPE_ROUTE:
                    ((RouteViewHolder) holder).bindRouteView(item);
                    break;
                default:
                    Log.w(TAG, "Cannot bind item to ViewHolder because of wrong view type");
            }
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        Drawable getIconDrawable(MediaRouter.RouteInfo route) {
            Uri iconUri = route.getIconUri();
            if (iconUri != null) {
                try {
                    InputStream is = mContext.getContentResolver().openInputStream(iconUri);
                    Drawable drawable = Drawable.createFromStream(is, null);
                    if (drawable != null) {
                        return drawable;
                    }
                } catch (IOException e) {
                    Log.w(TAG, "Failed to load " + iconUri, e);
                    // Falls back.
                }
            }
            return getDefaultIconDrawable(route);
        }

        private Drawable getDefaultIconDrawable(MediaRouter.RouteInfo route) {
            // If the type of the receiver device is specified, use it.
            switch (route.getDeviceType()) {
                case  MediaRouter.RouteInfo.DEVICE_TYPE_TV:
                    return mTvIcon;
                case MediaRouter.RouteInfo.DEVICE_TYPE_SPEAKER:
                    return mSpeakerIcon;
            }

            // Otherwise, make the best guess based on other route information.
            if (route instanceof MediaRouter.RouteGroup) {
                // Only speakers can be grouped for now.
                return mSpeakerGroupIcon;
            }
            return mDefaultIcon;
        }

        @Override
        public int getItemViewType(int position) {
            return mItems.get(position).getType();
        }

        public Item getItem(int position) {
            return mItems.get(position);
        }

        /**
         * Item class contains information of section header(text of section header) and
         * route(text of route name, icon of route type)
         */
        private class Item {
            private final Object mData;
            private final int mType;

            Item(Object data) {
                mData = data;

                if (data instanceof String) {
                    mType = ITEM_TYPE_HEADER;
                } else if (data instanceof MediaRouter.RouteInfo) {
                    mType = ITEM_TYPE_ROUTE;
                } else {
                    mType = ITEM_TYPE_NONE;
                    Log.w(TAG, "Wrong type of data passed to Item constructor");
                }
            }

            public Object getData() {
                return mData;
            }

            public int getType() {
                return mType;
            }
        }

        // ViewHolder for section header list item
        private class HeaderViewHolder extends RecyclerView.ViewHolder {
            TextView mTextView;

            HeaderViewHolder(View itemView) {
                super(itemView);
                mTextView = itemView.findViewById(R.id.mr_picker_header_name);
            }

            public void binHeaderView(Item item) {
                String headerName = item.getData().toString();

                mTextView.setText(headerName);
            }
        }

        // ViewHolder for route list item
        private class RouteViewHolder extends RecyclerView.ViewHolder {
            View mItemView;
            TextView mTextView;
            ImageView mImageView;

            RouteViewHolder(View itemView) {
                super(itemView);
                mItemView = itemView;
                mTextView = itemView.findViewById(R.id.mr_picker_route_name);
                mImageView = itemView.findViewById(R.id.mr_picker_route_icon);
            }

            public void bindRouteView(final Item item) {
                final MediaRouter.RouteInfo route = (MediaRouter.RouteInfo) item.getData();

                mItemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        route.select();
                    }
                });
                mTextView.setText(route.getName());
                mImageView.setImageDrawable(getIconDrawable(route));
            }
        }
    }
}
