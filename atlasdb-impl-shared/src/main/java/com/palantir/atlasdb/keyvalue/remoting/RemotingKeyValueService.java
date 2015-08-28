/**
 * Copyright 2015 Palantir Technologies
 *
 * Licensed under the BSD-3 License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.atlasdb.keyvalue.remoting;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;

import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.palantir.atlasdb.keyvalue.api.Cell;
import com.palantir.atlasdb.keyvalue.api.KeyValueService;
import com.palantir.atlasdb.keyvalue.api.RangeRequest;
import com.palantir.atlasdb.keyvalue.api.RowResult;
import com.palantir.atlasdb.keyvalue.api.Value;
import com.palantir.atlasdb.keyvalue.impl.ForwardingKeyValueService;
import com.palantir.atlasdb.keyvalue.partition.DynamicPartitionMapImpl;
import com.palantir.atlasdb.keyvalue.partition.PartitionMapService;
import com.palantir.atlasdb.keyvalue.partition.api.PartitionMap;
import com.palantir.atlasdb.keyvalue.partition.exception.VersionTooOldException;
import com.palantir.atlasdb.keyvalue.partition.util.VersionedObject;
import com.palantir.atlasdb.keyvalue.remoting.iterators.HistoryRangeIterator;
import com.palantir.atlasdb.keyvalue.remoting.iterators.RangeIterator;
import com.palantir.atlasdb.keyvalue.remoting.iterators.TimestampsRangeIterator;
import com.palantir.atlasdb.keyvalue.remoting.iterators.ValueRangeIterator;
import com.palantir.atlasdb.keyvalue.remoting.serialization.BytesAsKeyDeserializer;
import com.palantir.atlasdb.keyvalue.remoting.serialization.CellAsKeyDeserializer;
import com.palantir.atlasdb.keyvalue.remoting.serialization.RowResultDeserializer;
import com.palantir.atlasdb.keyvalue.remoting.serialization.RowResultSerializer;
import com.palantir.atlasdb.keyvalue.remoting.serialization.SaneAsKeySerializer;
import com.palantir.atlasdb.server.OutboxShippingInterceptor;
import com.palantir.common.base.ClosableIterator;
import com.palantir.common.supplier.ExecutorInheritableServiceContext;
import com.palantir.common.supplier.PopulateServiceContextProxy;
import com.palantir.common.supplier.RemoteContextHolder;
import com.palantir.common.supplier.RemoteContextHolder.RemoteContextType;
import com.palantir.common.supplier.ServiceContext;
import com.palantir.util.Pair;

import feign.Feign;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.jaxrs.JAXRSContract;

public class RemotingKeyValueService extends ForwardingKeyValueService {
    final static ServiceContext<KeyValueService> serviceContext = ExecutorInheritableServiceContext.create();
    final static ServiceContext<Long> clientVersionContext = ExecutorInheritableServiceContext.create();
    // RemoteContextHolder.INBOX.getProviderForKey(VersionedKeyValueEndpoint.VERSIONED_PM.PM_VERSION);

    public static ServiceContext<KeyValueService> getServiceContext() {
        return serviceContext;
    }

    public static ServiceContext<Long> getClientVersionContext() {
        return clientVersionContext;
    }

    final KeyValueService delegate;

    public static KeyValueService createClientSide(final KeyValueService remoteService) {
        return new ForwardingKeyValueService() {
            @Override
            protected KeyValueService delegate() {
                return remoteService;
            }

            @SuppressWarnings("unchecked")
            private <T extends ClosableIterator<?>> T withKvs(T it) {
                return (T) PopulateServiceContextProxy.newProxyInstanceWithConstantValue(
                        ClosableIterator.class, it, delegate(), serviceContext);
            }

            @Override
            public ClosableIterator<RowResult<Value>> getRange(String tableName,
                                                               RangeRequest rangeRequest,
                                                               long timestamp) {
                return withKvs(super.getRange(tableName, rangeRequest, timestamp));
            }

            @Override
            public ClosableIterator<RowResult<Set<Value>>> getRangeWithHistory(String tableName,
                                                                               RangeRequest rangeRequest,
                                                                               long timestamp) {
                return withKvs(super.getRangeWithHistory(tableName, rangeRequest, timestamp));
            }

            @Override
            public ClosableIterator<RowResult<Set<Long>>> getRangeOfTimestamps(String tableName,
                                                                               RangeRequest rangeRequest,
                                                                               long timestamp) {
                return withKvs(super.getRangeOfTimestamps(tableName, rangeRequest, timestamp));
            }
        };
    }

    public enum HOLDER implements RemoteContextType<Long> {
        PM_VERSION {
            @Override
            public Class<Long> getValueType() {
                return Long.class;
            }
        }
    }

    public static KeyValueService createClientSide(String uri, Supplier<Long> localVersionSupplier) {
        ServiceContext<Long> ctx = RemoteContextHolder.OUTBOX.getProviderForKey(HOLDER.PM_VERSION);
        KeyValueService ret = createClientSide(Feign.builder()
                .encoder(new JacksonEncoder(kvsMapper()))
                .decoder(new EmptyOctetStreamDelegateDecoder(new JacksonDecoder(kvsMapper())))
                .errorDecoder(KeyValueServiceErrorDecoder.instance())
                .contract(new JAXRSContract())
                .requestInterceptor(new OutboxShippingInterceptor(kvsMapper))
                .target(KeyValueService.class, uri));
        return PopulateServiceContextProxy.newProxyInstance(
                KeyValueService.class, ret, localVersionSupplier, ctx);
    }

    public static KeyValueService createServerSide(KeyValueService delegate, Supplier<Long> serverVersionSupplier) {
        final KeyValueService kvs = new RemotingKeyValueService(delegate);
        return VersionCheckProxy.newProxyInstance(kvs, serverVersionSupplier);
    }

    static class VersionCheckProxy implements InvocationHandler {
        final Supplier<Long> serverVersionProvider;
        final KeyValueService delegate;

        private VersionCheckProxy(Supplier<Long> serverVersionProvider, KeyValueService delegate) {
            this.serverVersionProvider = serverVersionProvider;
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            ServiceContext<Long> remoteClientCtx = RemoteContextHolder.INBOX.getProviderForKey(HOLDER.PM_VERSION);
            if (method.getDeclaringClass() == KeyValueService.class) {
                Long clientVersion = remoteClientCtx.get();
                Long serverVersion = Preconditions.checkNotNull(serverVersionProvider.get());
                if (serverVersion < 0L) {
                    // In this case the version check is simply disabled.
                    assert clientVersion == null;
                } else {
                    if (clientVersion < serverVersion) {
                        throw new VersionTooOldException(serverVersion, "Version too old. Please update!");
                    }
                }
            }
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        public static KeyValueService newProxyInstance(KeyValueService delegate, Supplier<Long> serverVersionProvider) {
            VersionCheckProxy vcp = new VersionCheckProxy(serverVersionProvider, delegate);
            return (KeyValueService) Proxy.newProxyInstance(
                    KeyValueService.class.getClassLoader(),
                    new Class<?>[] { KeyValueService.class }, vcp);
        }

    }

    public static class AutoUpdateProxy implements InvocationHandler {

        final KeyValueService remoteKvs;
        final PartitionMapService remotePms;
        final PartitionMapService localPms;


        private AutoUpdateProxy(KeyValueService delegate,
                                PartitionMapService remotePms,
                                PartitionMapService localPms) {
            this.remoteKvs = delegate;
            this.remotePms = remotePms;
            this.localPms = localPms;
        }


        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(remoteKvs, args);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof VersionTooOldException) {
                    VersionTooOldException vtoe = (VersionTooOldException) cause;
                    VersionedObject<PartitionMap> newPmap = remotePms.get();
                    if (newPmap.getVersion() < vtoe.getMinRequiredVersion()) {
                        throw new RuntimeException("Updated map version is still too old. This does not make too much sense.");
                    }
                    localPms.update(newPmap.getVersion(), newPmap.getObject());
                    return invoke(proxy, method, args);
                }
                throw cause;
            }
        }

    }

    private RemotingKeyValueService(KeyValueService service) {
        this.delegate = service;
    }

    @Override
    protected KeyValueService delegate() {
        return delegate;
    }

    @Override
    public RangeIterator<Value> getRange(final String tableName,
                                         final RangeRequest range,
                                         final long timestamp) {
        return transformIterator(tableName, range, timestamp, super.getRange(tableName, range, timestamp),
            new Function<Pair<Boolean, ImmutableList<RowResult<Value>>>, RangeIterator<Value>>() {
                @Override @Nullable
                public RangeIterator<Value> apply(@Nullable Pair<Boolean, ImmutableList<RowResult<Value>>> input) {
                    return new ValueRangeIterator(tableName, range, timestamp, input.lhSide, input.rhSide);
                }
            });
    }

    @Override
    public RangeIterator<Set<Value>> getRangeWithHistory(final String tableName,
                                                         final RangeRequest rangeRequest,
                                                         final long timestamp) {
        return transformIterator(tableName, rangeRequest, timestamp, super.getRangeWithHistory(tableName, rangeRequest, timestamp),
            new Function<Pair<Boolean, ImmutableList<RowResult<Set<Value>>>>, RangeIterator<Set<Value>>>(){
                @Override @Nullable
                public RangeIterator<Set<Value>> apply(@Nullable Pair<Boolean, ImmutableList<RowResult<Set<Value>>>> input) {
                    return new HistoryRangeIterator(tableName, rangeRequest, timestamp, input.lhSide, input.rhSide);
                }
            });
    }

    @Override
    public RangeIterator<Set<Long>> getRangeOfTimestamps(final String tableName,
                                                         final RangeRequest rangeRequest,
                                                         final long timestamp) {
        return transformIterator(tableName, rangeRequest, timestamp, super.getRangeOfTimestamps(tableName, rangeRequest, timestamp),
            new Function<Pair<Boolean, ImmutableList<RowResult<Set<Long>>>>, RangeIterator<Set<Long>>>() {
                @Override @Nullable
                public RangeIterator<Set<Long>> apply(@Nullable Pair<Boolean, ImmutableList<RowResult<Set<Long>>>> input) {
                    return new TimestampsRangeIterator(tableName, rangeRequest, timestamp, input.lhSide, input.rhSide);
                }
            });
    }

    private static final SimpleModule kvsModule = new SimpleModule(); static {
        kvsModule.addKeyDeserializer(Cell.class, CellAsKeyDeserializer.instance());
        kvsModule.addKeyDeserializer(byte[].class, BytesAsKeyDeserializer.instance());
        kvsModule.addKeySerializer(Cell.class, SaneAsKeySerializer.instance());
        kvsModule.addKeySerializer(byte[].class, SaneAsKeySerializer.instance());
        kvsModule.addSerializer(RowResult.class, RowResultSerializer.instance());
        kvsModule.addDeserializer(RowResult.class, RowResultDeserializer.instance());
        kvsModule.addSerializer(DynamicPartitionMapImpl.class, DynamicPartitionMapImpl.Serializer.instance());
        kvsModule.addDeserializer(DynamicPartitionMapImpl.class, DynamicPartitionMapImpl.Deserializer.instance());
    }
    private static final ObjectMapper kvsMapper = new ObjectMapper(); static {
        kvsMapper.registerModule(kvsModule);
        kvsMapper.registerModule(new GuavaModule());
    }
    public static SimpleModule kvsModule() {
        return kvsModule;
    }
    public static ObjectMapper kvsMapper() {
        return kvsMapper;
    }

    private static <T> RangeIterator<T> transformIterator(String tableName, RangeRequest range,
                                                   long timestamp, ClosableIterator<RowResult<T>> closableIterator,
                                                   Function<Pair<Boolean, ImmutableList<RowResult<T>>>, RangeIterator<T>> resultSupplier) {
        try {
            int pageSize = range.getBatchHint() != null ? range.getBatchHint() : 100;
            if (pageSize == 1) {
                pageSize = 2;
            }
            ImmutableList<RowResult<T>> page = ImmutableList.copyOf(Iterators.limit(closableIterator, pageSize));
            if (page.size() < pageSize) {
                return resultSupplier.apply(Pair.create(false, page));
            } else {
                return resultSupplier.apply(Pair.create(true, page.subList(0, pageSize - 1)));
            }
        } finally {
            closableIterator.close();
        }
    }
}
