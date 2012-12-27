/*
 * Copyright 2009-2011 by The Regents of the University of California
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
package edu.uci.ics.asterix.feed.managed.adapter;

import java.util.Map;

public interface IManagedFeedAdapter {

    public enum OperationState {
        // ACTIVE state signifies that the adapter is connected to an external
        // data source.
        ACTIVE,
        // ACTIVE state signifies that the adapter is not connected to an external
        // data source.
        INACTIVE
    }

    public void stop() throws Exception;

    public void alter(Map<String, String> properties) throws Exception;

}
