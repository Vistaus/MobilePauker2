/* 
 * Copyright 2011 Brian Ford
 * 
 * This file is part of Pocket Pauker.
 * 
 * Pocket Pauker is free software: you can redistribute it and/or modify it under the 
 * terms of the GNU General Public License as published by the Free Software Foundation, 
 * either version 3 of the License, or (at your option) any later version.
 * 
 * Pocket Pauker is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY 
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more 
 * details.
 * 
 * See http://www.gnu.org/licenses/.

*/

package com.daniel.mobilepauker2.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;

import com.daniel.mobilepauker2.PaukerManager;
import com.daniel.mobilepauker2.model.pauker_native.Card;
import com.daniel.mobilepauker2.model.pauker_native.Lesson;
import com.daniel.mobilepauker2.model.pauker_native.LongTermBatch;
import com.daniel.mobilepauker2.model.xmlsupport.FlashCardXMLStreamWriter;
import com.daniel.mobilepauker2.statistics.BatchStatistics;
import com.daniel.mobilepauker2.utils.Constants;
import com.daniel.mobilepauker2.utils.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.zip.GZIPOutputStream;

import static android.content.Context.MODE_APPEND;
import static android.content.Context.MODE_PRIVATE;
import static com.daniel.mobilepauker2.model.SettingsManager.Keys.LEARN_NEW_CARDS_RANDOMLY;
import static com.daniel.mobilepauker2.model.SettingsManager.Keys.RETURN_FORGOTTEN_CARDS;

/**
 * Manages access to a lesson
 * <p>
 * <p>
 * Provides a single facade (API) to the pauker model
 * <p>
 * Controls:
 * * setting up a lesson (static)
 * * moving through learning phases
 * * moving cards between lesson batches
 */

public class PaukerModelManager {
    private static PaukerModelManager instance;
    private static final PaukerManager paukerManager = PaukerManager.instance();

    private final List<FlashCard> mCurrentPack = new ArrayList<>();
    private final SettingsManager settingsManager = SettingsManager.instance();
    private Lesson mLesson = null;
    private FlashCard mCurrentCard = new FlashCard();
    private LearningPhase mLearningPhase = LearningPhase.NOTHING;

    private PaukerModelManager() {

    }

    public static PaukerModelManager instance() {
        if (instance == null) {
            instance = new PaukerModelManager();
        }
        return instance;
    }

    /**
     * the learning phases
     */
    public enum LearningPhase {

        /**
         * not learning
         */
        NOTHING,
        /**
         * Just browsing the new cards - not learning
         */
        BROWSE_NEW,

        /**
         * Not using USTM or STM memory and timers
         */
        SIMPLE_LEARNING,

        /**
         * the phase of filling the ultra short term memory
         */
        FILLING_USTM,
        /**
         * the phase of waiting for the ultra short term memory
         */
        WAITING_FOR_USTM,
        /**
         * the phase of repeating the ultra short term memory
         */
        REPEATING_USTM,
        /**
         * the phase of waiting for the short term memory
         */
        WAITING_FOR_STM,
        /**
         * the phase of repeating the short term memory
         */
        REPEATING_STM,
        /**
         * the phase of repeating the long term memory
         */
        REPEATING_LTM
    }

