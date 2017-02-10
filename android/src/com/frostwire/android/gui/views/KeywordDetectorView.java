/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.Adapter;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.search.KeywordDetector;
import com.frostwire.util.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created on 11/29/16.
 *
 * @author gubatron
 * @author aldenml
 */

public final class KeywordDetectorView extends RelativeLayout implements KeywordDetector.KeywordDetectorListener {
    private Logger LOG = Logger.getLogger(KeywordDetectorView.class);
    private RecyclerView tagsRecyclerView;
    private KeywordFilterTagAdapter filterTagAdapter;

    public KeywordDetectorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray attributes = context.getTheme().obtainStyledAttributes(attrs, R.styleable.ProductCardView, 0, 0);
        // use XML attributes if we specify any.
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        View.inflate(getContext(), R.layout.view_keyword_detector, this);
        invalidate();
        initComponents();
    }

    private void initComponents() {
        tagsRecyclerView = (RecyclerView) findViewById(R.id.view_keyword_detector_tag_recyclerview);
        filterTagAdapter = new KeywordFilterTagAdapter();
        tagsRecyclerView.setAdapter(filterTagAdapter);
    }

    private void updateKeyword(final String keyword, final KeywordDetector.Feature feature, int appearances) {
        LOG.info("KeywordDetectorView.updateKeyword(feature="+feature+", keyword="+keyword+", appearances="+appearances+")");
        // update our internal view and placement depending on
        // how many appearances we have.
        if (filterTagAdapter != null) {
            filterTagAdapter.updateKeyword(new KeywordFilterTag(keyword, feature, appearances));
            if (tagsRecyclerView != null ) {
                tagsRecyclerView.setAdapter(filterTagAdapter);
            }
        }

        // TEMP: for now we'll have everybody together, but perhaps it might make sense
        // to keep labels grouped by Feature. this way we can have
        // 2 labels with the same word but which mean something else. e.g. a filename that's the same
        // as the name of a search engine, or file extension, they would be filtered differently.

        // for now let's just work on search sources
    }

    private void removeKeyword(KeywordDetector.Feature feature, String keyword) {
        // a keyword detector should invoke this on us
        // or we could have a reference to the keyword detector of the
        // ongoing search, and after we invoke it's histogram
        // method, then we add/remove keywords from our list
        // which in turn results in adding/removing our
        // KeywordLabelView.
    }

    private void onKeywordTouched(KeywordFilterTag keywordTag) {
        LOG.info("onKeywordTouched("+keywordTag.feature+", "+ keywordTag.keyword+")");
        filterTagAdapter.cycleMode(keywordTag);
    }

    @Override
    public void onSearchReceived(final KeywordDetector detector, final KeywordDetector.Feature feature, int numSearchesProcessed) {
        // for now we'll request a histogram update every 5 searches
        if (numSearchesProcessed % 5 == 0) {
            detector.requestHistogramUpdate(feature);
        }
    }

    @Override
    public void onHistogramUpdate(final KeywordDetector detector, final KeywordDetector.Feature feature, final Map.Entry<String, Integer>[] histogram) {
        LOG.info("KeywordDetectorView.onHistogramUpdate(...)");
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                for (Map.Entry<String, Integer> entry : histogram) {
                    updateKeyword(entry.getKey(), feature, entry.getValue());
                }
            }
        });
    }

    private class KeywordFilterTag {
        final String keyword;
        final KeywordDetector.Feature feature;
        final int appearances;

        KeywordFilterTag(String keyword, KeywordDetector.Feature feature, int appearances) {
            this.keyword = keyword;
            this.feature = feature;
            this.appearances = appearances;
        }

        @Override
        public boolean equals(Object obj) {
            KeywordFilterTag tag = (KeywordFilterTag) obj;
            return obj == this || (this.keyword.equals(tag.keyword) && this.feature.equals(tag.feature));
        }

        @Override
        public int hashCode() {
            return this.keyword.hashCode() * this.feature.ordinal();
        }
    }

    private class KeywordFilterTagAdapter extends RecyclerView.Adapter<KeywordFilterTagAdapter.ViewHolder> {
        // we want to speed up search on this adapter
        // as there will be lots of updateHistogram calls
        //private Map<KeywordFilterTag, KeywordFilterTag> cache;
        private Map<KeywordFilterTag, KeywordFilterTag> cache;
        private List<KeywordFilterTag> arrayList;
        private Map<KeywordFilterTag, Integer> reverseIndex;
        private Map<KeywordFilterTag, KeywordDetector.Mode> tagModes;

        public KeywordFilterTagAdapter() {
            cache = new HashMap<>();
            arrayList = new ArrayList<>();
            reverseIndex = new HashMap<>();
            tagModes = new HashMap<>();
        }

        @Override
        public KeywordFilterTagAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.view_keyword_tag, parent, false);
            return new ViewHolder(itemView);
        }

        @Override
        public void onBindViewHolder(KeywordFilterTagAdapter.ViewHolder holder, int position) {
            KeywordFilterTag keywordFilterTag = arrayList.get(position);
            if (keywordFilterTag != null) {
                holder.keyword.setText(keywordFilterTag.keyword);
                if (tagModes.containsKey(keywordFilterTag)) {
                    holder.tagMode.setText(" ("+tagModes.get(keywordFilterTag).toString().toLowerCase()+")");
                }
                final int positionCopy = position;
                holder.keyword.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onKeywordTouched(arrayList.get(positionCopy));
                    }
                });
            }
        }

        public void add(KeywordFilterTag tag) {
            cache.put(tag, tag);
            arrayList.add(tag);
            tagModes.put(tag, KeywordDetector.Mode.INACTIVE);

            Collections.sort(arrayList, new Comparator<KeywordFilterTag>() {
                @Override
                public int compare(KeywordFilterTag o1, KeywordFilterTag o2) {
                    int x = o1.appearances;
                    int y = o2.appearances;
                    return (x < y) ? -1 : ((x == y) ? 0 : 1);
                }
            });
            int index = arrayList.lastIndexOf(tag);
            reverseIndex.put(tag, index);
            notifyDataSetChanged();
        }

        public void updateKeyword(KeywordFilterTag tag) {
            reverseIndex.remove(tag);
            add(tag);
        }

        @Override
        public long getItemId(int position) {
            return arrayList.get(position).hashCode();
        }

        @Override
        public int getItemCount() {
            return arrayList.size();
        }

        public void cycleMode(KeywordFilterTag keywordTag) {
            if (tagModes.containsKey(keywordTag)) {
                KeywordDetector.Mode previousMode = tagModes.get(keywordTag);
                switch (previousMode) {
                    case INACTIVE:
                        tagModes.put(keywordTag, KeywordDetector.Mode.INCLUSIVE);
                        break;
                    case INCLUSIVE:
                        tagModes.put(keywordTag, KeywordDetector.Mode.EXCLUSIVE);
                        break;
                    case EXCLUSIVE:
                        tagModes.put(keywordTag, KeywordDetector.Mode.INACTIVE);
                        break;
                }
                notifyDataSetChanged();
            }
        }

        private class ViewHolder extends RecyclerView.ViewHolder {
            public TextView keyword;
            public TextView tagMode;
            public ViewHolder(View itemView) {
                super(itemView);
                keyword = (TextView) itemView.findViewById(R.id.view_keyword_tag_keyword);
                keyword.setClickable(true);
                tagMode = (TextView) itemView.findViewById(R.id.view_keyword_tag_mode);
                // TODO: set action listeners to change mode and trigger filtering in the search fragment.
            }
        }
    }
}