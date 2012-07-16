/*
 * Copyright 2009-2012 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.uci.ics.asterix.transaction.management.service.locking;

/**
 * LockWaiter object is used for keeping a lock waiter or a lock upgrader information on a certain resource.
 * The resource can be a dataset or an entity. 
 * @author kisskys
 *
 */
public class LockWaiter {
    /**
     * entityInfoSlotNum:
     * If this LockWaiter object is used, this variable is used to indicate the corresponding EntityInfoSlotNum.
     * Otherwise, the variable is used for nextFreeSlot Which indicates the next free waiter object.
     */
    private int entityInfoSlotNum;
    private boolean wait;
    private boolean victim;

    public LockWaiter() {
        this.victim = false;
        this.wait = true;
    }

    public void setEntityInfoSlot(int slotNum) {
        this.entityInfoSlotNum = slotNum;
    }

    public int getEntityInfoSlot() {
        return this.entityInfoSlotNum;
    }

    public void setNextFreeSlot(int slotNum) {
        this.entityInfoSlotNum = slotNum;
    }

    public int getNextFreeSlot() {
        return this.entityInfoSlotNum;
    }

    public void setWait(boolean wait) {
        this.wait = wait;
    }

    public boolean getWait() {
        return this.wait;
    }

    public void setVictim(boolean victim) {
        this.victim = victim;
    }

    public boolean getVictim() {
        return this.victim;
    }
}