    private void setupCurrentPack(Context context) {

        Iterator<Card> cardIterator = null;

        if (mLesson != null) {
            switch (mLearningPhase) {

                case NOTHING: {
                    mCurrentPack.clear();
                    cardIterator = mLesson.getCards().iterator();
                    break;
                }

                case BROWSE_NEW: {
                    mCurrentPack.clear();
                    cardIterator = mLesson.getUnlearnedBatch().getCards().iterator();
                    break;
                }

                case SIMPLE_LEARNING: {
                    mCurrentPack.clear();
                    cardIterator = mLesson.getUnlearnedBatch().getCards().iterator();
                    break;
                }

                case FILLING_USTM: {
                    Log.d("AndyPaukerApplication::setupCurrentPack", "Setting batch to UnlearnedBatch");
                    mCurrentPack.clear();
                    cardIterator = mLesson.getUnlearnedBatch().getCards().iterator();
                    break;
                }

                case WAITING_FOR_USTM: {
                    Log.d("AndyPaukerApplication::setupCurrentPack", "Waiting for ustm");
                    return;
                }

                case REPEATING_USTM: {
                    Log.d("AndyPaukerApplication::setupCurrentPack", "Setting pack as ultra short term memory");
                    mCurrentPack.clear();
                    cardIterator = mLesson.getUltraShortTermList().listIterator();
                    break;
                }

                case WAITING_FOR_STM: {
                    cardIterator = mLesson.getShortTermList().listIterator(); // Need to put something in the iterator for requery
                    return;
                }

                case REPEATING_STM: {
                    Log.d("AndyPaukerApplication::setupCurrentPack", "Setting pack as short term memory");
                    mCurrentPack.clear();
                    cardIterator = mLesson.getShortTermList().listIterator();
                    break;
                }

                case REPEATING_LTM: {
                    Log.d("AndyPaukerApplication::setupCurrentPack", "Setting pack as expired cards");
                    mLesson.refreshExpiration();
                    mCurrentPack.clear();
                    cardIterator = mLesson.getExpiredCards().iterator();
                    break;
                }
            }

            // Fill the current pack
            fillCurrentPack(context, cardIterator);
        }
    }

    public void setCurrentPack(Context context, int stackIndex) {
        Iterator<Card> cardIterator;
        if (getLessonSize() > 0) {
            mCurrentPack.clear();
            switch (stackIndex) {
                case 0:
                    cardIterator = mLesson.getCards().iterator();
                    break;
                case 1:
                    cardIterator = mLesson.getUnlearnedBatch().getCards().iterator();
                    break;
                default:
                    cardIterator = mLesson.getLongTermBatch(stackIndex - 2).getCards().iterator();
            }

            // Fill the current pack
            fillCurrentPack(context, cardIterator);
        }
    }

    private void fillCurrentPack(Context context, Iterator<Card> cardIterator) {
        while (cardIterator.hasNext()) {
            mCurrentPack.add((FlashCard) cardIterator.next());
        }

        if (isShuffle(context)) {
            shuffleCurrentPack();
        }
    }

    public void addCard(String sideA, String sideB, String rowID, String index, String learnStatus) {
        FlashCard newCard = new FlashCard(sideA, sideB, index, rowID, learnStatus);
        mLesson.getUnlearnedBatch().addCard(newCard);
    }

    public List<BatchStatistics> getBatchStatistics() {
        ArrayList<BatchStatistics> batchSizes = new ArrayList<>();

        List<LongTermBatch> longTermBatches = mLesson.getLongTermBatches();
        for (int i = 0, size = longTermBatches.size(); i < size; i++) {
            LongTermBatch longTermBatch = longTermBatches.get(i);
            int numberOfCards = longTermBatch.getNumberOfCards();
            int expiredCards = longTermBatch.getNumberOfExpiredCards();

            if (numberOfCards == 0) {
                batchSizes.add(new BatchStatistics(0, 0));
            } else {
                batchSizes.add(new BatchStatistics(numberOfCards, expiredCards));
            }
        }

        return batchSizes;
    }

    public void editCard(int position, String sideAText, String sideBText) {
        if (position < 0 || position >= mCurrentPack.size()) {
            Log.e("AndyPaukerApplication::learnedCard", "request to update a card with position outside the pack");
            return;
        }

        mCurrentPack.get(position).setSideAText(sideAText);
        mCurrentPack.get(position).setSideBText(sideBText);
    }

    /**
     * While repeating cards the user acknowledged that the card was remembered
     * correctly, we have to move the current card one batch further
     * @param position .
     */
    public void setCardLearned(int position) {
        if (position < 0 || position >= mCurrentPack.size()) {
            Log.e("AndyPaukerApplication::learnedCard", "request to update a card with position outside the pack");
            return;
        }

        mCurrentCard = mCurrentPack.get(position);

        pushCurrentCard();
    }

