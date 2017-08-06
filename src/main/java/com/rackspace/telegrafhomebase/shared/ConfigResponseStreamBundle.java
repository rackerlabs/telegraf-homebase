package com.rackspace.telegrafhomebase.shared;

import io.grpc.stub.StreamObserver;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import remote.Telegraf;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This is a concurrency-safe maintainer of config grpc response streams for a "bundle" of remote telegrafs in
 * a region. It will take care of randomly picking from the known response streams and removing from the bundle
 * when the handler indicates a stream is discontinued.
 *
 * @author Geoff Bourne
 * @since Jul 2017
 */
@Slf4j
public class ConfigResponseStreamBundle implements Closeable, TelegrafDisconnectFunction {

    /**
     * Used around all access to entries.
     */
    private final ReentrantLock lock;
    /**
     * Used to conditionally block on emptiness of entries.
     */
    private final Condition notEmpty;
    private final List<Entry> entries = new ArrayList<>();

    private final Random rand;
    private final TelegrafDisconnectFunction removalFunction;
    private boolean closed;

    public ConfigResponseStreamBundle(Random rand, TelegrafDisconnectFunction removalFunction) {
        this.rand = rand;
        this.removalFunction = removalFunction;
        lock = new ReentrantLock();
        notEmpty = lock.newCondition();
    }

    /**
     * Closes this bundle and signals any threads blocked in {@link #waitForReady()}
     * @throws IOException
     */
    @Override
    public void close() throws IOException {
        log.debug("Closing");

        lock.lock();
        try {
            closed = true;
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    /**
     * This blocks until at least one configuration observer (a remote telegraf instance) is present.
     *
     * @return true if calls to this should continue
     * @throws InterruptedException when interrupted
     */
    public boolean waitForReady() throws InterruptedException {
        lock.lock();
        try {
            while (!closed && entries.isEmpty()) {
                log.debug("Waiting for ready");
                notEmpty.await();
            }
            return !closed;
        } finally {
            lock.unlock();
        }
    }

    public void add(Entry entry) {
        log.debug("Adding {}", entry);
        lock.lock();
        try {
            entries.add(entry);
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Picks a configuration stream entry at random and calls the given <code>handler</code> with it.
     * @param handler will be called for one of the entries at random
     */
    public void respondToOne(EntryPredicate handler) {
        lock.lock();
        try {
            final int choice = rand.nextInt(entries.size());

            final Entry entry = entries.get(choice);
            final boolean keep = handler.invoke(entry);
            if (!keep) {
                log.debug("Removing due to handler response {}", entry);
                entries.remove(choice);
                removalFunction.handleDisconnect(entry.getTid());
            }

        } finally {
            lock.unlock();
        }

    }

    /**
     * Calls the given <code>handler</code> for all entries, which is usually needed when broadcasting an error
     * condition to all.
     *
     * @param handler will be called for each entry
     */
    public void respondToAll(EntryPredicate handler) {
        lock.lock();
        try {
            final Iterator<Entry> itr = entries.iterator();
            while (itr.hasNext()) {
                final Entry entry = itr.next();
                final boolean keep = handler.invoke(entry);
                if (!keep) {
                    log.debug("Removing due to handler response {}", entry);
                    itr.remove();
                    removalFunction.handleDisconnect(entry.getTid());
                }
            }

        } finally {
            lock.unlock();
        }

    }

    @Override
    public void handleDisconnect(String tid) {
        lock.lock();
        try {
            entries.removeIf(entry -> entry.getTid().equals(tid));
        } finally {
            lock.unlock();
        }
    }

    public interface EntryPredicate {
        boolean invoke(Entry entry);
    }

    /**
     * Represents an entry to track in the bundle.
     */
    @Data
    public static class Entry {

        /**
         * The ID of the remote telegraf instance.
         */
        final String tid;
        final StreamObserver<Telegraf.ConfigPack> stream;

    }
}
