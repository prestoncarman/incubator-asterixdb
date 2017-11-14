/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.asterix.transaction.management.service.transaction;

import java.util.concurrent.atomic.AtomicLong;

import org.apache.asterix.common.transactions.TxnId;

/**
 * Represents a factory to generate unique transaction IDs.
 */
public class TxnIdFactory {

    private static final AtomicLong id = new AtomicLong();

    private TxnIdFactory() {
    }

    public static TxnId create() {
        return new TxnId(id.incrementAndGet());
    }

    public static void ensureMinimumId(long id) {
        TxnIdFactory.id.updateAndGet(current -> Math.max(current, id));
    }
}