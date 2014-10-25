package com.pulapirata.core;

import java.util.Iterator;
import java.util.Random;

import react.Signal;
import react.Slot;

import playn.core.*;
import playn.core.util.Clock;
import static playn.core.PlayN.*;

import tripleplay.entity.Component;
import tripleplay.entity.Entity;
import tripleplay.entity.System;
import tripleplay.entity.World;
import tripleplay.util.Randoms;


/**
 * An entity world designed for character animation and interaction among
 * multiple other objects. Call this the <em>extrinsic world</em>. Pet's extrinsic
 * properties are efficiently updated together with the rest of the objects that
 * share those properties - position, velocity, etc.
 */
class PetWorld extends World {
    /*-------------------------------------------------------------------------------*/
    /** Misc variables */
    public  final GroupLayer layer_;
    public  final float width_;
    public  final float height_;
    public  final Signal<Key> keyDown_ = Signal.create();
    public  final Signal<Key> keyUp_ = Signal.create();
    private final Randoms rando_ = Randoms.with(new Random());
    private boolean attributesLoaded_ = false;

    /*-------------------------------------------------------------------------------*/
    /** Types of entities */
    public static final int PET      = (1 << 0);
    public static final int DROPPING = (1 << 1);
    public static final int VOMIT    = (1 << 2);
    public static final int DIARRHEA = (1 << 3);
    public static final int MOSQUITOS = (1 << 4);
    public static final int STINKY_MOSQUITOS = (1 << 5);

    /*-------------------------------------------------------------------------------*/
    /**
     * Components.
     * Components are bags of types, positions, and other properties shared among
     * playable entities in Pet (like the bunny itself and its droppings)
     */
    public final Component.IMask type_ = new Component.IMask(this);
    public final Component.XY pos_  = new Component.XY(this);
    public final Component.XY opos_ = new Component.XY(this);  // old pos for interpolates
    public final Component.XY vel_  = new Component.XY(this);  // pixels/ms
    public final Component.FScalar radius = new Component.FScalar(this); // diameter
    public final Component.IScalar expires_ = new Component.IScalar(this);  // expected lifetime
        // XXX    public final Component.Generic<Sprite> sprite_ = new Component.Generic<Sprite>(this);
    public final Component.Generic<Layer> spriteLayer_ = new Component.Generic<Layer>(this);
    public final Component.Generic<PetAttributes> pet_ = new Component.Generic<PetAttributes>(this);
        // XXX     public final PetAtlas atlas_;  // shared atlas amongst all sprites
    protected PetAttributes mainPet_;  // direct handle on the attributes of the main pet
    public int mainID_ = -1;

    public PetAttributes mainPet() { return mainPet_; }

    /*-------------------------------------------------------------------------------*/
    /** Time data */

    private int beat_ = 0; // total number of updates so far
    // the following is not static so that we can dynamically speedup the game if desired
    private int beatsCoelhoDia_ = 600; // beats por 1 coelho dia.
    private double beatsCoelhoHora_ = (double)beatsCoelhoDia_/24.f;
    private double beatsCoelhoSegundo_ = (double)beatsCoelhoDia_/(24.*60.*60.);
    // TODO: colocar em pet attributes?
    public int idadeCoelhoHoras() { return (int)((double)beat_ / ((double)beatsCoelhoDia_/24.)); }
    public int idadeCoelhoDias() { return beat_ / beatsCoelhoDia_; }

    /*-------------------------------------------------------------------------------*/
    /** Misc methods */

    @Override public void update (int delta) {
        beat_ += delta;
        super.update(delta);
    }

    public PetWorld (GroupLayer layer, float width, float height) {
        this.layer_  = layer;
        this.width_  = width;
        this.height_ = height;

        // load attributes. Only 1 pet attribute set is supported for now
        PetAttributesLoader.CreateAttributes(PetAttributes.JSON_PATH, beatsCoelhoHora_,
            new Callback<PetAttributes>() {
                @Override
                public void onSuccess(PetAttributes resource) {
                    mainPet_ = resource;
                    if (mainID_ != -1)
                        pet_(mainID_).didChange();
                    attributesLoaded_ = true;
                }

                @Override
                public void onFailure(Throwable err) {
                    PlayN.log().error("Error loading pet attributes: " + err.getMessage());
                }
            });


        keyboard().setListener(new Keyboard.Adapter() {
            @Override public void onKeyDown (Keyboard.Event event) {
                keyReloadGameFile.emit(event.key());
            }
        });
        reset();
    }

