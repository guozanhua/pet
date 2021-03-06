/**
 * Pet - a comic pet simulator game
 * Copyright (C) 2013-2015 Ricardo Fabbri and Edson "Presto" Correa
 *
 * This program is free software. You can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version. A different license may be requested
 * to the copyright holders.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses/>
 *
 * NOTE: this file may contain code derived from the PlayN, Tripleplay, and
 * React libraries, all (C) The PlayN Authors or Three Rings Design, Inc.  The
 * original PlayN is released under the Apache, Version 2.0 License, and
 * TriplePlay and React have their licenses listed in COPYING-OOO. PlayN and
 * Three Rings are NOT responsible for the changes found herein, but are
 * thankfully acknowledged.
 */
package com.pulapirata.core;

import java.util.ArrayList;
import react.Slot;
import react.IntValue;
import com.pulapirata.core.PetAttributeEnum;
import com.pulapirata.core.PetAttributes;
import static com.pulapirata.core.utils.Puts.*;


/**
 * A class that just partitions values into qualitative states.
 * For instance: Alcool: drunk, sober, hangover etc.
 *
 * Inside a rule that depends on a PetAttributeState, there will be a
 * slot() function. PetAttributeState will emit a signal only when
 * its qualitative state changes.
 *
 */
public class PetAttributeState<State extends Enum<State>>  extends PetAttributeEnum<State> {

    /**
     * Construct for a given {@link PetAttribute} att.
     *
     * @param att       the underlying numeric PetAttribute
     * @param states    an array of PetAttributeState.State for that att
     * @param intervals an array boundaries partitioning att's range. For
     * instance, if states == (A,B), then the interval will be:
     *      A is from att.min() inclusive to intervals[0] inclusive
     *      B is from intervals[0]+1 inclusive to intervals[1] inclusive
     */
    public void set(PetAttribute att) {
        att_ = att;
    }

    /**
     * Listen to the associated attribute for changes.
     */
    public void listen() {
        att_.connect(slot());
        att_.updateForce(att_.get());   // notify all watchers
    }

    public void set(ArrayList<State> states, ArrayList<Integer> intervals) {
        // make sure supplied intervals partitions the range of that
        // parameter:
        assert intervals.size() == states.size() : "Intervals and stateslist must be same-sized.";

        assert intervals.get(0) >= att_.min() : "Interval 0 is not beyond att.min(), which should be the first interval boundary";

        for (int i = 1; i < intervals.size(); ++i) {
            assert intervals.get(i) > intervals.get(i-1) : "entries in intervals vector must be decreasing";
        }

        assert intervals.get(intervals.size()-1) == att_.max() : "last element must be att max";

        /* hook qualitative attributes to reduce ifs - make logic more
         * declarative */
        intervals_ = intervals;
        states_ = states;
        initialized_ = true;
    }

    /**
     * Returns a slot which can be used to wire this value to the emissions of a {@link Signal} or
     * another value.
     */
    @Override public Slot<Integer> slot () {
        return new Slot<Integer> () {
            @Override public void onEmit (Integer value) {
                updateState(value);
            }
        };
    }

    State updateState(int v) {
        assert att_.inRange(v) : "received signal must be in this attribute's range";

//        System.out.println("Thisshitsssssssssssss");
//        print();
//        System.out.println("END Thisshitsssssssssssss");

        State s = null;

        for (int i = 0; i < intervals_.size(); ++i)  // TODO: binary search
            if (v <= intervals_.get(i)) {
                s = states_.get(i);
                break;
            }
        assert s != null : "state not set in updateState..";

        State cs = super.updateState(s);
        //print();
        //System.out.println("_______________________ ENDState: " + cs);
        return cs;
    }

    /**
     * Updates state by setting the underlying PetAttribute.
     * "Max" means it sets to the appropriate upper limit of the corresponding
     * interval range.  This is to be used when you want to set by qualitative
     * state. For instance, if you want to make the pet sick (and also change
     * the underlying its numeric PetAttribute), you do
     * petAttributes.sSaude().updateStateDeep(State.DOENTE);
     */
    int updateStateDeepMax(State s) {
        // find interval corresponding to s
        int i = states_.indexOf(s);
        att_.set(intervals_.get(i));
        // done. this sAttribute is set upon receipt of a signal from att_
        return att_.val();
    }

    /**
     * Same as updateStateDeepMax but returns lower limit of interval.
     */
    int updateStateDeepMin(State s) {
        // find interval corresponding to s
        int i = states_.indexOf(s);
        att_.set((i == 0) ? att_.min() : intervals_.get(i-1)+1);
        // done. this sAttribute is set upon receipt of a signal from att_
        return att_.val();
    }

    public boolean isInitialized() {
        return initialized_;
    }

    @Override public void print() {
        //super.print();
        //if (att_.name().equals("Nutricao")) {
        System.out.println("[state] " + att_.name() + ": "
                + PetAttributes.State.values()[get()]  + " (" + get() + ") " + " val: " +  att_.val());
        dprint("possible states and corresp intervals: ");

        dprint("        state: " + states_.get(0) + " interval: " + att_.min() + " to " + intervals_.get(0));

        for (int i = 1; i < states_.size(); ++i) {
            dprint("        state: " + states_.get(i) + " interval: " + (intervals_.get(i-1)+1) + " to " + intervals_.get(i));
        }
        //}
    }

    public ArrayList<State> states() { return states_; }

    // pointer to the attribute corresponding to this state
    public PetAttribute att_;
    ArrayList<State> states_;
    ArrayList<Integer> intervals_;
    boolean initialized_ = false;
}
