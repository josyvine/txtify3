package com.yard.txtify.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ZipContentsAdapter extends RecyclerView.Adapter<ZipContentsAdapter.ViewHolder> {

    private List<ZipEntryItem> zipEntries;
    private Context context;

    public ZipContentsAdapter(Context context, List<ZipEntryItem> zipEntries) {
        this.context = context;
        this.zipEntries = zipEntries;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.list_item_zip_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {
        final ZipEntryItem item = zipEntries.get(position);

        holder.fileName.setText(item.getFileName());
        holder.fileSubfolder.setText(item.getSubfolder());

        if (item.isIncluded()) {
            holder.statusIcon.setImageResource(R.drawable.ic_file_included);
            holder.itemView.setAlpha(1.0f); // Fully opaque
        } else {
            holder.statusIcon.setImageResource(R.drawable.ic_file_excluded);
            holder.itemView.setAlpha(0.5f); // Semi-transparent to indicate exclusion
        }

        holder.itemView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					// Toggle the inclusion state
					item.setIncluded(!item.isIncluded());
					// Notify the adapter that this specific item has changed, so it redraws itself
					notifyItemChanged(holder.getAdapterPosition());
				}
			});
    }

    @Override
    public int getItemCount() {
        return zipEntries.size();
    }

    // A helper method to get only the files that are still marked for inclusion
    public ArrayList<ZipEntryItem> getIncludedItems() {
        ArrayList<ZipEntryItem> included = new ArrayList<>();
        for (ZipEntryItem item : zipEntries) {
            if (item.isIncluded()) {
                included.add(item);
            }
        }
        return included;
    }

    /**
     * Filters and includes ONLY files matching the target file names list.
     * All non-matching files are automatically excluded.
     *
     * @param targetFileNames Set of sanitized file names.
     * @return Number of files matched and included.
     */
    public int applyTargetFileNamesFilter(Set<String> targetFileNames) {
        if (targetFileNames == null || targetFileNames.isEmpty() || zipEntries == null) {
            return 0;
        }

        int matchCount = 0;
        for (ZipEntryItem item : zipEntries) {
            String fileName = item.getFileName();
            String fullPath = item.getFullPath();

            // Check match against simple file name or full relative path inside the ZIP
            boolean isMatched = FileNameParserUtils.matchesAny(fileName, targetFileNames)
                    || FileNameParserUtils.matchesAny(fullPath, targetFileNames);

            if (isMatched) {
                item.setIncluded(true);
                matchCount++;
            } else {
                item.setIncluded(false);
            }
        }

        notifyDataSetChanged();
        return matchCount;
    }

    /**
     * Marks all files as included.
     */
    public void selectAll() {
        if (zipEntries == null) {
            return;
        }
        for (ZipEntryItem item : zipEntries) {
            item.setIncluded(true);
        }
        notifyDataSetChanged();
    }

    /**
     * Marks all files as excluded.
     */
    public void clearAll() {
        if (zipEntries == null) {
            return;
        }
        for (ZipEntryItem item : zipEntries) {
            item.setIncluded(false);
        }
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView statusIcon;
        TextView fileName;
        TextView fileSubfolder;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            statusIcon = itemView.findViewById(R.id.file_status_icon);
            fileName = itemView.findViewById(R.id.file_name);
            fileSubfolder = itemView.findViewById(R.id.file_subfolder);
        }
    }
}