    /**
     * Removes the card from its current batch and moves it to the
     * un-learned batch. Use this to forget cards.
     * @param position .
     * @param context .
     */
    void pullCurrentCard(int position, Context context) {
        if (position < 0 || position >= mCurrentPack.size()) {
            Log.e("AndyPaukerApplication::setCardUnLearned", "request to update a card with position outside the pack");
            return;
        }

        mCurrentCard = mCurrentPack.get(position);

        switch (mLearningPhase) {

            case SIMPLE_LEARNING: {
                // Note that we do not have to handle this as all cards here are unlearned
                break;
            }

            case REPEATING_USTM:
                mCurrentCard.setLearned(false);

                if (mLesson.getUltraShortTermList().remove(mCurrentCard)) {
                    Log.d("AndyPaukerApplication::setCurretnCardUnlearned", "Moved card from USTM to unlearned batch");
                } else {
                    Log.e("AndyPaukerApplication::setCurretnCardUnlearned", "Unable to delete card from USTM");
                }

                switch (settingsManager.getStringPreference(context, RETURN_FORGOTTEN_CARDS)) {
                    case "1":
                        mLesson.getUnlearnedBatch().addCard(mCurrentCard);
                        break;
                    case "2":
                        int numberOfCards = mLesson.getUnlearnedBatch().getNumberOfCards();
                        if (numberOfCards > 0) {
                            Random random = new Random();
                            int index = random.nextInt(numberOfCards);
                            mLesson.getUnlearnedBatch().addCard(index, mCurrentCard);
                        } else {
                            mLesson.getUnlearnedBatch().addCard(0, mCurrentCard);
                        }
                        break;
                    default:
                        mLesson.getUnlearnedBatch().addCard(0, mCurrentCard);
                        break;
                }

                break;

            case REPEATING_STM:
                mCurrentCard.setLearned(false);
                mLesson.getShortTermList().remove(mCurrentCard);

                switch (settingsManager.getStringPreference(context, RETURN_FORGOTTEN_CARDS)) {
                    case "1":
                        mLesson.getUnlearnedBatch().addCard(mCurrentCard);
                        break;
                    case "2":
                        int numberOfCards = mLesson.getUnlearnedBatch().getNumberOfCards();
                        if (numberOfCards > 0) {
                            Random random = new Random();
                            int index = random.nextInt(numberOfCards);
                            mLesson.getUnlearnedBatch().addCard(index, mCurrentCard);
                        } else {
                            mLesson.getUnlearnedBatch().addCard(0, mCurrentCard);
                        }
                        break;
                    default:
                        mLesson.getUnlearnedBatch().addCard(0, mCurrentCard);
                        break;
                }
                break;

            case REPEATING_LTM:
                mCurrentCard.setLearned(false);
                // remove card from current long term memory batch
                int longTermBatchNumber = mCurrentCard.getLongTermBatchNumber();
                LongTermBatch longTermBatch = mLesson.getLongTermBatch(longTermBatchNumber);
                longTermBatch.removeCard(mCurrentCard);

                switch (settingsManager.getStringPreference(context, RETURN_FORGOTTEN_CARDS)) {
                    case "1":
                        mLesson.getUnlearnedBatch().addCard(mCurrentCard);
                        break;
                    case "2":
                        int numberOfCards = mLesson.getUnlearnedBatch().getNumberOfCards();
                        if (numberOfCards > 0) {
                            Random random = new Random();
                            int index = random.nextInt(numberOfCards);
                            mLesson.getUnlearnedBatch().addCard(index, mCurrentCard);
                        } else {
                            mLesson.getUnlearnedBatch().addCard(0, mCurrentCard);
                        }
                        break;
                    default:
                        mLesson.getUnlearnedBatch().addCard(0, mCurrentCard);
                        break;
                }
                break;

            default:
                Log.e("AndyPaukerApplication::setCurretnCardUnlearned", "Learning phase not supported");
                break;
        }
    }

