/*
 * Copyright 2009-2010 by The Regents of the University of California
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
package edu.uci.ics.asterix.metadata.statistics;

import java.io.Serializable;

import edu.uci.ics.asterix.metadata.declared.AqlSourceId;

/**
 * @author rico
 * 
 */
public class BaseStatistics extends AbstractMessageClass implements Serializable {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    private long tupleCount;
    private AqlSourceId ds;
    private String nodeId;

    public BaseStatistics(AqlSourceId datasource) {
        this.ds = datasource;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.STATISTICS;
    }

    public void setTupleCount(long tupleCount) {
        this.tupleCount = tupleCount;
    }

    public long getTupleCount() {
        return this.tupleCount;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public AqlSourceId getAqlSourceId() {
        return ds;
    }

    @Override
    public String toString() {
        return "{ \"nodeId\": \"" + this.nodeId + "\", \"tupleCount\"" + this.tupleCount + "}";
    }
}
