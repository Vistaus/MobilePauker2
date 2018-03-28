package com.daniel.mobilepauker2.model.pauker_native;

import android.support.annotation.NonNull;

import java.util.LinkedList;
import java.util.List;


/**
 * A card is part of a batch. Besides having a front side and a reverse side
 * it can contain information about the date the card was learned and if
 * the card should be repeated by typing or not.
 * @author Ronny.Standtke@gmx.net
 */
public class Card implements Comparable<Card> {

    public class UID {
    }


    /**
     * the elements of a card
     */
    public enum Element {

        /**
         * the front side
         */
        FRONT_SIDE,
        /**
         * the reverse side
         */
        REVERSE_SIDE,
        /**
         * both sides
         */
        BOTH_SIDES,
        /**
         * the batchnumber
         */
        BATCH_NUMBER,
        /**
         * the date when the card was learned
         */
        LEARNED_DATE,
        /**
         * the date when the card expires
         */
        EXPIRED_DATE,
        /**
         * the way a card has to be repeated
         */
        REPEATING_MODE
    }

    protected CardSide frontSide;
    protected CardSide reverseSide;
    // for indexing; unique id will be used to store card in the object-store
    private String id;
    private long expirationTime;

    /**
     * creates a new card
     * @param frontSide   the front side of the card
     * @param reverseSide the reverse side of the card
     */
    public Card(CardSide frontSide, CardSide reverseSide) {
        this.frontSide = frontSide;
        this.reverseSide = reverseSide;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Card other = (Card) obj;
        return !(frontSide != other.frontSide
                && (frontSide == null || !frontSide.equals(other.frontSide)))
                && !(reverseSide != other.reverseSide && (reverseSide == null
                || !reverseSide.equals(other.reverseSide)))
                && expirationTime == other.expirationTime;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 47 * hash + (frontSide != null ? frontSide.hashCode() : 0);
        hash = 47 * hash + (reverseSide != null ? reverseSide.hashCode() : 0);
        hash = 47 * hash + (int) (expirationTime ^ (expirationTime >>> 32));
        return hash;
    }

    public int compareTo(@NonNull Card otherCard) {
        int frontSideResult = frontSide.compareTo(otherCard.getFrontSide());
        if (frontSideResult != 0) {
            return frontSideResult;
        }
        int reverseSideResult =
                reverseSide.compareTo(otherCard.getReverseSide());
        if (reverseSideResult != 0) {
            return reverseSideResult;
        }
        // both sides are equal, base decision on expiration time
        long otherExpirationTime = otherCard.getExpirationTime();
        if (expirationTime < otherExpirationTime) {
            return -1;
        } else if (expirationTime > otherExpirationTime) {
            return 1;
        }
        // cards are equal
        return 0;
    }

    /**
     * sets the front side of the card
     * @param frontSide the front side of the card
     */
    public void setFrontSide(CardSide frontSide) {
        this.frontSide = frontSide;
    }

    /**
     * returns the front side of the card
     * @return the front side of the card
     */
    public CardSide getFrontSide() {
        return frontSide;
    }

    /**
     * sets the reverse side of the card
     * @param reverseSide the reverse side of the card
     */
    public void setReverseSide(CardSide reverseSide) {
        this.reverseSide = reverseSide;
    }

    /**
     * returns the reverse side of the card
     * @return the reverse side of the card
     */
    public CardSide getReverseSide() {
        return reverseSide;
    }

    /**
     * returns the timestamp when the card was learned
     * @return the timestamp when the card was learned
     */
    public long getLearnedTimestamp() {
        return frontSide.getLearnedTimestamp();
    }

    /**
     * returns the unique object identifier<br>
     * used for indexing!
     * @return the unique object id
     */
    public String getId() {
        if (id == null) {
            this.id = new UID().toString();
        }
        return id;
    }

    /**
     * sets if the card is learned or not
     * @param learned if true the cards state is set to learned and the current date is used as
     *                </CODE>learnedDate</CODE>
     */
    public void setLearned(boolean learned) {
        frontSide.setLearned(learned);
        if (learned) {
            frontSide.setLearnedTimestamp(System.currentTimeMillis());
        }
    }