    /**
     * While repeating cards the user acknowledged that the card was remembered
     * correctly, we have to move the current card one batch further
     */
    private void pushCurrentCard() {
        //Log.d("AndyPaukerApplication::pushCurrentCard","Entry : Learning phase = ");
        LongTermBatch longTermBatch;

        switch (mLearningPhase) {

            case SIMPLE_LEARNING:
                mLesson.getUnlearnedBatch().removeCard(mCurrentCard);

                if (mLesson.getNumberOfLongTermBatches() == 0) {
                    mLesson.addLongTermBatch();
                }

                longTermBatch = mLesson.getLongTermBatch(0);
                longTermBatch.addCard(mCurrentCard);
                mCurrentCard.setLearned(true);
                break;

            case FILLING_USTM:
                mLesson.getUltraShortTermList().add(mCurrentCard);
                mLesson.getUnlearnedBatch().removeCard(mCurrentCard);
                break;

            case REPEATING_USTM:
                mLesson.getUltraShortTermList().remove(mCurrentCard);
                mLesson.getShortTermList().add(mCurrentCard);
                break;

            case REPEATING_STM:
                mLesson.getShortTermList().remove(mCurrentCard);
                if (mLesson.getNumberOfLongTermBatches() == 0) {
                    mLesson.addLongTermBatch();
                }
                longTermBatch = mLesson.getLongTermBatch(0);
                longTermBatch.addCard(mCurrentCard);
                mCurrentCard.setLearned(true);
                break;

            case REPEATING_LTM:
                // remove card from current long term memory batch
                int longTermBatchNumber = mCurrentCard.getLongTermBatchNumber();
                int nextLongTermBatchNumber = longTermBatchNumber + 1;
                longTermBatch =
                        mLesson.getLongTermBatch(longTermBatchNumber);
                longTermBatch.removeCard(mCurrentCard);
                // add card to next long term batch
                if (mLesson.getNumberOfLongTermBatches()
                        == nextLongTermBatchNumber) {
                    mLesson.addLongTermBatch();
                }
                LongTermBatch nextLongTermBatch =
                        mLesson.getLongTermBatch(nextLongTermBatchNumber);
                nextLongTermBatch.addCard(mCurrentCard);
                mCurrentCard.updateLearnedTimeStamp();
                break;

            default:
                throw new RuntimeException("unsupported learning phase \""
                        + getLearningPhase()
                        + "\" -> can't find batch of card!");
        }
    }

    //TODO This sould de in a class of its own or in the Flash cardd xml stream writer...
    public void saveLesson() {
        if (!isLessonNew()) {
            String filePath = Environment.getExternalStorageDirectory()
                    + paukerManager.getApplicationDataDirectory()
                    + paukerManager.getCurrentFileName();
            File newxmlfile = new File(filePath);

            Log.d("PaukerModelManager::saveLesson", "Filename = " + paukerManager.getCurrentFileName());
            Log.d("PaukerModelManager::saveLesson", "Directory= " + paukerManager.getFileAbsolutePath());
            Log.d("PaukerModelManager::saveLesson", "Directory= " + newxmlfile.getAbsolutePath());

            GZIPOutputStream gzipOutputStream;
            try {
                if (!newxmlfile.exists() && !newxmlfile.createNewFile()) {
                    return;
                }
                FileOutputStream fos = new FileOutputStream(newxmlfile);
                gzipOutputStream = new GZIPOutputStream(fos);
                FlashCardXMLStreamWriter.writeXML(mLesson, gzipOutputStream);
                gzipOutputStream.close();
            } catch (FileNotFoundException e) {
                Log.e("PaukerModelManager::saveLesson", "can't create FileOutputStream");
                throw new RuntimeException(e);
            } catch (IOException e) {
                Log.e("PaukerModelManager::saveLesson", "exception in saveLesson() method");
                throw new RuntimeException(e);
            }
        }
    }

    public boolean deleteLesson(Context context, File file) {
        try {
            String filename = file.getName();
            if (file.delete()) {
                FileOutputStream fos = context.openFileOutput(Constants.DELETED_FILES_NAMES_FILE_NAME, MODE_APPEND);
                String text = "\n" + filename + ";*;" + System.currentTimeMillis();
                fos.write(text.getBytes());
                fos.close();
                return true;
            } else return false;
        } catch (IOException e) {
            return false;
        }
    }


    /**
     * Move all cards in USTM and STM back to unlearned batch
     */
    public void resetLesson() {
        List<Card> ustmList = mLesson.getUltraShortTermList();
        List<Card> stmList = mLesson.getShortTermList();

        for (int i = 0; i < ustmList.size(); i++) {
            mLesson.getUnlearnedBatch().addCard(ustmList.get(i));
        }

        for (int i = 0; i < stmList.size(); i++) {
            mLesson.getUnlearnedBatch().addCard(stmList.get(i));
        }

        mLesson.getUltraShortTermList().clear();
        mLesson.getShortTermList().clear();
    }

    public void ForgetAllCards() {
        mLesson.reset();
    }

