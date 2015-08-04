/**
 * // Copyright 2015 Palantir Technologies
 * //
 * // Licensed under the BSD-3 License (the "License");
 * // you may not use this file except in compliance with the License.
 * // You may obtain a copy of the License at
 * //
 * // http://opensource.org/licenses/BSD-3-Clause
 * //
 * // Unless required by applicable law or agreed to in writing, software
 * // distributed under the License is distributed on an "AS IS" BASIS,
 * // WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * // See the License for the specific language governing permissions and
 * // limitations under the License.
 */
package com.palantir.atlasdb.transaction.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.io.BaseEncoding;
import com.palantir.atlasdb.keyvalue.api.Cell;
import com.palantir.atlasdb.transaction.api.Transaction;
import com.palantir.util.AssertUtils;

/**
 * Disallows the same cell from being written twice with different values within
 * the same transaction. Multiple writes to same cell which write the same value
 * are allowed.
 */
public class NoDuplicateWritesTransaction extends ForwardingTransaction {
    final Transaction delegate;
    final ImmutableSet<String> noDoubleWritesTables;
    final LoadingCache<String, Map<Cell, byte[]>> writes = CacheBuilder.newBuilder().build(new CacheLoader<String, Map<Cell, byte[]>>() {
        @Override
        public Map<Cell, byte[]> load(String input) {
            return Collections.synchronizedMap(Maps.<Cell, byte[]>newHashMap());
        }
    });

    public NoDuplicateWritesTransaction(Transaction delegate, Iterable<String> noDoubleWritesTables) {
        this.delegate = delegate;
        this.noDoubleWritesTables = ImmutableSet.copyOf(noDoubleWritesTables);
    }

    @Override
    public Transaction delegate() {
        return delegate;
    }

    @Override
    public void put(String tableName, Map<Cell, byte[]> values) {
        validateWrites(tableName, values);
        super.put(tableName, values);
    }

    @Override
    public void delete(String tableName, Set<Cell> keys) {
        // Map deletes into writes of zero-length byte arrays (this is in
        // accordance with the semantics of our transaction API).
        Map<Cell, byte[]> values = Maps.newHashMap();
        for (Cell c : keys) {
            values.put(c, new byte[0]);
        }
        validateWrites(tableName, values);
        super.delete(tableName, keys);
    }

    private void validateWrites(String tableName, Map<Cell, byte[]> values) {
        if (noDoubleWritesTables.contains(tableName)) {
            Map<Cell, byte[]> table;
            try {
                table = writes.get(tableName);
            } catch (ExecutionException e) {
                throw new RuntimeException(e.getCause());
            }
            for (Cell cell : values.keySet()) {
                byte[] newValue = values.get(cell);
                byte[] oldValue = table.get(cell);
                if (oldValue != null && !Arrays.equals(oldValue, newValue)) {
                    AssertUtils.assertAndLog(false, "table: " + tableName
                            + " cell was writen to twice: " + cell
                            + " old value: " + BaseEncoding.base16().lowerCase().encode(oldValue)
                            + " new value: " + BaseEncoding.base16().lowerCase().encode(newValue));
                        break;
                }
            }
        }
    }
}