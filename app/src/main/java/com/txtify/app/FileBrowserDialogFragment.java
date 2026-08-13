package com.txtify.app;

import android.Manifest;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// New imports for modern Android permission handling
import android.os.Build;
import android.provider.Settings;
import android.net.Uri;
import android.content.Intent;

public class FileBrowserDialogFragment extends DialogFragment {

    // MODIFIED: Updated callback interface
    public interface FileBrowserCallbacks {
        void onFilesAdded(ArrayList<File> files);
        void onBrowserClosed();
    }

    private static final int PERMISSIONS_REQUEST_READ_STORAGE = 201;

    private RecyclerView recyclerView;
    private TextView currentPathText;
    private TextView emptyFolderText;
    private ImageButton upButton;
    private ImageButton addSelectedButton; // NEW
    private Button selectAllButton;
    private Button clearSelectionButton;
    private Button closeButton; // NEW
    private Button pasteSearchButton; // NEW: Paste and search button

    private File currentDirectory;
    private FileBrowserCallbacks callbacks;
    private FileBrowserAdapter adapter;

    // NEW: The fragment now owns the master set of selected files.
    private Set<File> selectedFiles = new HashSet<>();

    public static FileBrowserDialogFragment newInstance() {
        return new FileBrowserDialogFragment();
    }

    @Override
    public void onAttach(@NonNull android.content.Context context) {
        super.onAttach(context);
        if (context instanceof FileBrowserCallbacks) {
            callbacks = (FileBrowserCallbacks) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement FileBrowserCallbacks");
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.95);
            int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.85);
            dialog.getWindow().setLayout(width, height);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_file_browser, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        getDialog().setTitle("Select and Drag Files");

