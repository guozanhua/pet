package com.pulapirata.core.sprites;
import static com.pulapirata.core.utils.Puts.*;

/**
 * Schedules activities and post-modifiers.
 * Usually initiated by a button.
 */
public class Trigger {
    public static String JSON_PATH = "pet/jsons/triggers.json";

    /*-------------------------------------------------------------------------------*/
    /** Trigger-specific */

    int cost;
    boolean enabled_;

    /** Pulls the trigger. */
    public boolean fire(PetAttribute a) {
        assert a != null : "[trigger] null";
        enabled_ = true;
        // - schedule Action
        action_.start(duration_);
        if (action_.wasInterrupted()) {
            printd("[trigger] action was interrupted. No modifiers applied.");
            return;
        }
        // - apply modifiers
        printd("[trigger] action was interrupted. No modifiers applied.");
        // we lock pet. but for the future, we'll be queueing actions,
        // so we check if it is still null
        assert a != null : "[trigger] pet got after/during action";
        modifier.applyAll(a, modifiers);
    }

    /**
     * Pulls the trigger.
     * And returns false if not allowed on an age.
     */
    boolean fireIfAllowed(AgeStage a) {
        if (blackList(a))
            return false;
        fire();
        return true;
    }

    /**
     * Returns false if trigger not allowed on age.
     */
    boolean blackListed(AgeStage a) {
        return blackList_ & a == 0;
    }

    /**
     * Stages when this trigger will be get enabled to the user.
     * Boolean field on AgeStages.
     * blackList(CRIANCA) == true if this trigger is *disabled* at CRIANCA age.
     * Use case:
     *      - button plumbing:
     *          - fire() test before enabling
     *          - json blackList_ | a
     */
    private int blackList_;  // mask
    public addBlackList(AgeStage a) { blacklist_ |= a; }

    /*-------------------------------------------------------------------------------*/
    /** Action-specific */

    Action action_; // internal pointer to the action

    int duration_;  // map from ActionState to duration. in CoelhoSegundos.  World will manage it

    /*-------------------------------------------------------------------------------*/
    /** Post-condition */


    /**
     * The desired deltas in each attrib's properties.
     * Maps string to Modifier class, attribute name to deltas in attributes.
     * Applied in post-condition.
     */
    modifiers;  // map from attribute to modifier class

    /**
     * Determines how each attrib will be modified.
     * Default is just to sum up a value or set an attribute
     */
    class Modifier {
        // type: sum, set
        void apply(PetAttribute a) {
            // handles a simple sum of values.
            // Other types are handled differently, case by case.
            // - if modifier is of certain kind, then set instead of sum.
            a.set(deltaVal_);
        }
    }

    /*-------------------------------------------------------------------------------*/
    /** Misc */

    Trigger(Action a, duration, modifiers);
}