    public boolean isLessonNew() {
        return paukerManager.getCurrentFileName().equals(Constants.DEFAULT_FILE_NAME);
    }

    public boolean isLessonSetup() {
        return (mLesson != null);
    }


    /**
     * Delete from memory current lesson - To be used when trying to recover from errors!
     */
    public void clearLesson() {
        mLesson = null;
    }

    public void createNewLesson(String lessonName) {
        Log.d("AndyPaukerApplication::setupNewLesson", "Entry");
        Lesson newLesson = new Lesson();
        setLesson(newLesson);
    }

    public boolean deleteCard(int position) {

        mCurrentCard = mCurrentPack.get(position);

        Log.d("AndyPaukerApplication::deleteCard", "entry");

        if (mCurrentCard.isLearned()) {
            int batchNumber = mCurrentCard.getLongTermBatchNumber();
            LongTermBatch longTermBatch = mLesson.getLongTermBatch(batchNumber);
            if (longTermBatch.removeCard(mCurrentCard)) {
                Log.d("AndyPaukerApplication::deleteCard", "Deleted from long term batch" + batchNumber);
            } else {
                Log.e("AndyPaukerApplication::deleteCard", "Card not in long term batch" + batchNumber);
            }
        } else {
            if (mLesson.getUnlearnedBatch().removeCard(mCurrentCard)) {
                Log.d("AndyPaukerApplication::deleteCard", "Deleted from unlearned batch");
            } else {
                Log.e("AndyPaukerApplication::deleteCard", "Could not delete card from unlearned batch  ");
                return false;
            }
        }

        mCurrentPack.remove(position);

        return true;

    }

    public void setLearningPhase(Context context, LearningPhase learningPhase) {
        mLearningPhase = learningPhase;
        setupCurrentPack(context);
    }

    public void setLesson(Lesson lesson) {
        mLesson = lesson;
    }

    public void setDescription(String s) {
        mLesson.setDescription(s);
    }

    public LearningPhase getLearningPhase() {
        return mLearningPhase;
    }

    public String getDescription() {
        return mLesson.getDescription();
    }

    public FlashCard getCard(int position) {
        if (position < 0 || position >= mCurrentPack.size()) {
            return null;
        } else {
            return mCurrentPack.get(position);
        }
    }

    public int getCurrentBatchSize() {
        return mCurrentPack.size();
    }

    public int getLessonSize() {
        return mLesson.getCards().size();
    }

    //TODO Have an enum BATCH_TYPE and pass that into a single getBatchSize(BatchType)

    public int getExpiredCardsSize() {
        return mLesson.getNumberOfExpiredCards();
    }

    public int getUnlearnedBatchSize() {
        return mLesson.getUnlearnedBatch().getCards().size();
    }

    public int getUltraShortTermMemorySize() {
        return mLesson.getUltraShortTermList().size();
    }

    public int getShortTermMemorySize() {
        return mLesson.getShortTermList().size();
    }

    public List<FlashCard> getCurrentPack() {
        return mCurrentPack;
    }

    /*
     * Shuffle the card pack
     */
    public void shuffleCurrentPack() {
        Collections.shuffle(mCurrentPack);
    }

    /*
     * Return true if the pack needs shuffled
     */
    private boolean isShuffle(Context context) {
        // Check if we need to add random learn
        if (mLearningPhase == LearningPhase.SIMPLE_LEARNING ||
                mLearningPhase == LearningPhase.REPEATING_STM ||
                mLearningPhase == LearningPhase.REPEATING_USTM ||
                mLearningPhase == LearningPhase.FILLING_USTM) {

            if (settingsManager.getBoolPreference(context, LEARN_NEW_CARDS_RANDOMLY)) {
                return true;
            }
        }

        return mLearningPhase == LearningPhase.REPEATING_LTM
                && settingsManager.getBoolPreference(context, LEARN_NEW_CARDS_RANDOMLY);
    }

    public void resetDeletedFilesData(Context context) {
        try {
            FileOutputStream fos = context.openFileOutput(Constants.DELETED_FILES_NAMES_FILE_NAME, MODE_PRIVATE);
            String text = "\n";
            fos.write(text.getBytes());
            fos.close();
        } catch (IOException e) {
            // TODO ERROR
        }
    }

}