        recyclerView = view.findViewById(R.id.recycler_view_files);
        currentPathText = view.findViewById(R.id.text_current_path);
        emptyFolderText = view.findViewById(R.id.text_empty_folder);
        upButton = view.findViewById(R.id.button_up_directory);
        addSelectedButton = view.findViewById(R.id.button_add_selected); // NEW
        selectAllButton = view.findViewById(R.id.button_select_all);
        clearSelectionButton = view.findViewById(R.id.button_clear_selection);
        closeButton = view.findViewById(R.id.button_close_browser); // NEW
        pasteSearchButton = view.findViewById(R.id.button_paste_search); // NEW

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        upButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (currentDirectory != null && currentDirectory.getParentFile() != null) {
						navigateTo(currentDirectory.getParentFile());
					}
				}
			});

        // NEW: Listener for the "Add Selected" tick button
        addSelectedButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (callbacks != null && !selectedFiles.isEmpty()) {
						callbacks.onFilesAdded(new ArrayList<>(selectedFiles));
						if (adapter != null) {
							adapter.clearSelection();
						}
					} else {
						Toast.makeText(getContext(), "No files selected.", Toast.LENGTH_SHORT).show();
					}
				}
			});

        // NEW: Listener for the "Close" button
        closeButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					dismiss();
				}
			});

        selectAllButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (adapter != null) {
						adapter.selectAllFiles();
					}
				}
			});

        clearSelectionButton.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (adapter != null) {
						adapter.clearSelection();
					}
				}
			});

        // NEW: Listener for "Paste & Search" button
        if (pasteSearchButton != null) {
            pasteSearchButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showPasteSearchDialog();
                }
            });
        }

        checkAndRequestPermissions();
    }

    /**
     * Shows the paste dialog where user pastes file names list.
     * Launches a recursive search starting from the current directory.
     */
    private void showPasteSearchDialog() {
        if (currentDirectory == null) {
            Toast.makeText(getContext(), "Please select a parent directory first.", Toast.LENGTH_SHORT).show();
            return;
        }

        PasteListDialogFragment dialog = PasteListDialogFragment.newInstance();
        dialog.setOnListPastedListener(new PasteListDialogFragment.OnListPastedListener() {
            @Override
            public void onListPasted(Set<String> parsedFileNames) {
                new RecursiveSearchTask(currentDirectory, parsedFileNames).execute();
            }
        });
        dialog.show(getChildFragmentManager(), "paste_search_dialog");
    }

    /**
     * AsyncTask to recursively traverse all subfolders under currentDirectory
     * and select any matching files automatically.
     */
    private class RecursiveSearchTask extends AsyncTask<Void, Void, List<File>> {
        private final File parentDir;
        private final Set<String> targetNames;
        private ProgressDialog progressDialog;

        public RecursiveSearchTask(File parentDir, Set<String> targetNames) {
            this.parentDir = parentDir;
            this.targetNames = targetNames;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog = new ProgressDialog(getContext());
            progressDialog.setMessage("Searching subfolders for matching files...");
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @Override
        protected List<File> doInBackground(Void... voids) {
            List<File> matchedFiles = new ArrayList<>();
            searchFolderRecursively(parentDir, targetNames, matchedFiles);
            return matchedFiles;
        }

        private void searchFolderRecursively(File dir, Set<String> targets, List<File> result) {
            if (dir == null || !dir.exists() || !dir.isDirectory() || !dir.canRead()) {
                return;
            }

            File[] files = dir.listFiles();
            if (files == null) {
                return;
            }

            for (File file : files) {
                if (file.isDirectory()) {
                    searchFolderRecursively(file, targets, result);
                } else if (file.isFile()) {
                    if (FileNameParserUtils.matchesAny(file.getName(), targets)) {
                        result.add(file);
                    }
                }
            }
        }

        @Override
        protected void onPostExecute(List<File> matchedFiles) {
            super.onPostExecute(matchedFiles);
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }

            if (matchedFiles.isEmpty()) {
                Toast.makeText(getContext(), "No matching files found in subfolders.", Toast.LENGTH_LONG).show();
            } else {
                selectedFiles.addAll(matchedFiles);
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
                Toast.makeText(getContext(), "Found & selected " + matchedFiles.size() + " file(s) across subfolders.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        // MODIFIED: Use the new callback method
        if (callbacks != null) {
            callbacks.onBrowserClosed();
        }
    }

    private void navigateTo(File directory) {
        if (!directory.isDirectory() || !directory.canRead()) {
            Toast.makeText(getContext(), "Cannot access this folder.", Toast.LENGTH_SHORT).show();
            return;
        }

        currentDirectory = directory;
        currentPathText.setText(directory.getAbsolutePath());

        File[] filesArray = directory.listFiles();
        List<File> files;
        if (filesArray != null) {
            files = new ArrayList<>(Arrays.asList(filesArray));
        } else {
            files = new ArrayList<>();
        }

        if (files.isEmpty()) {
            emptyFolderText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyFolderText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }

        // MODIFIED: Pass the master selectedFiles set to the adapter's constructor.
        adapter = new FileBrowserAdapter(getContext(), files, selectedFiles,
            new FileBrowserAdapter.OnItemClickListener() {
                @Override
                public void onItemClick(File file) {
                    if (file.isDirectory()) {
                        navigateTo(file);
                    }
                    // A short click on a file now toggles selection (handled in adapter)
                }
            },
            new FileBrowserAdapter.OnDragStartListener() {
                @Override
                public void onDragStarted() {
                    dismiss(); // Dismiss the dialog if a drag is started.
                }
            }
        );

        recyclerView.setAdapter(adapter);
    }

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Under Android 11 to 15+, check for All Files Access Manager permission
            if (Environment.isExternalStorageManager()) {
                initializeBrowser();
            } else {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.addCategory("android.intent.category.DEFAULT");
                    intent.setData(Uri.parse(String.format("package:%s", getContext().getPackageName())));
                    startActivityForResult(intent, PERMISSIONS_REQUEST_READ_STORAGE);
                } catch (Exception e) {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivityForResult(intent, PERMISSIONS_REQUEST_READ_STORAGE);
                }
            }
        } else {
            // Fallback for Android 10 and older devices
            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                                   PERMISSIONS_REQUEST_READ_STORAGE);
            } else {
                initializeBrowser();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_READ_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeBrowser();
            } else {
                Toast.makeText(getContext(), "Read permission is required to browse files.", Toast.LENGTH_LONG).show();
                dismiss();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PERMISSIONS_REQUEST_READ_STORAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    initializeBrowser();
                } else {
                    Toast.makeText(getContext(), "All Files Access permission is required to browse files.", Toast.LENGTH_LONG).show();
                    dismiss();
                }
            }
        }
    }

    private void initializeBrowser() {
        File root = Environment.getExternalStorageDirectory();
        navigateTo(root);
    }
}