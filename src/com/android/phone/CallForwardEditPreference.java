package com.android.phone;

import static com.android.phone.TimeConsumingPreferenceActivity.EXCEPTION_ERROR;
import static com.android.phone.TimeConsumingPreferenceActivity.RESPONSE_ERROR;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.BidiFormatter;
import android.text.SpannableString;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.flags.Flags;

import java.util.HashMap;
import java.util.Locale;

public class CallForwardEditPreference extends EditPhoneNumberPreference {
    private static final String LOG_TAG = "CallForwardEditPreference";

    private static final String SRC_TAGS[]       = {"{0}"};

    private static final int DEFAULT_NO_REPLY_TIMER_FOR_CFNRY = 20;

    private CharSequence mSummaryOnTemplate;
    /**
     * Remembers which button was clicked by a user. If no button is clicked yet, this should have
     * {@link DialogInterface#BUTTON_NEGATIVE}, meaning "cancel".
     *
     * TODO: consider removing this variable and having getButtonClicked() in
     * EditPhoneNumberPreference instead.
     */
    private int mButtonClicked;
    private int mServiceClass;
    private MyHandler mHandler = new MyHandler();
    int reason;
    private Phone mPhone;
    CallForwardInfo callForwardInfo;
    private TimeConsumingPreferenceListener mTcpListener;
    // Should we replace CF queries containing an invalid number with "Voicemail"
    private boolean mReplaceInvalidCFNumber = false;
    private boolean mCallForwardByUssd = false;
    private CarrierXmlParser mCarrierXmlParser;
    private int mPreviousCommand = MyHandler.MESSAGE_GET_CF;
    private Object mCommandException;
    private CarrierXmlParser.SsEntry.SSAction mSsAction =
            CarrierXmlParser.SsEntry.SSAction.UNKNOWN;
    private int mAction;
    private HashMap<String, String> mCfInfo;
    private long mDelayMillisAfterUssdSet = 1000;

