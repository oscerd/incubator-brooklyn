/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.policy.basic;

import static org.testng.Assert.assertEquals;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.BasicSensorEvent;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.management.SubscriptionHandle;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestEntity;

import com.google.common.collect.ImmutableList;

public class PolicySubscriptionTest extends BrooklynAppUnitTestSupport {

    // TODO Duplication between this and EntitySubscriptionTest
    
    private static final long SHORT_WAIT_MS = 100;
    
    private SimulatedLocation loc;
    private TestEntity entity;
    private TestEntity otherEntity;
    private AbstractPolicy policy;
    private RecordingSensorEventListener listener;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        loc = app.newSimulatedLocation();
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        otherEntity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        listener = new RecordingSensorEventListener();
        policy = new AbstractPolicy() {};
        entity.addPolicy(policy);
        app.start(ImmutableList.of(loc));
    }

    @Test
    public void testSubscriptionReceivesEvents() throws Exception {
        policy.subscribe(entity, TestEntity.SEQUENCE, listener);
        policy.subscribe(entity, TestEntity.NAME, listener);
        policy.subscribe(entity, TestEntity.MY_NOTIF, listener);
        
        otherEntity.setAttribute(TestEntity.SEQUENCE, 456);
        entity.setAttribute(TestEntity.SEQUENCE, 123);
        entity.setAttribute(TestEntity.NAME, "myname");
        entity.emit(TestEntity.MY_NOTIF, 789);
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(listener.events, ImmutableList.of(
                        new BasicSensorEvent<Integer>(TestEntity.SEQUENCE, entity, 123),
                        new BasicSensorEvent<String>(TestEntity.NAME, entity, "myname"),
                        new BasicSensorEvent<Integer>(TestEntity.MY_NOTIF, entity, 789)));
            }});
    }
    
    @Test
    public void testUnsubscribeRemovesAllSubscriptionsForThatEntity() throws Exception {
        policy.subscribe(entity, TestEntity.SEQUENCE, listener);
        policy.subscribe(entity, TestEntity.NAME, listener);
        policy.subscribe(entity, TestEntity.MY_NOTIF, listener);
        policy.subscribe(otherEntity, TestEntity.SEQUENCE, listener);
        policy.unsubscribe(entity);
        
        entity.setAttribute(TestEntity.SEQUENCE, 123);
        entity.setAttribute(TestEntity.NAME, "myname");
        entity.emit(TestEntity.MY_NOTIF, 456);
        otherEntity.setAttribute(TestEntity.SEQUENCE, 789);
        
        Thread.sleep(SHORT_WAIT_MS);
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(listener.events, ImmutableList.of(
                        new BasicSensorEvent<Integer>(TestEntity.SEQUENCE, otherEntity, 789)));
            }});
    }
    
    @Test
    public void testUnsubscribeUsingHandleStopsEvents() throws Exception {
        SubscriptionHandle handle1 = policy.subscribe(entity, TestEntity.SEQUENCE, listener);
        SubscriptionHandle handle2 = policy.subscribe(entity, TestEntity.NAME, listener);
        SubscriptionHandle handle3 = policy.subscribe(otherEntity, TestEntity.SEQUENCE, listener);
        
        policy.unsubscribe(entity, handle2);
        
        entity.setAttribute(TestEntity.SEQUENCE, 123);
        entity.setAttribute(TestEntity.NAME, "myname");
        otherEntity.setAttribute(TestEntity.SEQUENCE, 456);
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEquals(listener.events, ImmutableList.of(
                        new BasicSensorEvent<Integer>(TestEntity.SEQUENCE, entity, 123),
                        new BasicSensorEvent<Integer>(TestEntity.SEQUENCE, otherEntity, 456)));
            }});
    }
    
    private static class RecordingSensorEventListener implements SensorEventListener<Object> {
        final List<SensorEvent<?>> events = new CopyOnWriteArrayList<SensorEvent<?>>();
        
        @Override public void onEvent(SensorEvent<Object> event) {
            events.add(event);
        }
    }
}