    // FIXME enum has something like this.
    protected String typeName (int id) {
        switch (type_.get(id)) {
        case PET: return "pet";
        case DROPPING: return "dropping";
        case VOMIT: return "vomit";
        case DIARRHEA: return "diarrhea";
        case MOSQUITOS: return "mosquitos";
        case STINKY_MOSQUITOS: return "stinky_mosquitos";
        default: return "unknown:" + type_.get(id);
      }
    }

    /*-------------------------------------------------------------------------------*/
    /** Motion Systems */

    /** Simple motion. Handles updating entity position based on entity velocity */
    public final System logicMover = new System(this, 0) {
        @Override protected void update (int delta, Entities entities) {
            Point p = innerPos_;
            Vector v = innerVel_;
            for (int ii = 0, ll = entities.size(); ii < ll; ii++) {
                int eid = entities.get(ii);
                pos_.get(eid, p); // get our current pos
                opos_.set(eid, p);
                vel.get(eid, v).scaleLocal(delta); // turn velocity into delta pos
                pos.set(eid, p.x + v.x, p.y + v.y); // add velocity
            }
        }

        @Override protected boolean isInterested (Entity entity) {
            return entity.has(opos) && entity.has(pos) && entity.has(vel);
        }

        protected final Point  innerPos_ = new Point();
        protected final Vector innerVel_ = new Vector();
    };

    /** Updates sprite layers to interpolated position of entities on each paint() call */
    public final System spriteMover = new System(this, 0) {
        @Override protected void paint (Clock clock, Entities entities) {
            float alpha = clock.alpha();
            Point op = innerOldPos_, p = innerPos_;
            for (int ii = 0, ll = entities.size(); ii < ll; ii++) {
                int eid = entities.get(ii);
                // interpolate between opos and pos and use that to update the sprite position
                opos_.get(eid, op);
                pos_.get(eid, p);
                // wrap our interpolated position as we may interpolate off the screen
                spriteLayer_.get(eid).setTranslation(wrapx(MathUtil.lerp(op.x, p.x, alpha)),
                                                     wrapy(MathUtil.lerp(op.y, p.y, alpha)));
            }
        }

        @Override protected void update (int delta) {
            for (int ii = 0, ll = entities.size(); ii < ll; ii++) {
                int eid = entities.get(ii);
                // XXX                sprite_.get(eid).update(delta);
            }
        }

        @Override protected void wasAdded (Entity entity) {
            super.wasAdded(entity);
            layer_.addAt(spriteLayer_.get(entity.id), pos_.getX(entity.id), pos_.getX(entity.id));
        }

        @Override protected void wasRemoved (Entity entity, int index) {
            super.wasRemoved(entity, index);
            layer_.remove(spriteLayer_.get(entity.id));
        }

        @Override protected boolean isInterested (Entity entity) {
            return entity.has(opos_) && entity.has(pos_) && entity.has(spriteLayer_);
        }

        protected final Point innerOldPos_ = new Point(), innerPos_ = new Point();
    };

    /**
     * Updates pet sprites to reflect inner state.
     */
    public final System spriteLinker = new System(this, 0) {
        @Override protected void update (int delta) {
            for (int ii = 0, ll = entities.size(); ii < ll; ii++) {
                int eid = entities.get(ii);
// XXX               sprite_.get(eid).update(delta);
            }
        }

        @Override protected void wasAdded (Entity entity) {
            super.wasAdded(entity);
            layer_.addAt(spriteLayer_.get(entity.id), pos_.getX(entity.id), pos_.getX(entity.id));
        }

        @Override protected void wasRemoved (Entity entity, int index) {
            super.wasRemoved(entity, index);
            layer_.remove(spriteLayer_.get(entity.id));
        }

        @Override protected boolean isInterested (Entity entity) {
            return type.get(entity.id) == PET;
        }

        protected final Point innerOldPos_ = new Point(), innerPos_ = new Point();
    };

    /** Use keys to control pet. Like in minigames inside this game. Pet should
     * automatically move and do something fun if no control is pressed. NOOP if
     * touchscreen or gamepad are available.
     */
    public final System walkControls = new System(this, 1) {
        /* ctor */ {
            keyDown_.connect(new Slot<Key>() {
                @Override public void onEmit (Key key) {
                    switch (key) {
                      // TODO colocar estado walk_velocity_ na classe pet?
                      case LEFT:  vel_.x = -WALK_VELOCITY;  vel_.y = 0;  break;
                      case RIGHT: vel_.x  =  WALK_VELOCITY;  vel_.y = 0;  break;
                      case UP:    vel_.x  =  0;  vel_.y =  WALK_VELOCITY;  break;
                      case DOWN:  vel_.x  =  0;  vel_.y = -WALK_VELOCITY;  break;
                      case SPACE: System.out.println("Key SPACE pressed: u mean jump?"); break;
                      case C: System.out.println("Key C pressed: u mean taka dump?"); break;
                    default: break;
                    }
                }
            });
            keyUp_.connect(new Slot<Key>() {
                @Override public void onEmit (Key key) {
                    switch (key) {
                      case LEFT:  vel_.x = 0; vel_.y = 0; break;
                      case RIGHT: vel_.x = 0; vel_.y = 0;  break;
                      case UP:    vel_.x = 0; vel_.y = 0; break;
                      case DOWN:  vel_.x = 0; vel_.y = 0;  break;
                    default: break;
                    }
                }
            });
        }

        @Override protected void wasAdded (Entity entity) {
            super.wasAdded(entity);
            _ship = entity;
        }

        @Override protected boolean isInterested (Entity entity) {
            return type.get(entity.id) == PET;
        }

        protected Vector vel_ = new Vector();
    };

