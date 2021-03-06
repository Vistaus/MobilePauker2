package com.daniel.mobilepauker2.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.ankushgrover.hourglass.Hourglass;
import com.daniel.mobilepauker2.PaukerManager;
import com.daniel.mobilepauker2.R;
import com.daniel.mobilepauker2.model.CardPackAdapter;
import com.daniel.mobilepauker2.model.FlashCard;
import com.daniel.mobilepauker2.model.MPEditText;
import com.daniel.mobilepauker2.model.MPTextView;
import com.daniel.mobilepauker2.model.ModelManager.LearningPhase;
import com.daniel.mobilepauker2.model.SettingsManager;
import com.daniel.mobilepauker2.model.pauker_native.Font;
import com.daniel.mobilepauker2.utils.Constants;
import com.daniel.mobilepauker2.utils.ErrorReporter;
import com.daniel.mobilepauker2.utils.Log;
import com.danilomendes.progressbar.InvertedTextProgressbar;

import java.util.Locale;
import java.util.Random;

import static android.app.Notification.VISIBILITY_PUBLIC;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.daniel.mobilepauker2.PaukerManager.showToast;
import static com.daniel.mobilepauker2.model.FlashCard.SideShowing.SIDE_A;
import static com.daniel.mobilepauker2.model.FlashCard.SideShowing.SIDE_B;
import static com.daniel.mobilepauker2.model.ModelManager.LearningPhase.FILLING_USTM;
import static com.daniel.mobilepauker2.model.ModelManager.LearningPhase.NOTHING;
import static com.daniel.mobilepauker2.model.ModelManager.LearningPhase.REPEATING_LTM;
import static com.daniel.mobilepauker2.model.ModelManager.LearningPhase.REPEATING_STM;
import static com.daniel.mobilepauker2.model.ModelManager.LearningPhase.REPEATING_USTM;
import static com.daniel.mobilepauker2.model.ModelManager.LearningPhase.SIMPLE_LEARNING;
import static com.daniel.mobilepauker2.model.ModelManager.LearningPhase.WAITING_FOR_STM;
import static com.daniel.mobilepauker2.model.ModelManager.LearningPhase.WAITING_FOR_USTM;
import static com.daniel.mobilepauker2.model.SettingsManager.Keys.AUTO_SAVE;
import static com.daniel.mobilepauker2.model.SettingsManager.Keys.STM;
import static com.daniel.mobilepauker2.model.SettingsManager.Keys.USTM;
import static com.daniel.mobilepauker2.utils.Constants.TIMER_BAR_CHANNEL_ID;
import static com.daniel.mobilepauker2.utils.Constants.TIME_BAR_ID;
import static com.daniel.mobilepauker2.utils.Constants.TIME_NOTIFY_ID;

