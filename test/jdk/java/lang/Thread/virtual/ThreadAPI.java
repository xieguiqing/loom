/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/**
 * @test
 * @run testng ThreadAPI
 * @summary Test Thread API with virtual threads
 */

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import java.nio.channels.Selector;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

@Test
public class ThreadAPI {

    // -- Thread.currentThread --

    //  Thread.currentThread before/after park
    public void testCurrentThread1() throws Exception {
        var before = new AtomicReference<Thread>();
        var after = new AtomicReference<Thread>();
        var thread = Thread.newThread(Thread.VIRTUAL, () -> {
            before.set(Thread.currentThread());
            LockSupport.park();
            after.set(Thread.currentThread());
        });
        thread.start();
        Thread.sleep(100); // give time for virtual thread to park
        LockSupport.unpark(thread);
        thread.join();
        assertTrue(before.get() == thread);
        assertTrue(after.get() == thread);
    }

    //  Thread.currentThread before/after synchronized block
    public void testCurrentThread2() throws Exception {
        var ref1 = new AtomicReference<Thread>();
        var ref2 = new AtomicReference<Thread>();
        var ref3 = new AtomicReference<Thread>();
        var lock = new Object();
        var thread = Thread.newThread(Thread.VIRTUAL, () -> {
            ref1.set(Thread.currentThread());
            synchronized (lock) {
                ref2.set(Thread.currentThread());
            }
            ref3.set(Thread.currentThread());
        });
        synchronized (lock) {
            thread.start();
            Thread.sleep(100); // give time for virtual thread to block
        }
        thread.join();
        assertTrue(ref1.get() == thread);
        assertTrue(ref2.get() == thread);
        assertTrue(ref3.get() == thread);
    }

    //  Thread.currentThread before/after lock
    public void testCurrentThread3() throws Exception {
        var ref1 = new AtomicReference<Thread>();
        var ref2 = new AtomicReference<Thread>();
        var ref3 = new AtomicReference<Thread>();
        var lock = new ReentrantLock();
        var thread = Thread.newThread(Thread.VIRTUAL, () -> {
            ref1.set(Thread.currentThread());
            lock.lock();
            try {
                ref2.set(Thread.currentThread());
            } finally {
                lock.unlock();
            }
            ref3.set(Thread.currentThread());
        });
        lock.lock();
        try {
            thread.start();
            Thread.sleep(100); // give time for virtual thread to block
        } finally {
            lock.unlock();
        }
        thread.join();
        assertTrue(ref1.get() == thread);
        assertTrue(ref2.get() == thread);
        assertTrue(ref3.get() == thread);
    }


    // -- run --

    public void testRun1() throws Exception {
        var ref = new AtomicBoolean();
        var thread = Thread.newThread(Thread.VIRTUAL, () -> ref.set(true));
        thread.run();
        assertFalse(ref.get());
    }


    // -- start --

