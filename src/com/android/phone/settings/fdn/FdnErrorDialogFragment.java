/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.phone.settings.fdn;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.WindowManager;

import com.android.phone.R;

/**
 * Dialog Fragment that displays dialogs indicating error messages for following scenarios:
 *
 * 1. When user fails PIN2 authentication and PIN2 is locked, show the dialog indicating that PIN2
 * is locked and PUK2 must be entered.
 * 2. When user fails PUK2 authentication and PUK2 is locked, show the dialog indicating that PUK2
 * is locked and user must contact service provider to unlock PUK2.
 * 3. When user enters incorrect PIN2 in Change PIN2 option in FDN settings, show the dialog
 * indicating that PIN2 is incorrect along with no. of remaining attempts.
 * 4. When user enters incorrect PUK2 in Change PIN2 option FDN Settings, show the dialog
 * indicating that wrong PUK2 is entered along with no. of remaining attempts.
 * 5. When user enters invalid PIN2 when enabling FDN in FDN Settings, show the dialog indicating
 * that PIN2 is invalid along with no. of remaining attempts.
 * 6. When user fails to enable FDN in FDN Settings, show the dialog indicating fdn is failed
 * along with no. of remaining attempts.
 */
public class FdnErrorDialogFragment extends DialogFragment {

    static final String TAG_PIN2_LOCKED_DIALOG = "tag_pin2_locked_dialog";
    static final String KEY_DIALOG_ID = "key_dialog_id";
    static final String KEY_ERROR_MSG = "key_error_msg";
    static final String KEY_ATTEMPTS_REMAINING = "key_attempts_remaining";

    // AlertDialog IDs
    static final int DIALOG_ID_PUK2_LOCKED_OUT = 10;
    static final int DIALOG_ID_PUK2_REQUESTED_ON_PIN_ENTRY = 11;
    static final int DIALOG_ID_PUK2_REQUESTED_ON_PIN_CHANGED = 12;
    static final int DIALOG_ID_INCORRECT_PIN2_ENTRY = 13;
    static final int DIALOG_ID_INCORRECT_PUK2_ENTRY = 14;
    static final int DIALOG_ID_INVALID_PIN2_ENTRY = 15;
    static final int DIALOG_ID_FDN_FAILED_ERROR = 16;
    static final int DIALOG_ID_PIN2_ALREADY_BLOCKED = 17;

    private Listener mListener;
    private int mId;

    interface Listener {
        void onRequestPuk2(int id);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);
        Activity activity = getActivity();
        if (!(activity instanceof Listener)) {
            return null;
        }
        mListener = (Listener) activity;
        mId = getArguments().getInt(KEY_DIALOG_ID);

        switch (mId) {
            case DIALOG_ID_PUK2_LOCKED_OUT -> {
                AlertDialog alert = new AlertDialog.Builder(activity)
                        .setMessage(R.string.puk2_locked)
                        .setCancelable(true)
                        .create();
                alert.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                alert.setButton(DialogInterface.BUTTON_NEUTRAL, getText(R.string.ok),
                        (dialog, which) -> {
                        });
                return alert;
            }

            case DIALOG_ID_PUK2_REQUESTED_ON_PIN_CHANGED, DIALOG_ID_PUK2_REQUESTED_ON_PIN_ENTRY,
                 DIALOG_ID_PIN2_ALREADY_BLOCKED -> {
                AlertDialog alert = new AlertDialog.Builder(activity)
                        .setMessage(mId == DIALOG_ID_PIN2_ALREADY_BLOCKED
                                ? R.string.pin2_blocked : R.string.puk2_requested)
                        .setCancelable(true)
                        .create();
                alert.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                alert.setButton(DialogInterface.BUTTON_NEUTRAL, getText(R.string.ok),
                        (dialog, which) -> {
                            mListener.onRequestPuk2(mId);
                            dialog.dismiss();
                        });
                return alert;
            }
            case DIALOG_ID_INCORRECT_PIN2_ENTRY, DIALOG_ID_INCORRECT_PUK2_ENTRY,
                 DIALOG_ID_INVALID_PIN2_ENTRY, DIALOG_ID_FDN_FAILED_ERROR -> {
                AlertDialog alert = new AlertDialog.Builder(activity)
                        .setMessage(getDisplayMessage(getArguments().getInt(KEY_ERROR_MSG),
                                getArguments().getInt(KEY_ATTEMPTS_REMAINING)))
                        .setCancelable(true)
                        .create();
                alert.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                alert.setButton(DialogInterface.BUTTON_NEUTRAL, getText(R.string.ok),
                        (dialog, which) -> {
                        });
                return alert;
            }
            default -> {
                return null;
            }
        }
    }

    private String getDisplayMessage(int errorStrResId, int attemptsRemaining) {
        String errorMsg = getString(errorStrResId);
        if ((errorStrResId == R.string.badPin2) || (errorStrResId == R.string.badPuk2) ||
                (errorStrResId == R.string.pin2_invalid)) {
            if (attemptsRemaining >= 0) {
                errorMsg = getString(errorStrResId)
                        + getString(R.string.pin2_attempts, attemptsRemaining);
            } else {
                errorMsg = getString(errorStrResId);
            }
        }
        return errorMsg;
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        if (mId == DIALOG_ID_PUK2_REQUESTED_ON_PIN_CHANGED
                || mId == DIALOG_ID_PUK2_REQUESTED_ON_PIN_ENTRY
                || mId == DIALOG_ID_PIN2_ALREADY_BLOCKED) {
            mListener.onRequestPuk2(mId);
        }
        dialog.dismiss();
    }
}