    /**
     * updates the "learned" timestamp of this card (e.g. when moving this card
     * from one long term batch to the next one)
     */
    public void updateLearnedTimeStamp() {
        frontSide.setLearnedTimestamp(System.currentTimeMillis());
    }

    /**
     * determines if the card is learned or not
     * @return if true the card is learned
     */
    public boolean isLearned() {
        return frontSide.isLearned();
    }

    /**
     * sets the number of the batch this card is part of
     * @param batchNumber the number of the batch this card is part of
     */
    public void setLongTermBatchNumber(int batchNumber) {
        frontSide.setLongTermBatchNumber(batchNumber);
    }

    /**
     * determines the number of the batch this card is part of
     * @return the number of the batch this card is part of
     */
    public int getLongTermBatchNumber() {
        return frontSide.getLongTermBatchNumber();
    }

    /**
     * sets the expiration time of this card
     * @param expirationTime the expiration time of this card
     */
    public void setExpirationTime(long expirationTime) {
        this.expirationTime = expirationTime;
    }

    /**
     * returns the expiration time of this card
     * @return the expiration time of this card
     */
    public long getExpirationTime() {
        if (frontSide.isLearned()) {
            return frontSide.getLearnedTimestamp() + expirationTime;
        }
        return -1;
    }

    /**
     * expires this card
     */
    public void expire() {
        long expiredTime = System.currentTimeMillis() - expirationTime - 60000;
        frontSide.setLearned(true);
        frontSide.setLearnedTimestamp(expiredTime);
    }

    /**
     * determines if this card should be repeated by typing or not
     * @return if true this card should be repeated by typing
     */
    public boolean isRepeatedByTyping() {
        return frontSide.isRepeatedByTyping();
    }

    /**
     * sets if this card should be repeated by typing or not
     * @param repeatByTyping if true this card should be repeated by typing
     */
    public void setRepeatByTyping(boolean repeatByTyping) {
        frontSide.setRepeatByTyping(repeatByTyping);
    }

    /**
     * Sets a search pattern onto this card.
     * @param pattern   the search pattern
     * @param matchCase if the case of the pattern must be matched
     * @param cardSide  the card side where to look for the pattern can be one of: Pauker.FRONT_SIDE
     *                  Pauker.REVERSE_SIDE Pauker.BOTH_SIDES
     * @return a list with search match indices
     */
    public List<SearchHit> search(
            Element cardSide, String pattern, boolean matchCase) {
        LinkedList<SearchHit> searchHits = new LinkedList<>();
        if (cardSide == Element.FRONT_SIDE) {
            searchHits.addAll(frontSide.search(
                    this, Element.FRONT_SIDE, pattern, matchCase));
            reverseSide.cancelSearch();
        } else if (cardSide == Element.REVERSE_SIDE) {
            frontSide.cancelSearch();
            searchHits.addAll(reverseSide.search(
                    this, Element.REVERSE_SIDE, pattern, matchCase));
        } else { // BOTH_SIDES
            searchHits.addAll(frontSide.search(
                    this, Element.FRONT_SIDE, pattern, matchCase));
            searchHits.addAll(reverseSide.search(
                    this, Element.REVERSE_SIDE, pattern, matchCase));
        }
        return searchHits;
    }

    /**
     * returns a list of search hits at this card side
     * @return a list of search hits at this card side
     */
    public List<SearchHit> getSearchHits() {
        List<SearchHit> searchHits = new LinkedList<>();
        searchHits.addAll(frontSide.getSearchHits());
        searchHits.addAll(reverseSide.getSearchHits());
        return searchHits;
    }

    /**
     * clears the search hits list on both card sides
     */
    public void stopSearching() {
        frontSide.cancelSearch();
        reverseSide.cancelSearch();
    }

    /**
     * flips the card sides
     */
    public void flip() {
        CardSide tmpCardSide = frontSide;
        frontSide = reverseSide;
        reverseSide = tmpCardSide;
    }

    //    /**
    //     * returns the average font size of this card
    //     * (averages front and reverse side)
    //     * @return the average font size of this card
    //     * (averages front and reverse side)
    //     */
    //    public float getAverageFontSize() {
    //        return ((float) (frontSide.getFontSize() +
    //                reverseSide.getFontSize()) / 2);
    //    }
}