    // already started
    public void testStart1() throws Exception {
        var thread = Thread.newThread(Thread.VIRTUAL, () -> LockSupport.park());
        thread.start();
        try {
            assertThrows(IllegalThreadStateException.class, thread::start);
        } finally {
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    // already terminated
    public void testStart2() throws Exception {
        var thread = Thread.newThread(Thread.VIRTUAL, () -> { });
        thread.start();
        thread.join();
        assertThrows(IllegalThreadStateException.class, thread::start);
    }


    // -- stop/suspend/resume --

    public void testStop1() throws Exception {
        TestHelper.runInVirtualThread(() -> {
            Thread t = Thread.currentThread();
            try {
                t.stop();
                assertTrue(false);
            } catch (UnsupportedOperationException e) {
                // expected
            }
        });
    }

    public void testStop2() throws Exception {
        var thread = Thread.newThread(Thread.VIRTUAL, () -> {
            try {
                Thread.sleep(20*1000);
            } catch (InterruptedException e) { }
        });
        thread.start();
        try {
            assertThrows(UnsupportedOperationException.class, () -> thread.stop());
        } finally {
            thread.interrupt();
            thread.join();
        }
    }

    public void testSuspend1() throws Exception {
        TestHelper.runInVirtualThread(() -> {
            Thread t = Thread.currentThread();
            try {
                t.suspend();
                assertTrue(false);
            } catch (UnsupportedOperationException e) {
                // expected
            }
        });
    }

    public void testSuspend2() throws Exception {
        var thread = Thread.newThread(Thread.VIRTUAL, () -> {
            try {
                Thread.sleep(20*1000);
            } catch (InterruptedException e) { }
        });
        thread.start();
        try {
            assertThrows(UnsupportedOperationException.class, () -> thread.suspend());
        } finally {
            thread.interrupt();
            thread.join();
        }
    }

    public void testResume1() throws Exception {
        TestHelper.runInVirtualThread(() -> {
            Thread t = Thread.currentThread();
            try {
                t.resume();
                assertTrue(false);
            } catch (UnsupportedOperationException e) {
                // expected
            }
        });
    }

    public void testResume2() throws Exception {
        var thread = Thread.newThread(Thread.VIRTUAL, () -> {
            try {
                Thread.sleep(20*1000);
            } catch (InterruptedException e) { }
        });
        thread.start();
        try {
            assertThrows(UnsupportedOperationException.class, () -> thread.resume());
        } finally {
            thread.interrupt();
            thread.join();
        }
    }


    // -- join --

    // join before start
    public void testJoin1() throws Exception {
        var thread = Thread.newThread(Thread.VIRTUAL, () -> { });

        // invoke join from dinosaur thread
        thread.join();
        thread.join(0);
        thread.join(100);
        assertThrows(IllegalThreadStateException.class,
                () -> thread.join(Duration.ofMillis(-100)));
        assertThrows(IllegalThreadStateException.class,
                () -> thread.join(Duration.ofMillis(0)));
        assertThrows(IllegalThreadStateException.class,
                () -> thread.join(Duration.ofMillis(100)));
    }

    // join before start
    public void testJoin2() throws Exception {
        TestHelper.runInVirtualThread(this::testJoin1);
    }

    // join where thread does not terminate
    public void testJoin3() throws Exception {
        var thread = Thread.newThread(Thread.VIRTUAL, () -> LockSupport.park());
        thread.start();
        try {
            thread.join(100);
            assertFalse(thread.join(Duration.ofMillis(-100)));
            assertFalse(thread.join(Duration.ofMillis(0)));
            assertFalse(thread.join(Duration.ofMillis(100)));
            assertTrue(thread.isAlive());
        } finally {
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    // join where thread does not terminate
    public void testJoin4() throws Exception {
        TestHelper.runInVirtualThread(this::testJoin3);
    }

    // join where thread terminates
    public void testJoin5() throws Exception {
        var thread = Thread.newThread(Thread.VIRTUAL, () -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) { }
        });
        thread.start();
        thread.join();
        assertFalse(thread.isAlive());
    }

    // join where thread terminates
    public void testJoin6() throws Exception {
        TestHelper.runInVirtualThread(this::testJoin5);
    }

    // join where thread terminates
    public void testJoin7() throws Exception {
        var thread = Thread.newThread(Thread.VIRTUAL, () -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) { }
        });
        thread.start();
        thread.join(10*1000);
        assertFalse(thread.isAlive());
    }

    // join where thread terminates
    public void testJoin8() throws Exception {
        TestHelper.runInVirtualThread(this::testJoin7);
    }