    /** Checks for collisions. Like between PET and DROPPING. Models everything as a sphere. */
    public final System collider = new System(this, 1) {
        @Override protected void update (int delta, Entities entities) {
            // simple O(n^2) collision check; no need for anything fancy here
            for (int ii = 0, ll = entities.size(); ii < ll; ii++) {
                int eid1 = entities.get(ii);
                Entity e1 = world.entity(eid1);
                if (e1.isDestroyed()) continue;
                pos_.get(eid1, p1);
                float r1 = radius.get(eid1);
                for (int jj = ii+1; jj < ll; jj++) {
                    int eid2 = entities.get(jj);
                    Entity e2 = world.entity(eid2);
                    if (e2.isDestroyed()) continue;
                    pos.get(eid2, p2);
                    float r2 = radius.get(eid2), dr = r2+r1;
                    float dist2 = p1.distanceSq(p2);
                    if (dist2 <= dr*dr) {
                        collide(e1, e2);
                        break; // don't collide e1 with any other entities
                    }
                }
            }
        }

        @Override protected boolean isInterested (Entity entity) {
            return entity.has(pos) && entity.has(radius);
        }

        private void collide (Entity e1, Entity e2) {
            if (attributesLoaded_) {
                switch (type.get(e1.id) | type.get(e2.id)) {
                case PET_DROPPING:
                    if (type.get(e1.id) == PET) {
                        if (pet_.get(e1.id).mode(pet_.get(e1.id).sAction()) == PetAttributesActionState.CLEANING) {
                            e2.destroy();
                        }
                    } else {
                        if (pet_.get(e2.id).mode(pet_.get(e1.id).sAction()) == PetAttributesActionState.CLEANING) {
                            e1.destroy();
                        }
                    }
                    break;
                default: break; // nada
                }
            }
        }

        protected static final int PET_DROPPING = PET|DROPPING;
        protected static final int PET_VOMIT = PET|VOMIT;
        protected static final int PET_DIARRHEA = PET|DIARRHEA;

        protected final Point _p1 = new Point(), _p2 = new Point();
    };

    /*-------------------------------------------------------------------------------*/
    /** Game logic that generalizes among many entities */

    // expires things with limited lifespan
    public final System expirer = new System(this, 0) {
        @Override protected void update (int delta, Entities entities) {
            int now = this.beat_;
            for (int ii = 0, ll = entities.size(); ii < ll; ii++) {
                int eid = entities.get(ii);
                if (expires_.get(eid) <= now) world.entity(eid).destroy();
            }
        }

        @Override protected boolean isInterested (Entity entity) {
            return entity.has(expires);
        }
    };

    /*-------------------------------------------------------------------------------*/
    /** Entity creation */

    protected Entity createPet (float x, float y) {
        Entity pet = create(true);
        pet.add(type, pet_, /* XXX sprite_, */spriteLayer_, opos_, pos_, vel_, radius_, expires_);

        int id = pet_.id;
        type_.set(id, PET);
        opos_.set(id, x, y);
        pos_.set(id, x, y);
        vel_.set(id, 0, 0);
        pet_.set(id, mainPet_); // only 1 pet for now, but more are easily supported
        mainID_ = id;

        // read imgLayer /sprite loader
        // XXX PetSprite ps(imgLayer, atlas_);

        // -> pet.connect(ps.slot());
        // XXX        sprite_.set(id, ps);
        spriteLayer_.set(id, imgLayer);

        // XXX        radius.set(id, sprite_.boundingRadius());
        // radius.ComputeFromSprite(id, 10);
        return pet;
    }

    public void reset() {
        Iterator<Entity> iter = entities();
        while (iter.hasNext()) iter.next().destroy();
        createPet(width_/2., height_/2.);
    }

    /* TODO load atlas */
}
