package com.txtify.app;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import java.util.Set;

/**
 * DialogFragment that provides a text area for users to paste a list of file names.
 * Supports pasting directly from system clipboard and parses clean file names.
 */
public class PasteListDialogFragment extends DialogFragment {

    /**
     * Callback interface to return the parsed file names back to the caller.
     */
    public interface OnListPastedListener {
        void onListPasted(Set<String> parsedFileNames);
    }

    private EditText editTextPasteInput;
    private Button buttonPasteClipboard;
    private Button buttonApply;
    private Button buttonCancel;

    private OnListPastedListener listener;

    public static PasteListDialogFragment newInstance() {
        return new PasteListDialogFragment();
    }

    public void setOnListPastedListener(OnListPastedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90);
            int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.65);
            dialog.getWindow().setLayout(width, height);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_paste_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getDialog() != null) {
            getDialog().setTitle("Paste File Names List");
        }

        editTextPasteInput = view.findViewById(R.id.edit_text_paste_input);
        buttonPasteClipboard = view.findViewById(R.id.button_paste_clipboard);
        buttonApply = view.findViewById(R.id.button_apply_paste);
        buttonCancel = view.findViewById(R.id.button_cancel_paste);

        buttonPasteClipboard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pasteFromClipboard();
            }
        });

        buttonApply.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String rawText = editTextPasteInput.getText().toString();
                if (rawText.trim().isEmpty()) {
                    Toast.makeText(getContext(), "Please paste or enter file names.", Toast.LENGTH_SHORT).show();
                    return;
                }

                Set<String> parsedNames = FileNameParserUtils.parseFileNames(rawText);
                if (parsedNames.isEmpty()) {
                    Toast.makeText(getContext(), "No valid file names found in pasted text.", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (listener != null) {
                    listener.onListPasted(parsedNames);
                }

                dismiss();
            }
        });

        buttonCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
    }

    private void pasteFromClipboard() {
        if (getContext() == null) {
            return;
        }

        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip() != null) {
            ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
            if (item != null && item.getText() != null) {
                String clipboardText = item.getText().toString();
                editTextPasteInput.setText(clipboardText);
                editTextPasteInput.setSelection(clipboardText.length());
                Toast.makeText(getContext(), "Pasted from clipboard!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "Clipboard is empty or contains non-text content.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(getContext(), "Nothing found in clipboard.", Toast.LENGTH_SHORT).show();
        }
    }
}