public class LearnCardsActivity extends FlashCardSwipeScreenActivity {
    private final PaukerManager paukerManager = PaukerManager.instance();
    private final Context context = this;
    private NotificationManagerCompat notificationManager;
    private boolean flipCardSides = false;
    private boolean completedLearning = false;
    private boolean repeatingLTM = false;
    private boolean stopWaiting = false;
    private boolean firstStart = true;
    private boolean isActivityVisible;
    private boolean showNotify;
    private boolean ustmTimerFinished = true;
    private boolean stmTimerFinished = true;
    private Hourglass ustmTimer;
    private Hourglass stmTimer;
    private int ustmTotalTime;
    private int stmTotalTime;
    private int initStackSize;
    private InvertedTextProgressbar ustmTimerBar;
    private InvertedTextProgressbar stmTimerBar;
    private Button bNext;
    private Button bShowMe;
    private RelativeLayout lRepeatButtons;
    private RelativeLayout lSkipWaiting;
    private MenuItem pauseButton;
    private MenuItem restartButton;
    private RelativeLayout timerAnimation;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createNotificationChannel();
        createTimerBarChannel();
        showNotify = settingsManager.getBoolPreference(context, SettingsManager.Keys.SHOW_NOTIFY);

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.learn_cards);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (modelManager.getLearningPhase() != REPEATING_LTM
                && (modelManager.getLearningPhase() != SIMPLE_LEARNING
                || modelManager.getLearningPhase() != NOTHING)) {
            // A check on mActivitySetupOk is done here as onCreate is called even if the
            // super (FlashCardSwipeScreenActivity) onCreate fails to find any cards and calls finish()
            if (mActivitySetupOk) {
                initTimer();
                if (ustmTimer != null && stmTimer != null) {
                    findViewById(R.id.lTimerFrame).setVisibility(VISIBLE);
                    startAllTimer();
                    timerAnimation = findViewById(R.id.timerAnimationPanel);
                }
            }
        } else if (modelManager.getLearningPhase() == REPEATING_LTM) {
            repeatingLTM = true;
        }

        initButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();

        isActivityVisible = true;
        if (notificationManager == null) {
            notificationManager = NotificationManagerCompat.from(context);
        }
        notificationManager.cancelAll();

        if (!firstStart && !restartButton.isVisible()) {
            restartTimer();

            if (mCardCursor != null && mSavedCursorPosition != -1) {
                refreshCursor();
            }
        }

        LearningPhase learningPhase = modelManager.getLearningPhase();
        if (learningPhase == WAITING_FOR_USTM || learningPhase == WAITING_FOR_STM) {
            showHideTimerAnimation();
        }

        firstStart = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        isActivityVisible = false;
        mSavedCursorPosition = mCardCursor.getPosition();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Constants.REQUEST_CODE_EDIT_CARD && resultCode == RESULT_OK) {
            updateCurrentCard();
            fillInData(flipCardSides);
            setButtonsVisibility();
            if (bShowMe.getVisibility() == VISIBLE
                    && ((flipCardSides && currentCard.getSide() == SIDE_A)
                    || (!flipCardSides && currentCard.getSide() == SIDE_B))) {
                bShowMe.setVisibility(GONE);
                lRepeatButtons.setVisibility(VISIBLE);
            }
        } else if (requestCode == Constants.REQUEST_CODE_SAVE_DIALOG_NORMAL) {
            if (resultCode == RESULT_OK) {
                showToast((Activity) context, R.string.saving_success, Toast.LENGTH_SHORT);
                paukerManager.setSaveRequired(false);
                modelManager.showExpireToast(context);

                /*if (settingsManager.getBoolPreference(context, SettingsManager.Keys.AUTO_SYNC)) {
                    String accessToken = PreferenceManager.getDefaultSharedPreferences(context)
                            .getString(Constants.DROPBOX_ACCESS_TOKEN, null);
                    Intent intent = new Intent(context, SyncDialog.class);
                    intent.putExtra(SyncDialog.ACCESS_TOKEN, accessToken);
                    intent.putExtra(SyncDialog.FILES, new File(paukerManager.getFileAbsolutePath()));
                    startActivity(intent);
                }*/
            } else {
                showToast((Activity) context, R.string.saving_error, Toast.LENGTH_SHORT);
            }
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.exit_learning_dialog)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        stopSTMTimer();
                        stopUSTMTimer();
                        finish();
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                })
                .create().show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.learning_cards, menu);

        pauseButton = menu.findItem(R.id.mPauseButton);
        restartButton = menu.findItem(R.id.mRestartButton);

        LearningPhase learningPhase = modelManager.getLearningPhase();
        if (learningPhase == REPEATING_LTM
                || learningPhase == SIMPLE_LEARNING
                || learningPhase == NOTHING
                || learningPhase == REPEATING_STM) {
            pauseButton.setVisible(false);
            restartButton.setVisible(false);
        }

        return true;
    }

    private void initTimer() {
        if (ustmTimer == null) {
            ustmTotalTime = Integer.parseInt(settingsManager.getStringPreference(context, USTM));
            ustmTimer = new Hourglass(ustmTotalTime * 1000) {
                @Override
                public void onTimerTick(long timeRemaining) {
                    if (timeRemaining > 0) {
                        if (ustmTimerBar.getVisibility() == VISIBLE
                                && !ustmTimerFinished) {
                            int totalSec = (int) (timeRemaining / 1000);
                            int timeElapsed = ustmTotalTime - totalSec;
                            int sec = timeElapsed % 60;

                            String timerText = String.format(Locale.getDefault()
                                    , "%d / %ds", sec, ustmTotalTime);
                            ustmTimerBar.setProgress(timeElapsed);
                            ustmTimerBar.setText(timerText);
                        }
                    } else {
                        stopUSTMTimer();
                        if (modelManager.getLearningPhase() == WAITING_FOR_USTM) {
                            stopWaiting = true;
                            updateLearningPhase();
                        }
                    }
                }

                @Override
                public void onTimerFinish() {
                    Log.d("LearnActivity::USTM-Timer finished", "Timer finished");
                    if (!isActivityVisible && ustmTimerFinished && showNotify) {
                        Log.d("LearnActivity::USTM-Timer finished", "Acivity is not visible");
                        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context, TIMER_BAR_CHANNEL_ID)
                                .setContentText(getString(R.string.ustm_expired_notify_message))
                                .setSmallIcon(R.drawable.notify_icon)
                                .setContentTitle(getString(R.string.app_name))
                                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                .setContentIntent(PendingIntent.getActivity(context, 0, getIntent(), 0))
                                .setVisibility(VISIBILITY_PUBLIC);
                        Log.d("LearnActivity::USTM-Timer finished", "Notification created");
                        notificationManager.notify(TIME_NOTIFY_ID, mBuilder.build());
                        Log.d("LearnActivity::USTM-Timer finished", "Show Notification");
                    }
                }
            };
            ustmTimerBar = findViewById(R.id.UKZGTimerBar);
            ustmTimerBar.setMaxProgress(ustmTotalTime);
            ustmTimerBar.setMinProgress(0);
        }
        if (stmTimer == null) {
            stmTotalTime = Integer.parseInt(settingsManager.getStringPreference(context, STM));
            stmTimer = new Hourglass(stmTotalTime * 60 * 1000) {
                @Override
                public void onTimerTick(long timeRemaining) {
                    if (timeRemaining > 0
                            && stmTimerBar.getVisibility() == VISIBLE
                            && !stmTimerFinished) {
                        String timerText;
                        int totalSec = (int) (timeRemaining / 1000);
                        int timeElapsed = stmTotalTime * 60 - totalSec;
                        int sec = timeElapsed % 60;
                        int min = timeElapsed / 60;
                        if (sec < 10) {
                            timerText = String.format(Locale.getDefault(),
                                    "%d:0%d / %d:00min", min, sec, stmTotalTime);
                        } else {
                            timerText = String.format(Locale.getDefault(),
                                    "%d:%d / %d:00min", min, sec, stmTotalTime);
                        }
                        stmTimerBar.setProgress(timeElapsed);
                        stmTimerBar.setText(timerText);

                        // Ist die App pausiert, soll in der Titelleiste die Zeit angezeigt werden
                        if (!isActivityVisible && !stmTimerFinished) {
                            Log.d("LearnActivity::STM-onTimerClick", "Acivity is not visible");
                            String ustmTimerText = ustmTimerFinished ? ""
                                    : getString(R.string.ustm) + " " + String.format(Locale.getDefault()
                                    , "%d / %ds", sec, ustmTotalTime);
                            String timerbarText = ustmTimerText + "  " +
                                    getString(R.string.stm) + " " + timerText;
                            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context, Constants.TIMER_BAR_CHANNEL_ID)
                                    .setContentText(timerbarText)
                                    .setSmallIcon(R.drawable.notify_icon)
                                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                    .setContentIntent(PendingIntent.getActivity(context, 0, getIntent(), 0))
                                    .setAutoCancel(true)
                                    .setOngoing(true);
                            Log.d("LearnActivity::STM-onTimerClick", "Notification created");
                            notificationManager.notify(TIME_BAR_ID, mBuilder.build());
                            Log.d("LearnActivity::STM-onTimerClick", "Show Notification");
                        }
                    } else {
                        stopSTMTimer();
                        if (modelManager.getLearningPhase() == WAITING_FOR_STM) {
                            stopWaiting = true;
                            updateLearningPhase();
                        }
                    }
                }

                @Override
                public void onTimerFinish() {
                    Log.d("LearnActivity::STM-Timer finished", "Timer finished");
                    if (!isActivityVisible && stmTimerFinished && showNotify) {
                        Log.d("LearnActivity::STM-Timer finished", "Acivity is visible");
                        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context, Constants.TIMER_NOTIFY_CHANNEL_ID)
                                .setContentText(getString(R.string.stm_expired_notify_message))
                                .setSmallIcon(R.drawable.notify_icon)
                                .setContentTitle(getString(R.string.app_name))
                                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                .setContentIntent(PendingIntent.getActivity(context, 0, getIntent(), 0))
                                .setAutoCancel(true)
                                .setVisibility(VISIBILITY_PUBLIC);
                        Log.d("LearnActivity::STM-Timer finished", "Notification created");
                        notificationManager.notify(TIME_NOTIFY_ID, mBuilder.build());
                        Log.d("LearnActivity::STM-Timer finished", "Show Notification");
                    }
                }
            };
            stmTimerBar = findViewById(R.id.KZGTimerBar);
            stmTimerBar.setMaxProgress(stmTotalTime * 60);
            stmTimerBar.setMinProgress(0);
        }
    }

    private void startAllTimer() {
        startUSTMTimer();
        startSTMTimer();
    }

    private void startUSTMTimer() {
        if (ustmTimer != null && ustmTimerFinished) {
            ustmTimer.startTimer();
            ustmTimerFinished = false;
        }
    }

    private void startSTMTimer() {
        if (stmTimer != null && stmTimerFinished) {
            stmTimer.startTimer();
            stmTimerFinished = false;
        }
    }

    private void pauseTimer() {
        if (ustmTimer != null && !ustmTimer.isPaused() && !ustmTimerFinished) {
            ustmTimer.pauseTimer();
        }
        if (stmTimer != null && !stmTimer.isPaused() && !stmTimerFinished) {
            stmTimer.pauseTimer();
        }
        if (!stmTimerFinished) {
            disableButtons();
        }
    }

    private void restartTimer() {
        if (ustmTimer != null && ustmTimer.isPaused() && !ustmTimerFinished) {
            ustmTimer.resumeTimer();
        }
        if (stmTimer != null && stmTimer.isPaused() && !stmTimerFinished) {
            stmTimer.resumeTimer();
        }
        enableButtons();
    }

    private void stopUSTMTimer() {
        if (ustmTimer != null && !ustmTimerFinished) {
            ustmTimer.stopTimer();
            ustmTimerFinished = true;
            ustmTimerBar.setText(" ");
            ustmTimerBar.setProgress(ustmTotalTime);
        }
    }

    private void stopSTMTimer() {
        notificationManager.cancel(TIME_BAR_ID);
        if (stmTimer != null && !stmTimerFinished) {
            stmTimer.stopTimer();
            stmTimerFinished = true;
            stmTimerBar.setProgress(stmTotalTime * 60);
            stmTimerBar.setText(" ");
        }
    }

    private void updateLearningPhase() {
        LearningPhase learningPhase = modelManager.getLearningPhase();

        boolean zeroUnlearnedCards = false;
        boolean zeroUSTMCards = false;
        boolean zeroSTMCards = false;

        if (modelManager.getUnlearnedBatchSize() <= 0) {
            zeroUnlearnedCards = true;
        }

        if (modelManager.getUltraShortTermMemorySize() <= 0) {
            zeroUSTMCards = true;
        }

        if (modelManager.getShortTermMemorySize() <= 0) {
            zeroSTMCards = true;
        }

        switch (learningPhase) {

            case NOTHING: {
                break;
            }

            case SIMPLE_LEARNING: {

                if (completedLearning) {
                    finishLearning();
                } else {
                    setButtonVisibilityRepeating();
                }
                break;
            }

            case FILLING_USTM: {
                setButtonVisibilityFilling();

                if (stmTimerFinished) // STM timeout so go straight to repeating ustm cards
                {
                    setLearningPhase(LearningPhase.REPEATING_USTM);
                    updateLearningPhase();
                } else if (zeroUnlearnedCards && !ustmTimerFinished) {
                    setLearningPhase(LearningPhase.WAITING_FOR_USTM);
                    updateLearningPhase();
                } else if (ustmTimerFinished) {
                    setLearningPhase(LearningPhase.REPEATING_USTM);
                    updateLearningPhase();
                }

                break;
            }

            case WAITING_FOR_USTM: {
                Log.d("LearnCardsActivity::updateLearningPhase", "Waiting for USTM");
                // Gif zeigen
                showHideTimerAnimation();

                // USTM Timeout
                if (ustmTimerFinished || stopWaiting) {
                    stopWaiting = false;

                    stopUSTMTimer();
                    setLearningPhase(LearningPhase.REPEATING_USTM);
                    updateLearningPhase();
                }

                break;
            }

            case REPEATING_USTM: {
                setButtonsVisibility();

                if (zeroUSTMCards) // We have learned all the ustm cards
                {
                    if (stmTimerFinished) //STM timer has timed out so move to repeating STM
                    {
                        setLearningPhase(LearningPhase.REPEATING_STM);
                    } else if (!zeroUnlearnedCards) // Unlearned cards available so go back to filling ustm;
                    {
                        setLearningPhase(LearningPhase.FILLING_USTM);
                        startUSTMTimer();
                    } else {
                        setLearningPhase(LearningPhase.WAITING_FOR_STM);
                    }

                    updateLearningPhase();
                } else if (mCardPackAdapter.isLastCard()) {
                    setLearningPhase(REPEATING_USTM);
                }

                break;
            }

            case WAITING_FOR_STM: {
                // Gif zeigen
                showHideTimerAnimation();

                // USTM Timeout
                if (stmTimerFinished || stopWaiting) {
                    stopWaiting = false;
                    stopSTMTimer();
                    setLearningPhase(LearningPhase.REPEATING_STM);
                    invalidateOptionsMenu();
                    updateLearningPhase();
                }
                break;
            }

            case REPEATING_STM: {
                setButtonsVisibility();
                if (zeroSTMCards) {
                    finishLearning();
                } else if (mCardPackAdapter.isLastCard()) {
                    setLearningPhase(REPEATING_STM);
                }
                break;
            }

            case REPEATING_LTM: {
                if (modelManager.getExpiredCardsSize() <= 0) {
                    finishLearning();
                } else {
                    /*initStackSize -= 1;
                    Log.d("LearnCardsActivity::updateLearningPhase", "repeatingLTM: " +
                            "savedPos= " + mSavedCursorPosition + ",\n" +
                            "cursorPos= " + mCardCursor.getPosition() + ",\n" +
                            "currentStackSize= " + modelManager.getExpiredCardsSize() + ",\n" +
                            "initSize= " + initStackSize);
                    if (modelManager.getExpiredCardsSize() != initStackSize) {
                        Log.d("LearnCardsActivity::updateLearningPhase", "refresh cursor");
                        mSavedCursorPosition = -1;
                        refreshCursor();
                    }*/
                    setButtonsVisibility();
                }
                break;
            }
        }
    }

    private void setLearningPhase(LearningPhase newLearningsPhase) {
        //Neue Phase dem Modelmanager mitteilen und Deck aktualisieren
        modelManager.setLearningPhase(context, newLearningsPhase);
        //Cursor an erste Stelle setzen
        mSavedCursorPosition = -1;
        refreshCursor();
    }

    private void finishLearning() {
        if (settingsManager.getBoolPreference(context, AUTO_SAVE)) {
            startActivityForResult(new Intent(context, SaveDialog.class), Constants.REQUEST_CODE_SAVE_DIALOG_NORMAL);
        } else {
            paukerManager.setSaveRequired(true);
            finish();
        }
    }

    public void initButtons() {
        bNext = findViewById(R.id.bNext);
        bShowMe = findViewById(R.id.bShowMe);
        lRepeatButtons = findViewById(R.id.lBRepeat);
        lSkipWaiting = findViewById(R.id.lBSkipWaiting);

        setButtonsVisibility();
    }

    private void setButtonsVisibility() {
        LearningPhase learningPhase = modelManager.getLearningPhase();
        if (learningPhase == WAITING_FOR_USTM || learningPhase == WAITING_FOR_STM) {
            setButtonVisibilityWaiting();
        } else if (learningPhase == SIMPLE_LEARNING || learningPhase == REPEATING_USTM
                || learningPhase == REPEATING_STM || learningPhase == REPEATING_LTM) {
            setButtonVisibilityRepeating();
        } else if (learningPhase == FILLING_USTM) {
            setButtonVisibilityFilling();
        }
    }

    private void setButtonVisibilityWaiting() {
        bNext.setVisibility(GONE);
        bShowMe.setVisibility(GONE);
        lRepeatButtons.setVisibility(GONE);
        lSkipWaiting.setVisibility(VISIBLE);
    }

    private void setButtonVisibilityRepeating() {
        bNext.setVisibility(GONE);
        bShowMe.setVisibility(VISIBLE);
        String text = mCardCursor != null && modelManager.getCard(mCardCursor.getPosition()).isRepeatedByTyping() ?
                "Enter answer" : getString(R.string.show_me);
        bShowMe.setText(text);
        lRepeatButtons.setVisibility(GONE);
        lSkipWaiting.setVisibility(GONE);
    }

    private void setButtonVisibilityFilling() {
        bNext.setVisibility(VISIBLE);
        bShowMe.setVisibility(GONE);
        lRepeatButtons.setVisibility(GONE);
        lSkipWaiting.setVisibility(GONE);
    }

    private void disableButtons() {
        bNext.setEnabled(false);
        bShowMe.setEnabled(false);
        findViewById(R.id.bYes).setEnabled(false);
        findViewById(R.id.bNo).setEnabled(false);
        findViewById(R.id.bSkipWaiting).setEnabled(false);
    }

    private void enableButtons() {
        bNext.setEnabled(true);
        bShowMe.setEnabled(true);
        findViewById(R.id.bYes).setEnabled(true);
        findViewById(R.id.bNo).setEnabled(true);
        findViewById(R.id.bSkipWaiting).setEnabled(true);
    }

    private void showHideTimerAnimation() {
        if (timerAnimation == null) return;

        timerAnimation.setVisibility(stopWaiting ? GONE : VISIBLE);
        setButtonsVisibility();
    }

    @Override
    void updateCurrentCard() {
        try {
            if (isCardCursorAvailable()) {
                currentCard.setSideAText(mCardCursor.getString(CardPackAdapter.KEY_SIDEA_ID));
                currentCard.setSideBText(mCardCursor.getString(CardPackAdapter.KEY_SIDEB_ID));
                String learnStatus = mCardCursor.getString(CardPackAdapter.KEY_LEARN_STATUS_ID);

                if (learnStatus.contentEquals("1")) {
                    currentCard.setLearned(true);
                } else {
                    currentCard.setLearned(false);
                }
            } else {
                currentCard.setSideAText("");
                currentCard.setSideBText("");
                Log.d("FlashCardSwipeScreenActivity::updateCurrentCard", "Card Cursor not available");
            }

        } catch (Exception e) {
            Log.e("FlashCardSwipeScreenActivity::updateCurrentCard", "Caught Exception");
            showToast((Activity) context, R.string.load_card_data_error, Toast.LENGTH_SHORT);
            ErrorReporter.instance().AddCustomData("LearnCardsActivity::updateCurrentCard", "cursor problem?");
            finish();
        }
    }

    @Override
    public void screenTouched() {
        if ((ustmTimer != null && ustmTimer.isPaused()) || (stmTimer != null && stmTimer.isPaused()))
            return;
        LearningPhase learningPhase = modelManager.getLearningPhase();
        if (learningPhase == REPEATING_LTM
                || learningPhase == REPEATING_STM
                || learningPhase == REPEATING_USTM) {

            if (modelManager.getCard(mCardCursor.getPosition()).isRepeatedByTyping()) {
                showInputDialog();
            } else {
                if (flipCardSides) {
                    currentCard.setSide(SIDE_A);
                } else {
                    currentCard.setSide(FlashCard.SideShowing.SIDE_B);
                }

                fillInData(flipCardSides);
                bShowMe.setVisibility(GONE);
                lRepeatButtons.setVisibility(VISIBLE);
            }
        }
    }

    @SuppressLint("InflateParams")
    private void showInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View view = getLayoutInflater().inflate(R.layout.dialog_input, null);
        final MPEditText inputField = view.findViewById(R.id.eTInput);
        builder.setView(view)
                .setPositiveButton(R.string.proof, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String cardText;
                        if (flipCardSides) {
                            cardText = currentCard.getSideAText();
                        } else {
                            cardText = currentCard.getSideBText();
                        }
                        boolean caseSensitive = settingsManager.getBoolPreference(context, SettingsManager.Keys.CASE_SENSITIV);
                        String input = inputField.getText().toString();
                        if (caseSensitive && cardText.equals(input)) {
                            yesClicked(null);
                        } else if (cardText.equalsIgnoreCase(input)) {
                            yesClicked(null);
                        } else showResultDialog(cardText, input);
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null && getCurrentFocus() != null && imm.isAcceptingText()) {
                            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
                        }
                    }
                });
        if (flipCardSides) {
            inputField.setFont(modelManager.getCardFont(CardPackAdapter.KEY_SIDEA_ID, mCardCursor.getPosition()));
        } else {
            inputField.setFont(modelManager.getCardFont(CardPackAdapter.KEY_SIDEB_ID, mCardCursor.getPosition()));
        }
        builder.create().show();
    }

    @SuppressLint("InflateParams")
    private void showResultDialog(String cardText, String input) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.ResultDialogTheme);
        View view = getLayoutInflater().inflate(R.layout.dialog_result, null);
        builder.setView(view)
                .setPositiveButton("Weiterlegen", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        yesClicked(null);
                    }
                })
                .setNegativeButton("Zurücklegen", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        noClicked(null);
                    }
                })
                .setCancelable(false);
        ((TextView) view.findViewById(R.id.tVRightAnswerText)).setText(cardText);
        ((TextView) view.findViewById(R.id.tVInputText)).setText(input);
        builder.create().show();
    }

    @Override
    protected void fillData() {
        // Prüfen, ob getauscht werden soll
        boolean flipCardSides = hasCardsToBeFlipped();

        fillInData(flipCardSides);
    }

    private void fillInData(boolean flipCardSides) {
        // Daten setzen
        Font fontA = modelManager.getCardFont(CardPackAdapter.KEY_SIDEA_ID, mCardCursor.getPosition());
        Font fontB = modelManager.getCardFont(CardPackAdapter.KEY_SIDEB_ID, mCardCursor.getPosition());

        LearningPhase learningPhase = modelManager.getLearningPhase();
        // Layoutcontents setzen
        if (flipCardSides) {
            fillSideA(R.string.back, currentCard.getSideBText(), fontB);

            String sideAText = "";
            if (currentCard.getSide() == SIDE_A
                    || learningPhase == SIMPLE_LEARNING
                    || learningPhase == FILLING_USTM) {
                sideAText = currentCard.getSideAText();
            } else if (modelManager.getCard(mCardCursor.getPosition()).isRepeatedByTyping()) {
                sideAText = "Tap to enter your answer.";
            }
            fillSideB(R.string.front, sideAText, fontA);

        } else {
            // Card sides not flipped so show side A in top box!
            fillSideA(R.string.front, currentCard.getSideAText(), fontA);

            String sideBText = "";
            if (currentCard.getSide() == FlashCard.SideShowing.SIDE_B
                    || learningPhase == SIMPLE_LEARNING
                    || learningPhase == FILLING_USTM) {
                sideBText = currentCard.getSideBText();
            } else if (modelManager.getCard(mCardCursor.getPosition()).isRepeatedByTyping()) {
                sideBText = "Tap to enter your answer.";
            }
            fillSideB(R.string.back, sideBText, fontB);
        }

        fillHeader();
    }

    private boolean hasCardsToBeFlipped() {
        String flipMode = settingsManager.getStringPreference(context, SettingsManager.Keys.FLIP_CARD_SIDES);
        LearningPhase learningPhase = modelManager.getLearningPhase();
        if (learningPhase == REPEATING_LTM || learningPhase == REPEATING_STM || learningPhase == REPEATING_USTM) {
            switch (flipMode) {
                case "1":
                    flipCardSides = true;
                    break;
                case "2":
                    Random rand = new Random();
                    flipCardSides = rand.nextBoolean();
                    break;
                default:
                    flipCardSides = false;
                    break;
            }
        } else {
            flipCardSides = false;
        }

        if (flipCardSides) {
            currentCard.setSide(FlashCard.SideShowing.SIDE_B);
        } else {
            currentCard.setSide(SIDE_A);
        }

        return flipCardSides;
    }

    private void fillSideA(int titleResource, String text, Font font) {
        ((TextView) findViewById(R.id.titelCardSideA)).setText(getString(titleResource));
        MPTextView sideA = findViewById(R.id.tCardSideA);
        sideA.setFont(font);
        sideA.setText(text);
    }

    /**
     * Füllt die Kartenseite mit Text und setzt Titel und Font. Benutzt beim Wiederholen durch
     * Erinnern.
     * @param titleResource Ttiel der Karte
     * @param text          Anzuzeigender Text
     */
    private void fillSideB(int titleResource, String text, Font font) {
        ((TextView) findViewById(R.id.titelCardSideB)).setText(getString(titleResource));
        MPTextView sideB = findViewById(R.id.tCardSideB_TV);
        if (text.isEmpty() || modelManager.getCard(mCardCursor.getPosition()).isRepeatedByTyping()) {
            sideB.setHint(getString(R.string.learncards_show_hint));
            sideB.setFont(new Font());
        } else {
            sideB.setFont(font);
        }
        sideB.setText(text);
    }

    private void fillHeader() {
        TextView allCards = findViewById(R.id.tAllCards);
        TextView ustmCards = findViewById(R.id.tUKZGCards);
        TextView stmCards = findViewById(R.id.tKZGCards);

        String text;
        if (repeatingLTM) {
            text = getString(R.string.expired).concat(": %d");
            text = String.format(text, mCardCursor.getCount() - mCardCursor.getPosition());
            allCards.setText(text);
            ustmCards.setVisibility(GONE);
            stmCards.setVisibility(GONE);
        } else {
            text = getString(R.string.untrained).concat(": %d");
            text = String.format(text, modelManager.getUnlearnedBatchSize());
            allCards.setText(text);
            text = getString(R.string.ustm).concat(": %d");
            text = String.format(text, modelManager.getUltraShortTermMemorySize());
            ustmCards.setText(text);
            text = getString(R.string.stm).concat(": %d");
            text = String.format(text, modelManager.getShortTermMemorySize());
            stmCards.setText(text);
        }
    }

    @Override
    protected void cursorLoaded() {
        Log.d("LearnCardsActivity::cursorLoaded", "cursor loaded: " +
                "savedPos= " + mSavedCursorPosition);
        if (mSavedCursorPosition == -1) {
            initStackSize = mCardCursor.getCount();
            setCursorToFirst();
            updateCurrentCard();
            fillData();
        } else {
            mCardCursor.moveToPosition(mSavedCursorPosition);
            updateCurrentCard();
            fillInData(flipCardSides);
            setButtonsVisibility();
            if (bShowMe.getVisibility() == VISIBLE
                    && ((flipCardSides && currentCard.getSide() == SIDE_A)
                    || (!flipCardSides && currentCard.getSide() == SIDE_B))) {
                bShowMe.setVisibility(GONE);
                lRepeatButtons.setVisibility(VISIBLE);
            }
        }
        mSavedCursorPosition = -1;
    }

    /**
     * Create the NotificationChannel
     */
    private void createNotificationChannel() {
        CharSequence name = getString(R.string.channel_notify_name);
        String description = getString(R.string.channel_notify_description);
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(Constants.TIMER_NOTIFY_CHANNEL_ID, name, importance);
        channel.setDescription(description);
        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * Create the Channel for Times in the Notificationbar
     */
    private void createTimerBarChannel() {
        CharSequence name = getString(R.string.channel_timerbar_name);
        String description = getString(R.string.channel_timerbar_description);
        int importance = NotificationManager.IMPORTANCE_LOW;
        NotificationChannel channel = new NotificationChannel(Constants.TIMER_BAR_CHANNEL_ID, name, importance);
        channel.setDescription(description);
        channel.setSound(null, null);
        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }

    public void mEditClicked(MenuItem item) {
        Intent intent = new Intent(context, EditCardActivity.class);
        intent.putExtra(Constants.CURSOR_POSITION, mCardCursor.getPosition());
        startActivityForResult(intent, Constants.REQUEST_CODE_EDIT_CARD);
    }

    public void mDeleteClicked(MenuItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(R.string.delete_card_message)
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                })
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Muss vorher gespeichert werden, da sonst im Nachhinein der Wert
                        // verfälscht werden kann!
                        boolean isLast = mCardCursor.isLast();

                        if (modelManager.deleteCard(mCardCursor.getPosition())) {
                            if (!isLast) {
                                updateCurrentCard();
                                fillData();
                            } else {
                                // Letzte Karte oder Timer abgelaufen. Darum Lernphase aktualisieren
                                updateLearningPhase();
                            }
                        } else {
                            showToast((Activity) context, "Löschen nicht möglich!", Toast.LENGTH_SHORT);
                        }
                        dialog.cancel();
                    }
                });
        builder.create().show();
    }

    public void mPauseTimerClicked(MenuItem item) {
        if (restartButton != null && !stmTimerFinished) {
            pauseTimer();
            item.setVisible(false);
            restartButton.setVisible(true);
        } else {
            showToast((Activity) context, R.string.pause_timer_error, Toast.LENGTH_LONG);
        }
    }

    public void mRestartTimerClicked(MenuItem item) {
        if (pauseButton != null) {
            restartTimer();
            item.setVisible(false);
            pauseButton.setVisible(true);
        } else {
            showToast((Activity) context, R.string.restart_timer_error, Toast.LENGTH_LONG);
        }
    }

    // Aktionen der Buttons
    public void nextCard(View view) {
        // Karte ein Deck weiterschieben
        mCardPackAdapter.setCardLearned(mCardCursor.getLong(CardPackAdapter.KEY_ROWID_ID));

        if (!mCardCursor.isLast() && !ustmTimerFinished) {
            mCardCursor.moveToNext();
            updateCurrentCard();
            fillData();
        } else {
            // Letzte Karte oder Timer abgelaufen. Darum Lernphase aktualisieren
            updateLearningPhase();
        }
    }

    public void skipWaiting(View view) {
        stopWaiting = true;
        updateLearningPhase();
    }

    public void noClicked(View view) {
        mCardPackAdapter.setCardUnLearned(context, mCardCursor.getLong(CardPackAdapter.KEY_ROWID_ID));
        paukerManager.setSaveRequired(true);

        if (!mCardCursor.isLast()) {
            mCardCursor.moveToNext();
            updateCurrentCard();
            fillData();
        } else {
            completedLearning = true;
        }

        updateLearningPhase();
    }

    public void yesClicked(View view) {
        mCardPackAdapter.setCardLearned(mCardCursor.getLong(CardPackAdapter.KEY_ROWID_ID));
        paukerManager.setSaveRequired(true);

        if (!mCardCursor.isLast()) {
            mCardCursor.moveToNext();
            updateCurrentCard();
            fillData();
        } else {
            completedLearning = true;
        }

        updateLearningPhase();
    }

    public void showCard(View view) {
        screenTouched();
    }
}