    // join where thread terminates
    public void testJoin9() throws Exception {
        var thread = Thread.newThread(Thread.VIRTUAL, () -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) { }
        });
        thread.start();
        thread.join(10*1000);
        assertFalse(thread.isAlive());
    }

    // join where thread terminates
    public void testJoin10() throws Exception {
        TestHelper.runInVirtualThread(this::testJoin9);
    }

    // join where thread terminates
    public void testJoin11() throws Exception {
        var thread = Thread.newThread(Thread.VIRTUAL, () -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) { }
        });
        thread.start();
        assertTrue(thread.join(Duration.ofSeconds(10)));
        assertFalse(thread.isAlive());
    }

    // join where thread terminates
    public void testJoin12() throws Exception {
        TestHelper.runInVirtualThread(this::testJoin11);
    }

    // join thread that has terminated
    public void testJoin13() throws Exception {
        var thread = Thread.newThread(Thread.VIRTUAL, () -> { });
        thread.start();
        while (thread.isAlive()) {
            Thread.sleep(10);
        }
        thread.join();
        thread.join(100);
        assertTrue(thread.join(Duration.ofMillis(-100)));
        assertTrue(thread.join(Duration.ofMillis(0)));
        assertTrue(thread.join(Duration.ofMillis(100)));
    }

    // join where thread terminates
    public void testJoin14() throws Exception {
        TestHelper.runInVirtualThread(this::testJoin13);
    }

    // interrupt status before join
    public void testJoin15() throws Exception {
        var thread = Thread.newThread(Thread.VIRTUAL, () -> LockSupport.park());
        thread.start();
        Thread.currentThread().interrupt();
        try {
            thread.join();
            assertTrue(false);
        } catch (InterruptedException expected) {
            // okay
        } finally {
            Thread.interrupted();
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    // interrupt status before join
    public void testJoin16() throws Exception {
        TestHelper.runInVirtualThread(this::testJoin15);
    }

    // interrupt status before join
    public void testJoin17() throws Exception {
        var thread = Thread.newThread(Thread.VIRTUAL, () -> LockSupport.park());
        thread.start();
        Thread.currentThread().interrupt();
        try {
            thread.join(100);
            assertTrue(false);
        } catch (InterruptedException expected) {
            // okay
        } finally {
            Thread.interrupted();
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    // interrupt status before join
    public void testJoin18() throws Exception {
        TestHelper.runInVirtualThread(this::testJoin17);
    }

    // interrupt status before join
    public void testJoin19() throws Exception {
        var thread = Thread.newThread(Thread.VIRTUAL, () -> LockSupport.park());
        thread.start();
        Thread.currentThread().interrupt();
        try {
            thread.join(Duration.ofMillis(100));
            assertTrue(false);
        } catch (InterruptedException expected) {
            // okay
        } finally {
            Thread.interrupted();
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    // interrupt status before join
    public void testJoin20() throws Exception {
        TestHelper.runInVirtualThread(this::testJoin19);
    }

    // interrupted when in join
    public void testJoin21() throws Exception {
        var thread = Thread.newThread(Thread.VIRTUAL, () -> LockSupport.park());
        thread.start();
        TestHelper.scheduleInterrupt(Thread.currentThread(), 100);
        try {
            thread.join();
            assertTrue(false);
        } catch (InterruptedException expected) {
            // okay
        } finally {
            Thread.interrupted();
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    // interrupted when in join
    public void testJoin22() throws Exception {
        TestHelper.runInVirtualThread(this::testJoin17);
    }

    // interrupted when in join
    public void testJoin23() throws Exception {
        var thread = Thread.newThread(Thread.VIRTUAL, () -> LockSupport.park());
        thread.start();
        TestHelper.scheduleInterrupt(Thread.currentThread(), 100);
        try {
            thread.join(10*1000);
            assertTrue(false);
        } catch (InterruptedException expected) {
            // okay
        } finally {
            Thread.interrupted();
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    // interrupted when in join
    public void testJoin24() throws Exception {
        TestHelper.runInVirtualThread(this::testJoin23);
    }

    // interrupted when in join
    public void testJoin25() throws Exception {
        var thread = Thread.newThread(Thread.VIRTUAL, () -> LockSupport.park());
        thread.start();
        TestHelper.scheduleInterrupt(Thread.currentThread(), 100);
        try {
            thread.join(Duration.ofSeconds(10));
            assertTrue(false);
        } catch (InterruptedException expected) {
            // okay
        } finally {
            Thread.interrupted();
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    // interrupted when in join
    public void testJoin26() throws Exception {
        TestHelper.runInVirtualThread(this::testJoin25);
    }

    // join dinosaur thread from virtual thread
    public void testJoin27() throws Exception {
        AtomicBoolean done = new AtomicBoolean();
        TestHelper.runInVirtualThread(() -> {
            var thread = new Thread(() -> {
                while (!done.get()) {
                    LockSupport.park();
                }
            });
            thread.start();
            try {
                assertFalse(thread.join(Duration.ofMillis(-100)));
                assertFalse(thread.join(Duration.ofMillis(0)));
                assertFalse(thread.join(Duration.ofMillis(100)));
            } finally {
                done.set(true);
                LockSupport.unpark(thread);
                thread.join();
            }
        });
    }

    // join dinosaur thread from virtual thread
    public void testJoin28() throws Exception {
        long nanos = TimeUnit.NANOSECONDS.convert(2, TimeUnit.SECONDS);
        TestHelper.runInVirtualThread(() -> {
            var thread = new Thread(() -> LockSupport.parkNanos(nanos));
            thread.start();
            try {
                assertTrue(thread.join(Duration.ofSeconds(Integer.MAX_VALUE)));
                assertFalse(thread.isAlive());
            } finally {
                LockSupport.unpark(thread);
                thread.join();
            }
        });
    }

    // join dinosaur thread from virtual thread with interrupt status set
    public void testJoin29() throws Exception {
        TestHelper.runInVirtualThread(() -> {
            var thread = new Thread(() -> LockSupport.park());
            thread.start();
            Thread.currentThread().interrupt();
            try {
                thread.join(Duration.ofSeconds(Integer.MAX_VALUE));
                assertTrue(false);
            } catch (InterruptedException expected) {
                assertFalse(Thread.interrupted());
            } finally {
                LockSupport.unpark(thread);
                thread.join();
            }
        });
    }

    // interrupt virtual thread when in join of dinosaur thread
    public void testJoin30() throws Exception {
        TestHelper.runInVirtualThread(() -> {
            var thread = new Thread(() -> LockSupport.park());
            thread.start();
            TestHelper.scheduleInterrupt(Thread.currentThread(), 100);
            try {
                thread.join(Duration.ofSeconds(Integer.MAX_VALUE));
                assertTrue(false);
            } catch (InterruptedException expected) {
                assertFalse(Thread.interrupted());
            } finally {
                LockSupport.unpark(thread);
                thread.join();
            }
        });
    }


    // -- interrupt --

    public void testInterrupt1() throws Exception {
        TestHelper.runInVirtualThread(() -> {
            Thread me = Thread.currentThread();
            assertFalse(me.isInterrupted());
            me.interrupt();
            assertTrue(me.isInterrupted());
            Thread.interrupted();  // clear interrupt status
            assertFalse(me.isInterrupted());
            me.interrupt();
        });
    }

    // interrupt before started
    public void testInterrupt2() throws Exception {
        var thread = Thread.newThread(Thread.VIRTUAL, () -> { });
        thread.interrupt();
        assertTrue(thread.isInterrupted());
    }

    // interrupt after terminated
    public void testInterrupt3() throws Exception {
        var thread = Thread.newThread(Thread.VIRTUAL, () -> { });
        thread.start();
        thread.join();
        thread.interrupt();
        assertTrue(thread.isInterrupted());
    }

    // terminate with interrupt status set
    public void testInterrupt4() throws Exception {
        var thread = Thread.newThread(Thread.VIRTUAL, () -> {
            Thread.currentThread().interrupt();
        });
        thread.start();
        thread.join();
        assertTrue(thread.isInterrupted());
    }

    // interrupt when mounted
    public void testInterrupt5() throws Exception {
        var exception = new AtomicReference<Exception>();
        var thread = Thread.newThread(Thread.VIRTUAL, () -> {
            try {
                try (var sel = Selector.open()) {
                    sel.select();
                    assertTrue(Thread.currentThread().isInterrupted());
                }
            } catch (Exception e) {
                exception.set(e);
            }
        });
        thread.start();
        Thread.sleep(100);  // give time for thread to block
        thread.interrupt();
        thread.join();
        assertTrue(exception.get() == null);
    }

    // interrupt when mounted
    public void testInterrupt6() throws Exception {
        var exception = new AtomicReference<Exception>();
        var thread = Thread.newThread(Thread.VIRTUAL, () -> {
            try {
                try {
                    Thread.sleep(60*1000);
                    assertTrue(false);
                } catch (InterruptedException e) {
                    // interrupt status should be reset
                    assertFalse(Thread.interrupted());
                }
            } catch (Exception e) {
                exception.set(e);
            }
        });
        thread.start();
        Thread.sleep(100);  // give time for thread to block
        thread.interrupt();
        thread.join();
        assertTrue(exception.get() == null);
    }

    // interrupt when unmounted
    public void testInterrupt7() throws Exception {
        var exception = new AtomicReference<Exception>();
        var thread = Thread.newThread(Thread.VIRTUAL, () -> {
            try {
                LockSupport.park();
                assertTrue(Thread.currentThread().isInterrupted());
            } catch (Exception e) {
                exception.set(e);
            }
        });
        thread.start();
        Thread.sleep(100);  // give time for thread to block
        thread.interrupt();
        thread.join();
        assertTrue(exception.get() == null);
    }

    // try to block with interrupt status set
    public void testInterrupt8() throws Exception {
        TestHelper.runInVirtualThread(() -> {
            Thread me = Thread.currentThread();
            me.interrupt();
            LockSupport.park();
            assertTrue(Thread.interrupted());
        });

        TestHelper.runInVirtualThread(() -> {
            Thread me = Thread.currentThread();
            me.interrupt();
            Object lock = new Object();
            synchronized (lock) {
                try {
                    lock.wait();
                    assertTrue(false);
                } catch (InterruptedException expected) {
                    assertFalse(Thread.interrupted());
                }
            }
        });

        TestHelper.runInVirtualThread(() -> {
            Thread me = Thread.currentThread();
            me.interrupt();
            try (Selector sel = Selector.open()) {
                sel.select();
                assertTrue(Thread.interrupted());
            }
        });
    }


    // -- setName/getName --

    // create without a name
    public void testSetName1() throws Exception {
        // initially unnamed
        TestHelper.runInVirtualThread(() -> {
            Thread me = Thread.currentThread();
            assertEquals(me.getName(), "<unnamed>");
            me.setName("fred");
            assertEquals(me.getName(), "fred");
        });
    }

    // create without a name
    public void testSetName2() throws Exception {
        var thread = Thread.newThread(Thread.VIRTUAL, () -> {
            LockSupport.park();
        });
        thread.start();
        try {
            assertEquals(thread.getName(), "<unnamed>");
            thread.setName("fred");
            assertEquals(thread.getName(), "fred");
        } finally {
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    // create with a name
    public void testSetName3() throws Exception {
        TestHelper.runInVirtualThread("fred", 0, () -> {
            Thread me = Thread.currentThread();
            assertEquals(me.getName(), "fred");
            me.setName("joe");
            assertEquals(me.getName(), "joe");
        });
    }

    // create with a name
    public void testSetName4() throws Exception {
        var thread = Thread.newThread("fred", 0, () -> {
            LockSupport.park();
        });
        thread.start();
        try {
            assertEquals(thread.getName(), "fred");
            thread.setName("joe");
            assertEquals(thread.getName(), "joe");
        } finally {
            LockSupport.unpark(thread);
            thread.join();
        }
    }


    // -- setPriority/getPriority --

    public void testSetPriority1() throws Exception {
        TestHelper.runInVirtualThread(() -> {
            Thread me = Thread.currentThread();
            assertTrue(me.getPriority() == Thread.NORM_PRIORITY);
            me.setPriority(Thread.MIN_PRIORITY);
            assertTrue(me.getPriority() == Thread.NORM_PRIORITY);
        });
    }

    public void testSetPriority2() throws Exception {
        var thread = Thread.newThread(Thread.VIRTUAL, () -> {
            LockSupport.park();
        });
        thread.start();
        try {
            assertTrue(thread.getPriority() == Thread.NORM_PRIORITY);
            thread.setPriority(Thread.MIN_PRIORITY);
            assertTrue(thread.getPriority() == Thread.NORM_PRIORITY);
        } finally {
            LockSupport.unpark(thread);
            thread.join();
        }
    }


    // -- setDaemon/isDaemon --

    public void testIsDaemon1() throws Exception {
        TestHelper.runInVirtualThread(() -> {
            Thread me = Thread.currentThread();
            assertTrue(me.isDaemon());
        });
    }

    public void testSetDaemon1() {
        var thread = Thread.newThread(Thread.VIRTUAL, () -> { });
        assertTrue(thread.isDaemon());
        thread.setDaemon(false);
        assertTrue(thread.isDaemon());
    }

    public void testSetDaemon2() throws Exception {
        var thread = Thread.newThread(Thread.VIRTUAL, () -> LockSupport.park());
        thread.start();
        try {
            assertTrue(thread.isDaemon());
            assertThrows(IllegalThreadStateException.class, () -> thread.setDaemon(true));
        } finally {
            LockSupport.unpark(thread);
            thread.join();
        }
    }


    // -- Thread.yield --

    public void testYield1() throws Exception {
        var list = new CopyOnWriteArrayList<String>();
        try (ExecutorService scheduler = Executors.newFixedThreadPool(1)) {
            ThreadFactory factory = Thread.builder().virtual(scheduler).factory();
            var thread = factory.newThread(() -> {
                list.add("A");
                var child = factory.newThread(() -> {
                    list.add("B");
                    Thread.yield();
                    list.add("B");
                });
                child.start();
                Thread.yield();
                list.add("A");
                try { child.join(); } catch (InterruptedException e) { }
            });
            thread.start();
            thread.join();
        }
        assertEquals(list, List.of("A", "B", "A", "B"));
    }

    public void testYield2() throws Exception {
        var list = new CopyOnWriteArrayList<String>();
        try (ExecutorService scheduler = Executors.newFixedThreadPool(1)) {
            ThreadFactory factory = Thread.builder().virtual(scheduler).factory();
            var thread = factory.newThread(() -> {
                list.add("A");
                var child = factory.newThread(() -> {
                    list.add("B");
                });
                child.start();
                Object lock = new Object();
                synchronized (lock) {
                    Thread.yield();   // pinned so will be a no-op
                    list.add("A");
                }
                try { child.join(); } catch (InterruptedException e) { }
            });
            thread.start();
            thread.join();
        }
        assertEquals(list, List.of("A", "A", "B"));
    }

    // -- Thread.onSpinWait --

    public void testOnSpinWait() throws Exception {
        TestHelper.runInVirtualThread(() -> {
            Thread me = Thread.currentThread();
            Thread.onSpinWait();
            assertTrue(Thread.currentThread() == me);
        });
    }


    // -- Thread.sleep --

    // Thread.sleep(-1)
    public void testSleep1() throws Exception {
        TestHelper.runInVirtualThread(() -> {
            try {
                Thread.sleep(-1);
                assertTrue(false);
            } catch (IllegalArgumentException e) {
                // expected
            }
        });
        TestHelper.runInVirtualThread(() -> Thread.sleep(Duration.ofMillis(-1)));
    }

    // Thread.sleep(0)
    public void testSleep2() throws Exception {
        TestHelper.runInVirtualThread(() -> Thread.sleep(0));
        TestHelper.runInVirtualThread(() -> Thread.sleep(Duration.ofMillis(0)));
    }

    // Thread.sleep(2000)
    public void testSleep3() throws Exception {
        TestHelper.runInVirtualThread(() -> {
            long start = System.currentTimeMillis();
            Thread.sleep(2000);
            long elapsed = System.currentTimeMillis() - start;
            assertTrue(elapsed > 1900);
        });
        TestHelper.runInVirtualThread(() -> {
            long start = System.currentTimeMillis();
            Thread.sleep(Duration.ofMillis(2000));
            long elapsed = System.currentTimeMillis() - start;
            assertTrue(elapsed > 1900);
        });
    }

    // Thread.sleep with interrupt status set
    public void testSleep4() throws Exception {
        TestHelper.runInVirtualThread(() -> {
            Thread me = Thread.currentThread();
            me.interrupt();
            try {
                Thread.sleep(0);
                assertTrue(false);
            } catch (InterruptedException e) {
                // expected
                assertFalse(me.isInterrupted());
            }
        });

        TestHelper.runInVirtualThread(() -> {
            Thread me = Thread.currentThread();
            me.interrupt();
            try {
                Thread.sleep(1000);
                assertTrue(false);
            } catch (InterruptedException e) {
                // expected
                assertFalse(me.isInterrupted());
            }
        });

        TestHelper.runInVirtualThread(() -> {
            Thread me = Thread.currentThread();
            me.interrupt();
            Thread.sleep(Duration.ofMillis(-1000));  // does nothing
            assertTrue(me.isInterrupted());
        });

        TestHelper.runInVirtualThread(() -> {
            Thread me = Thread.currentThread();
            me.interrupt();
            try {
                Thread.sleep(Duration.ofMillis(0));
                assertTrue(false);
            } catch (InterruptedException e) {
                // expected
                assertFalse(me.isInterrupted());
            }
        });

        TestHelper.runInVirtualThread(() -> {
            Thread me = Thread.currentThread();
            me.interrupt();
            try {
                Thread.sleep(Duration.ofMillis(1000));
                assertTrue(false);
            } catch (InterruptedException e) {
                // expected
                assertFalse(me.isInterrupted());
            }
        });
    }

    // Thread.sleep interrupted while sleeping
    public void testSleep5() throws Exception {
        TestHelper.runInVirtualThread(() -> {
            Thread t = Thread.currentThread();
            TestHelper.scheduleInterrupt(t, 2000);
            try {
                Thread.sleep(20*1000);
                assertTrue(false);
            } catch (InterruptedException e) {
                // interrupt status should be clearer
                assertFalse(t.isInterrupted());
            }
        });

        TestHelper.runInVirtualThread(() -> {
            Thread t = Thread.currentThread();
            TestHelper.scheduleInterrupt(t, 2000);
            try {
                Thread.sleep(Duration.ofSeconds(20));
                assertTrue(false);
            } catch (InterruptedException e) {
                // interrupt status should be clearer
                assertFalse(t.isInterrupted());
            }
        });
    }

    // Thread.sleep should not be disrupted by unparking virtual thread
    public void testSleep6() throws Exception {
        AtomicReference<Exception> exc = new AtomicReference<>();
        var thread = Thread.newThread(Thread.VIRTUAL, () -> {
            long start = System.currentTimeMillis();
            try {
                Thread.sleep(2000);
                long elapsed = System.currentTimeMillis() - start;
                if (elapsed < 1900) {
                    exc.set(new RuntimeException("sleep too short"));
                }
            } catch (InterruptedException e) {
                exc.set(e);
            }

        });
        thread.start();
        // attempt to disrupt sleep
        for (int i=0; i<5; i++) {
            Thread.sleep(20);
            LockSupport.unpark(thread);
        }
        thread.join();
        Exception e = exc.get();
        if (e != null) {
            throw e;
        }
    }


    // -- Thread.set/getContextClassLoader --

    public void testContextClassLoader1() throws Exception {
        ClassLoader loader = new ClassLoader() { };
        TestHelper.runInVirtualThread(() -> {
            Thread t = Thread.currentThread();
            t.setContextClassLoader(loader);
            assertTrue(t.getContextClassLoader() == loader);
        });
    }

    // inherit context class loader from creating thread
    public void testContextClassLoader2() throws Exception {
        ClassLoader loader = new ClassLoader() { };
        Thread t = Thread.currentThread();
        ClassLoader savedLoader = t.getContextClassLoader();
        t.setContextClassLoader(loader);
        try {
            TestHelper.runInVirtualThread(() -> {
                assertTrue(Thread.currentThread().getContextClassLoader() == loader);
            });
        } finally {
            t.setContextClassLoader(savedLoader);
        }
    }

    // inherit context class loader from creating virtual thread
    public void testContextClassLoader3() throws Exception {
        TestHelper.runInVirtualThread(() -> {
            ClassLoader loader = new ClassLoader() { };
            Thread.currentThread().setContextClassLoader(loader);
            TestHelper.runInVirtualThread(() -> {
                assertTrue(Thread.currentThread().getContextClassLoader() == loader);
            });
        });
    }

    // inherit context class loader from creating virtual thread
    public void testContextClassLoader4() throws Exception {
        ClassLoader loader = new ClassLoader() { };
        Thread t = Thread.currentThread();
        ClassLoader savedLoader = t.getContextClassLoader();
        t.setContextClassLoader(loader);
        try {
            TestHelper.runInVirtualThread(() -> {
                TestHelper.runInVirtualThread(() -> {
                    assertTrue(Thread.currentThread().getContextClassLoader() == loader);
                });
            });
        } finally {
            t.setContextClassLoader(savedLoader);
        }
    }


    // -- Thread.UncaughtExceptionHandler --

    // per thread UncaughtExceptionHandler
    public void testUncaughtExceptionHandler1() throws Exception {
        class FooException extends RuntimeException { }
        var exception = new AtomicReference<Throwable>();
        Thread.UncaughtExceptionHandler handler = (thread, exc) -> exception.set(exc);
        Thread thread = Thread.newThread(Thread.VIRTUAL, () -> {
            Thread me = Thread.currentThread();
            assertTrue(me.getUncaughtExceptionHandler() == me.getThreadGroup());
            me.setUncaughtExceptionHandler(handler);
            assertTrue(me.getUncaughtExceptionHandler() == handler);
            throw new FooException();
        });
        thread.start();
        thread.join();
        assertTrue(exception.get() instanceof FooException);
    }

    // default tUncaughtExceptionHandler
    public void testUncaughtExceptionHandler2() throws Exception {
        class FooException extends RuntimeException { }
        var exception = new AtomicReference<Throwable>();
        Thread.UncaughtExceptionHandler handler = (thread, exc) -> exception.set(exc);
        Thread.UncaughtExceptionHandler savedHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(handler);
        try {
            Thread thread = Thread.newThread(Thread.VIRTUAL, () -> {
                Thread me = Thread.currentThread();
                throw new FooException();
            });
            thread.start();
            thread.join();
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(savedHandler);
        }
        assertTrue(exception.get() instanceof FooException);
    }


    // -- Thread.getId --

    public void testGetId() throws Exception {
        var ref1 = new AtomicReference<Long>();
        var ref2 = new AtomicReference<Long>();
        TestHelper.runInVirtualThread(() -> ref1.set(Thread.currentThread().getId()));
        TestHelper.runInVirtualThread(() -> ref2.set(Thread.currentThread().getId()));
        long id1 = ref1.get();
        long id2 = ref2.get();
        long id3 = Thread.currentThread().getId();
        assertTrue(id1 != id2);
        assertTrue(id1 != id3);
        assertTrue(id2 != id3);
    }


    // -- Thread.getState --

    // NEW
    public void testGetState1() {
        var thread = Thread.newThread(Thread.VIRTUAL, () -> { });
        assertTrue(thread.getState() == Thread.State.NEW);
    }

    // RUNNABLE (mounted)
    public void testGetState2() throws Exception {
        TestHelper.runInVirtualThread(() -> {
            Thread.State state = Thread.currentThread().getState();
            assertTrue(state == Thread.State.RUNNABLE);
        });
    }

    // RUNNABLE (not mounted)
    public void testGetState3() throws Exception {
        AtomicBoolean completed = new AtomicBoolean();
        try (ExecutorService scheduler = Executors.newFixedThreadPool(1)) {
            Thread.Builder builder = Thread.builder().virtual(scheduler);
            Thread t1 = builder.task(() -> {
                Thread t2 = builder.task(LockSupport::park).build();
                assertTrue(t2.getState() == Thread.State.NEW);

                // runnable (not mounted)
                t2.start();
                assertTrue(t2.getState() == Thread.State.RUNNABLE);

                // yield to allow t2 to run and park
                Thread.yield();
                assertTrue(t2.getState() == Thread.State.WAITING);

                // unpark t2 and check runnable (not mounted)
                LockSupport.unpark(t2);
                assertTrue(t2.getState() == Thread.State.RUNNABLE);

                completed.set(true);
            }).start();
            t1.join();
        }
        assertTrue(completed.get() == true);
    }

    // WAITING when parked
    public void testGetState4() throws Exception {
        var thread = Thread.newThread(Thread.VIRTUAL, () -> LockSupport.park());
        thread.start();
        while (thread.getState() != Thread.State.WAITING) {
            Thread.sleep(20);
        }
        LockSupport.unpark(thread);
        thread.join();
    }

    // WAITING when parked and pinned
    public void testGetState5() throws Exception {
        var thread = Thread.newThread(Thread.VIRTUAL, () -> {
            var lock = new Object();
            synchronized (lock) {
                LockSupport.park();
            }
        });
        thread.start();
        while (thread.getState() != Thread.State.WAITING) {
            Thread.sleep(20);
        }
        LockSupport.unpark(thread);
        thread.join();
    }

    // WAITING when blocked in Object.wait
    public void testGetState6() throws Exception {
        var thread = Thread.newThread(Thread.VIRTUAL, () -> {
            var lock = new Object();
            synchronized (lock) {
                try { lock.wait(); } catch (InterruptedException e) { }
            }
        });
        thread.start();
        while (thread.getState() != Thread.State.WAITING) {
            Thread.sleep(20);
        }
        thread.interrupt();
        thread.join();
    }

    // TERMINATED
    public void testGetState7() throws Exception {
        var thread = Thread.newThread(Thread.VIRTUAL, () -> { });
        thread.start();
        thread.join();
        assertTrue(thread.getState() == Thread.State.TERMINATED);
    }


    // -- isAlive --

    public void testIsAlive1() throws Exception {
        var thread = Thread.newThread(Thread.VIRTUAL, () -> LockSupport.park());
        assertFalse(thread.isAlive());
        thread.start();
        try {
            assertTrue(thread.isAlive());
        } finally {
            LockSupport.unpark(thread);
            thread.join();
        }
        assertFalse(thread.isAlive());
    }


    // -- Thread.holdsLock --

    public void testHoldsLock1() throws Exception {
        TestHelper.runInVirtualThread(() -> {
            var lock = new Object();
            assertFalse(Thread.holdsLock(lock));
        });
    }

    public void testHoldsLock2() throws Exception {
        TestHelper.runInVirtualThread(() -> {
            var lock = new Object();
            synchronized (lock) {
                assertTrue(Thread.holdsLock(lock));
            }
        });
    }


    // -- Thread.getStackTrace --

    // runnable (mounted)
    public void testGetStackTrace1() throws Exception {
        var sel = Selector.open();
        var thread = Thread.newThread(Thread.VIRTUAL, () -> {
            try { sel.select(); } catch (Exception e) { }
        });
        thread.start();
        Thread.sleep(200);  // give him for thread to block
        try {
            assertTrue(thread.getState() == Thread.State.RUNNABLE);
            StackTraceElement[] stack = thread.getStackTrace();
            assertTrue(contains(stack, "select"));
        } finally {
            sel.close();
            thread.join();
        }
    }

    // waiting (mounted)
    public void testGetStackTrace2() throws Exception {
        var lock = new Object();
        var thread = Thread.newThread(Thread.VIRTUAL, () -> {
            synchronized (lock) {
                try { lock.wait(); } catch (InterruptedException e) { }
            }
        });
        thread.start();

        // wait for carrier thread to block
        while (thread.getState() != Thread.State.WAITING) {
            Thread.sleep(20);
        }

        try {
            StackTraceElement[] stack = thread.getStackTrace();
            assertTrue(contains(stack, "Object.wait"));
        } finally {
            synchronized (lock) {
                lock.notifyAll();
            }
        }
    }

    // parked (unmounted)
    public void testGetStackTrace3() throws Exception {
        var thread = Thread.newThread(Thread.VIRTUAL, () -> LockSupport.park());
        thread.start();

        // wait for thread to park
        while (thread.getState() != Thread.State.WAITING) {
            Thread.sleep(20);
        }

        try {
            StackTraceElement[] stack = thread.getStackTrace();
            assertTrue(contains(stack, "LockSupport.park"));
        } finally {
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    // not started
    public void testGetStackTrace4() {
        var thread = Thread.newThread(Thread.VIRTUAL, () -> { });
        StackTraceElement[] stack = thread.getStackTrace();
        assertTrue(stack.length == 0);
    }

    // terminated
    public void testGetStackTrace5() throws Exception {
        var thread = Thread.newThread(Thread.VIRTUAL, () -> { });
        thread.start();
        thread.join();
        StackTraceElement[] stack = thread.getStackTrace();
        assertTrue(stack.length == 0);
    }

    private boolean contains(StackTraceElement[] stack, String expected) {
        return Stream.of(stack)
                .map(Object::toString)
                .anyMatch(s -> s.contains(expected));
    }


    // -- Thread.getAllStackTraces --

    public void testGetAllStackTraces() throws Exception {
        TestHelper.runInVirtualThread(() -> {
            Set<Thread> threads = Thread.getAllStackTraces().keySet();
            assertFalse(threads.stream().anyMatch(Thread::isVirtual));
        });
    }


    // -- ThreadGroup --

    public void testThreadGroup1() throws Exception {
        var thread = Thread.newThread(Thread.VIRTUAL, () -> LockSupport.park());
        var group = thread.getThreadGroup();
        assertTrue(group != null);
        thread.start();
        try {
            assertTrue(thread.getThreadGroup() == group);

            // no "active threads"
            Thread[] threads = new Thread[100];
            assertTrue(group.enumerate(threads) == 0);
            assertTrue(group.activeGroupCount() == 0);

            assertTrue(group.allowThreadSuspension(true) == false);
        } finally {
            LockSupport.unpark(thread);
            thread.join();
        }
        assertTrue(thread.getThreadGroup() == null);
    }

    public void testActiveCount1() throws Exception {
        TestHelper.runInVirtualThread(() -> {
            assertTrue(Thread.activeCount() == 0);
        });
    }

    // Thread.enumerate should not enumerate virtual threads
    public void testEnumerate1() throws Exception {
        TestHelper.runInVirtualThread(() -> {
            Thread[] threads = new Thread[100];
            int n = Thread.enumerate(threads);
            assertFalse(Arrays.stream(threads, 0, n).anyMatch(Thread::isVirtual));
        });
    }


    // -- toString --

    // not started
    public void testToString1() {
        Thread thread = Thread.newThread(Thread.VIRTUAL, () -> { });
        thread.setName("fred");
        assertTrue(thread.toString().contains("fred"));
    }

    // mounted
    public void testToString2() throws Exception {
        TestHelper.runInVirtualThread(() -> {
            Thread me = Thread.currentThread();
            me.setName("fred");
            assertTrue(me.toString().contains("fred"));
        });
    }

    // unmounted
    public void testToString3() throws Exception {
        Thread thread = Thread.newThread(Thread.VIRTUAL, () -> {
            Thread me = Thread.currentThread();
            me.setName("fred");
            LockSupport.park();
        });
        thread.start();
        while (thread.getState() != Thread.State.WAITING) {
            Thread.sleep(10);
        }
        try {
            assertTrue(thread.toString().contains("fred"));
        } finally {
            LockSupport.unpark(thread);
            thread.join();
        }
    }

    // terminated
    public void testToString4() throws Exception {
        Thread thread = Thread.newThread(Thread.VIRTUAL, () -> {
            Thread me = Thread.currentThread();
            me.setName("fred");
        });
        thread.start();
        thread.join();
        assertTrue(thread.toString().contains("fred"));
    }

}