    public CallForwardEditPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        mSummaryOnTemplate = this.getSummaryOn();

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.CallForwardEditPreference, 0, R.style.EditPhoneNumberPreference);
        mServiceClass = a.getInt(R.styleable.CallForwardEditPreference_serviceClass,
                CommandsInterface.SERVICE_CLASS_VOICE);
        reason = a.getInt(R.styleable.CallForwardEditPreference_reason,
                CommandsInterface.CF_REASON_UNCONDITIONAL);
        a.recycle();

        Log.d(LOG_TAG, "mServiceClass=" + mServiceClass + ", reason=" + reason);
    }

    public CallForwardEditPreference(Context context) {
        this(context, null);
    }

    void init(TimeConsumingPreferenceListener listener, Phone phone,
            boolean replaceInvalidCFNumber, boolean callForwardByUssd) {
        mPhone = phone;
        mTcpListener = listener;
        mReplaceInvalidCFNumber = replaceInvalidCFNumber;
        mCallForwardByUssd = callForwardByUssd;
        Log.d(LOG_TAG,
                "init :mReplaceInvalidCFNumber " + mReplaceInvalidCFNumber + ", mCallForwardByUssd "
                        + mCallForwardByUssd);
        if (mCallForwardByUssd) {
            mCfInfo = new HashMap<String, String>();
            TelephonyManager telephonyManager = new TelephonyManager(getContext(),
                    phone.getSubId());
            mCarrierXmlParser = new CarrierXmlParser(getContext(),
                    telephonyManager.getSimCarrierId());
        }
    }

    void restoreCallForwardInfo(CallForwardInfo cf) {
        handleCallForwardResult(cf);
        updateSummaryText();
    }

    @Override
    protected void onBindDialogView(View view) {
        // default the button clicked to be the cancel button.
        mButtonClicked = DialogInterface.BUTTON_NEGATIVE;
        super.onBindDialogView(view);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        super.onClick(dialog, which);
        mButtonClicked = which;
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        Log.d(LOG_TAG, "mButtonClicked=" + mButtonClicked + ", positiveResult=" + positiveResult);
        // Ignore this event if the user clicked the cancel button, or if the dialog is dismissed
        // without any button being pressed (back button press or click event outside the dialog).
        if (isUnknownStatus() && this.mButtonClicked != DialogInterface.BUTTON_NEGATIVE) {
            int action = (mButtonClicked == DialogInterface.BUTTON_POSITIVE) ?
                CommandsInterface.CF_ACTION_REGISTRATION :
                CommandsInterface.CF_ACTION_DISABLE;
            final String number = (action == CommandsInterface.CF_ACTION_DISABLE) ?
                    "" : getPhoneNumber();

            Log.d(LOG_TAG, "reason=" + reason + ", action=" + action + ", number=" + number);

            // Display no forwarding number while we're waiting for confirmation.
            setSummaryOff("");

            mPhone.setCallForwardingOption(action,
                    reason,
                    number,
                    mServiceClass,
                    0,
                    mHandler.obtainMessage(MyHandler.MESSAGE_SET_CF,
                        action,
                        MyHandler.MESSAGE_SET_CF));
        } else if (this.mButtonClicked != DialogInterface.BUTTON_NEGATIVE) {
            int action = (isToggled() || (mButtonClicked == DialogInterface.BUTTON_POSITIVE)) ?
                    CommandsInterface.CF_ACTION_REGISTRATION :
                    CommandsInterface.CF_ACTION_DISABLE;
            int time = 0;
            if (reason == CommandsInterface.CF_REASON_NO_REPLY) {
                PersistableBundle carrierConfig = PhoneGlobals.getInstance()
                        .getCarrierConfigForSubId(mPhone.getSubId());
                if (carrierConfig.getBoolean(
                        CarrierConfigManager.KEY_SUPPORT_NO_REPLY_TIMER_FOR_CFNRY_BOOL, true)) {
                    if (Flags.setNoReplyTimerForCfnry()) {
                        // Get timer value from carrier config
                        time = carrierConfig.getInt(
                                CarrierConfigManager.KEY_NO_REPLY_TIMER_FOR_CFNRY_SEC_INT,
                                DEFAULT_NO_REPLY_TIMER_FOR_CFNRY);
                    } else {
                        time = DEFAULT_NO_REPLY_TIMER_FOR_CFNRY;
                    }
                }
            }
            final String number = getPhoneNumber();

            Log.d(LOG_TAG, "callForwardInfo=" + callForwardInfo);

            if (action == CommandsInterface.CF_ACTION_REGISTRATION
                    && callForwardInfo != null
                    && callForwardInfo.status == 1
                    && number.equals(callForwardInfo.number)) {
                // no change, do nothing
                Log.d(LOG_TAG, "no change, do nothing");
            } else {
                // set to network
                Log.d(LOG_TAG, "reason=" + reason + ", action=" + action
                        + ", number=" + number);

                // Display no forwarding number while we're waiting for
                // confirmation
                setSummaryOn("");
                if (!mCallForwardByUssd) {
                    // the interface of Phone.setCallForwardingOption has error:
                    // should be action, reason...
                    mPhone.setCallForwardingOption(action,
                            reason,
                            number,
                            mServiceClass,
                            time,
                            mHandler.obtainMessage(MyHandler.MESSAGE_SET_CF,
                                    action,
                                    MyHandler.MESSAGE_SET_CF));
                } else {
                    if (action == CommandsInterface.CF_ACTION_REGISTRATION) {
                        mCfInfo.put(CarrierXmlParser.TAG_ENTRY_NUMBER, number);
                        mCfInfo.put(CarrierXmlParser.TAG_ENTRY_TIME, Integer.toString(time));
                    } else {
                        mCfInfo.clear();
                    }
                    mHandler.sendMessage(mHandler.obtainMessage(mHandler.MESSAGE_SET_CF_USSD,
                            action, MyHandler.MESSAGE_SET_CF));
                }
                if (mTcpListener != null) {
                    mTcpListener.onStarted(this, false);
                }
            }
        }
    }

    private void handleCallForwardResult(CallForwardInfo cf) {
        callForwardInfo = cf;
        Log.d(LOG_TAG, "handleGetCFResponse done, callForwardInfo=" + callForwardInfo);
        // In some cases, the network can send call forwarding URIs for voicemail that violate the
        // 3gpp spec. This can cause us to receive "numbers" that are sequences of letters. In this
        // case, we must detect these series of characters and replace them with "Voicemail".
        // PhoneNumberUtils#formatNumber returns null if the number is not valid.
        if (mReplaceInvalidCFNumber && !TextUtils.isEmpty(callForwardInfo.number)
                && (PhoneNumberUtils.formatNumber(callForwardInfo.number, getCurrentCountryIso())
                == null)) {
            callForwardInfo.number = getContext().getString(R.string.voicemail);
            Log.i(LOG_TAG, "handleGetCFResponse: Overridding CF number");
        }

        setUnknownStatus(callForwardInfo.status == CommandsInterface.SS_STATUS_UNKNOWN);
        setToggled(callForwardInfo.status == 1);
        boolean displayVoicemailNumber = false;
        if (TextUtils.isEmpty(callForwardInfo.number)) {
            PersistableBundle carrierConfig =
                    PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
            if (carrierConfig != null) {
                displayVoicemailNumber = carrierConfig.getBoolean(CarrierConfigManager
                        .KEY_DISPLAY_VOICEMAIL_NUMBER_AS_DEFAULT_CALL_FORWARDING_NUMBER_BOOL);
                Log.d(LOG_TAG, "display voicemail number as default");
            }
        }
        String voicemailNumber = mPhone.getVoiceMailNumber();
        setPhoneNumber(displayVoicemailNumber ? voicemailNumber : callForwardInfo.number);
    }

    /**
     * Starts the Call Forwarding Option query to the network and calls
     * {@link TimeConsumingPreferenceListener#onStarted}. Will call
     * {@link TimeConsumingPreferenceListener#onFinished} when finished, or
     * {@link TimeConsumingPreferenceListener#onError} if an error has occurred.
     */
    void startCallForwardOptionsQuery() {
        if (!mCallForwardByUssd) {
            mPhone.getCallForwardingOption(reason, mServiceClass,
                    mHandler.obtainMessage(MyHandler.MESSAGE_GET_CF,
                            // unused in this case
                            CommandsInterface.CF_ACTION_DISABLE,
                            MyHandler.MESSAGE_GET_CF, null));
        } else {
            mHandler.sendMessage(mHandler.obtainMessage(mHandler.MESSAGE_GET_CF_USSD,
                    // unused in this case
                    CommandsInterface.CF_ACTION_DISABLE, MyHandler.MESSAGE_GET_CF, null));
        }
        if (mTcpListener != null) {
            mTcpListener.onStarted(this, true);
        }
    }

    private void updateSummaryText() {
        if (isToggled()) {
            final String number = getRawPhoneNumber();
            if (number != null && number.length() > 0) {
                // Wrap the number to preserve presentation in RTL languages.
                String wrappedNumber = BidiFormatter.getInstance().unicodeWrap(
                        number, TextDirectionHeuristics.LTR);
                String values[] = { wrappedNumber };
                String summaryOn = String.valueOf(
                        TextUtils.replace(mSummaryOnTemplate, SRC_TAGS, values));
                int start = summaryOn.indexOf(wrappedNumber);

                SpannableString spannableSummaryOn = new SpannableString(summaryOn);
                PhoneNumberUtils.addTtsSpan(spannableSummaryOn,
                        start, start + wrappedNumber.length());
                setSummaryOn(spannableSummaryOn);
            } else {
                setSummaryOn(getContext().getString(R.string.sum_cfu_enabled_no_number));
            }
        }

    }

    /**
     * @return The ISO 3166-1 two letters country code of the country the user is in based on the
     *      network location.
     */
    private String getCurrentCountryIso() {
        final TelephonyManager telephonyManager =
                (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager == null) {
            return "";
        }
        return telephonyManager.getNetworkCountryIso().toUpperCase(Locale.ROOT);
    }

    // Message protocol:
    // what: get vs. set
    // arg1: action -- register vs. disable
    // arg2: get vs. set for the preceding request
    private class MyHandler extends Handler {
        static final int MESSAGE_GET_CF = 0;
        static final int MESSAGE_SET_CF = 1;
        static final int MESSAGE_GET_CF_USSD = 2;
        static final int MESSAGE_SET_CF_USSD = 3;

        TelephonyManager.UssdResponseCallback mUssdCallback =
                new TelephonyManager.UssdResponseCallback() {
                    @Override
                    public void onReceiveUssdResponse(final TelephonyManager telephonyManager,
                            String request, CharSequence response) {
                        if (mSsAction == CarrierXmlParser.SsEntry.SSAction.UNKNOWN) {
                            return;
                        }

                        HashMap<String, String> analysisResult = mCarrierXmlParser.getFeature(
                                CarrierXmlParser.FEATURE_CALL_FORWARDING)
                                .getResponseSet(mSsAction,
                                        response.toString());

                        Throwable throwableException = null;
                        if (analysisResult.get(CarrierXmlParser.TAG_RESPONSE_STATUS_ERROR)
                                != null) {
                            throwableException = new CommandException(
                                    CommandException.Error.GENERIC_FAILURE);
                        }

                        Object obj = null;
                        if (mSsAction == CarrierXmlParser.SsEntry.SSAction.QUERY) {
                            obj = makeCallForwardInfo(analysisResult);
                        }

                        sendCfMessage(obj, throwableException);
                    }

                    @Override
                    public void onReceiveUssdResponseFailed(final TelephonyManager telephonyManager,
                            String request, int failureCode) {
                        Log.d(LOG_TAG, "receive the ussd result failed");
                        Throwable throwableException = new CommandException(
                                CommandException.Error.GENERIC_FAILURE);
                        sendCfMessage(null, throwableException);
                    }
                };

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_GET_CF:
                    handleGetCFResponse(msg);
                    break;
                case MESSAGE_SET_CF:
                    handleSetCFResponse(msg);
                    break;
                case MESSAGE_GET_CF_USSD:
                    prepareUssdCommand(msg, CarrierXmlParser.SsEntry.SSAction.QUERY);
                    break;
                case MESSAGE_SET_CF_USSD:
                    prepareUssdCommand(msg, CarrierXmlParser.SsEntry.SSAction.UNKNOWN);
                    break;
            }
        }

        private void handleGetCFResponse(Message msg) {
            Log.d(LOG_TAG, "handleGetCFResponse: done");

            mTcpListener.onFinished(CallForwardEditPreference.this, msg.arg2 != MESSAGE_SET_CF);

            AsyncResult ar = (AsyncResult) msg.obj;

            callForwardInfo = null;
            boolean summaryOff = false;
            if (ar.exception != null) {
                Log.d(LOG_TAG, "handleGetCFResponse: ar.exception=" + ar.exception);
                if (ar.exception instanceof CommandException) {
                    mTcpListener.onException(CallForwardEditPreference.this,
                            (CommandException) ar.exception);
                } else {
                    // Most likely an ImsException and we can't handle it the same way as
                    // a CommandException. The best we can do is to handle the exception
                    // the same way as mTcpListener.onException() does when it is not of type
                    // FDN_CHECK_FAILURE.
                    mTcpListener.onError(CallForwardEditPreference.this, EXCEPTION_ERROR);
                }
            } else {
                if (ar.userObj instanceof Throwable) {
                    mTcpListener.onError(CallForwardEditPreference.this, RESPONSE_ERROR);
                }
                CallForwardInfo cfInfoArray[] = (CallForwardInfo[]) ar.result;
                if (cfInfoArray == null || cfInfoArray.length == 0) {
                    Log.d(LOG_TAG, "handleGetCFResponse: cfInfoArray.length==0");
                    if (!(ar.userObj instanceof Throwable)) {
                        mTcpListener.onError(CallForwardEditPreference.this, RESPONSE_ERROR);
                    }
                } else {
                    for (int i = 0, length = cfInfoArray.length; i < length; i++) {
                        Log.d(LOG_TAG, "handleGetCFResponse, cfInfoArray[" + i + "]="
                                + cfInfoArray[i]);
                        if ((mServiceClass & cfInfoArray[i].serviceClass) != 0) {
                            // corresponding class
                            CallForwardInfo info = cfInfoArray[i];
                            handleCallForwardResult(info);

                            summaryOff = (info.status == CommandsInterface.SS_STATUS_UNKNOWN);

                            if (ar.userObj instanceof Throwable) {
                                Log.d(LOG_TAG, "Skipped duplicated error dialog");
                                continue;
                            }

                            // Show an alert if we got a success response but
                            // with unexpected values.
                            // Handle the fail-to-disable case.
                            if (msg.arg2 == MESSAGE_SET_CF &&
                                    msg.arg1 == CommandsInterface.CF_ACTION_DISABLE &&
                                    info.status == 1) {
                                // Skip showing error dialog since some operators return
                                // active status even if disable call forward succeeded.
                                // And they don't like the error dialog.
                                if (isSkipCFFailToDisableDialog()) {
                                    Log.d(LOG_TAG, "Skipped Callforwarding fail-to-disable dialog");
                                    continue;
                                }
                                CharSequence s;
                                switch (reason) {
                                    case CommandsInterface.CF_REASON_BUSY:
                                        s = getContext().getText(R.string.disable_cfb_forbidden);
                                        break;
                                    case CommandsInterface.CF_REASON_NO_REPLY:
                                        s = getContext().getText(R.string.disable_cfnry_forbidden);
                                        break;
                                    default: // not reachable
                                        s = getContext().getText(R.string.disable_cfnrc_forbidden);
                                }
                                AlertDialog.Builder builder =
                                        FrameworksUtils.makeAlertDialogBuilder(getContext());
                                builder.setNeutralButton(R.string.close_dialog, null);
                                builder.setTitle(getContext()
                                        .getText(R.string.error_updating_title));
                                builder.setMessage(s);
                                builder.setCancelable(true);
                                builder.create().show();
                            } else if (msg.arg2 == MESSAGE_SET_CF &&
                                    msg.arg1 == CommandsInterface.CF_ACTION_REGISTRATION &&
                                    info.status == 0) {
                                // Handle the fail-to-enable case.
                                CharSequence s = getContext()
                                    .getText(R.string.registration_cf_forbidden);
                                AlertDialog.Builder builder =
                                        FrameworksUtils.makeAlertDialogBuilder(getContext());
                                builder.setNeutralButton(R.string.close_dialog, null);
                                builder.setTitle(getContext()
                                        .getText(R.string.error_updating_title));
                                builder.setMessage(s);
                                builder.setCancelable(true);
                                builder.create().show();
                            }
                        }
                    }
                }
            }

            // Now whether or not we got a new number, reset our enabled
            // summary text since it may have been replaced by an empty
            // placeholder.
            // for CDMA, doesn't display summary.
            if (summaryOff) {
                setSummaryOff("");
            } else {
                // Now whether or not we got a new number, reset our enabled
                // summary text since it may have been replaced by an empty
                // placeholder.
                updateSummaryText();
            }
        }

        private void handleSetCFResponse(Message msg) {
            AsyncResult ar = (AsyncResult) msg.obj;
            if (ar.exception != null) {
                Log.d(LOG_TAG, "handleSetCFResponse: ar.exception=" + ar.exception);
                // setEnabled(false);
            }

            if (ar.result != null) {
                int arr = (int)ar.result;
                if (arr == CommandsInterface.SS_STATUS_UNKNOWN) {
                    Log.d(LOG_TAG, "handleSetCFResponse: no need to re get in CDMA");
                    mTcpListener.onFinished(CallForwardEditPreference.this, false);
                    return;
                }
            }

            Log.d(LOG_TAG, "handleSetCFResponse: re get");
            if (!mCallForwardByUssd) {
                mPhone.getCallForwardingOption(reason, mServiceClass,
                        obtainMessage(MESSAGE_GET_CF, msg.arg1, MESSAGE_SET_CF, ar.exception));
            } else {
                mHandler.sendMessageDelayed(mHandler.obtainMessage(mHandler.MESSAGE_GET_CF_USSD,
                        msg.arg1, MyHandler.MESSAGE_SET_CF, ar.exception),
                        mDelayMillisAfterUssdSet);
            }
        }

        private void prepareUssdCommand(Message msg,
                CarrierXmlParser.SsEntry.SSAction inputSsAction) {
            mAction = msg.arg1;
            mPreviousCommand = msg.arg2;
            mCommandException = msg.obj;
            mSsAction = inputSsAction;

            if (mSsAction != CarrierXmlParser.SsEntry.SSAction.QUERY) {
                if (mAction == CommandsInterface.CF_ACTION_REGISTRATION) {
                    mSsAction = CarrierXmlParser.SsEntry.SSAction.UPDATE_ACTIVATE;
                } else {
                    mSsAction = CarrierXmlParser.SsEntry.SSAction.UPDATE_DEACTIVATE;
                }
            }

            new Thread(new Runnable() {
                @Override
                public void run() {
                    sendUssdCommand(mUssdCallback, mSsAction, mCfInfo.isEmpty() ? null : mCfInfo);
                }
            }).start();
        }

        private void sendUssdCommand(TelephonyManager.UssdResponseCallback inputCallback,
                CarrierXmlParser.SsEntry.SSAction inputAction,
                HashMap<String, String> inputCfInfo) {
            String newUssdCommand = mCarrierXmlParser.getFeature(
                    CarrierXmlParser.FEATURE_CALL_FORWARDING)
                    .makeCommand(inputAction, inputCfInfo);
            TelephonyManager telephonyManager =
                    (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
            telephonyManager.sendUssdRequest(newUssdCommand, inputCallback, mHandler);
        }

        private Message makeGetCfMessage(int inputMsgWhat, int inputMsgArg2, Object inputMsgObj) {
            return mHandler.obtainMessage(inputMsgWhat,
                    mAction,
                    inputMsgArg2,
                    inputMsgObj);
        }

        private Message makeSetCfMessage(int inputMsgWhat, int inputMsgArg2) {
            return mHandler.obtainMessage(inputMsgWhat,
                    mAction,
                    inputMsgArg2);
        }

        private void sendCfMessage(Object inputArObj, Throwable inputThrowableException) {
            Message message;
            if (mSsAction == CarrierXmlParser.SsEntry.SSAction.UNKNOWN) {
                return;
            }
            if (mSsAction == CarrierXmlParser.SsEntry.SSAction.QUERY) {
                message = makeGetCfMessage(MyHandler.MESSAGE_GET_CF, mPreviousCommand,
                        mCommandException);
            } else {
                message = makeSetCfMessage(MyHandler.MESSAGE_SET_CF, MyHandler.MESSAGE_SET_CF);
            }
            AsyncResult.forMessage(message, inputArObj, inputThrowableException);
            message.sendToTarget();
        }

        private CallForwardInfo[] makeCallForwardInfo(HashMap<String, String> inputInfo) {
            int tmpStatus = 0;
            String tmpNumberStr = "";
            int tmpTime = 0;
            if (inputInfo != null && inputInfo.size() != 0) {
                String tmpStatusStr = inputInfo.get(CarrierXmlParser.TAG_RESPONSE_STATUS);

                String tmpTimeStr = inputInfo.get(CarrierXmlParser.TAG_RESPONSE_TIME);
                if (!TextUtils.isEmpty(tmpStatusStr)) {
                    if (tmpStatusStr.equals(
                            CarrierXmlParser.TAG_COMMAND_RESULT_DEFINITION_ACTIVATE)) {
                        tmpStatus = 1;
                    } else if (tmpStatusStr.equals(
                            CarrierXmlParser.TAG_COMMAND_RESULT_DEFINITION_DEACTIVATE)
                            || tmpStatusStr.equals(
                            CarrierXmlParser.TAG_COMMAND_RESULT_DEFINITION_UNREGISTER)) {
                        tmpStatus = 0;
                    }
                }

                tmpNumberStr = inputInfo.get(CarrierXmlParser.TAG_RESPONSE_NUMBER);
                if (!TextUtils.isEmpty(tmpTimeStr)) {
                    tmpTime = Integer.valueOf(inputInfo.get(CarrierXmlParser.TAG_RESPONSE_TIME));
                }
            }

            CallForwardInfo[] newCallForwardInfo = new CallForwardInfo[1];
            newCallForwardInfo[0] = new CallForwardInfo();
            newCallForwardInfo[0].status = tmpStatus;
            newCallForwardInfo[0].reason = reason;
            newCallForwardInfo[0].serviceClass = mServiceClass;
            newCallForwardInfo[0].number = tmpNumberStr;
            newCallForwardInfo[0].timeSeconds = tmpTime;
            return newCallForwardInfo;
        }
    }

    /*
     * Get the config of whether skip showing CF fail-to-disable dialog
     * from carrier config manager.
     *
     * @return boolean value of the config
     */
    private boolean isSkipCFFailToDisableDialog() {
        PersistableBundle carrierConfig =
                PhoneGlobals.getInstance().getCarrierConfigForSubId(mPhone.getSubId());
        if (carrierConfig != null) {
            return carrierConfig.getBoolean(
                    CarrierConfigManager.KEY_SKIP_CF_FAIL_TO_DISABLE_DIALOG_BOOL);
        } else {
            // by default we should not skip
            return false;
        }
    }
}
