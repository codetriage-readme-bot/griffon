/*
 * Copyright 2008-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.griffon.runtime.core.event

import com.google.guiceberry.GuiceBerryModule
import com.google.guiceberry.junit4.GuiceBerryRule
import com.google.inject.AbstractModule
import griffon.core.ExceptionHandler
import griffon.core.ExecutorServiceManager
import griffon.core.RunnableWithArgs
import griffon.core.event.Event
import griffon.core.event.EventPublisher
import griffon.core.event.EventRouter
import griffon.core.threading.UIThreadManager
import griffon.util.AnnotationUtils
import org.codehaus.griffon.runtime.core.DefaultExecutorServiceManager
import org.codehaus.griffon.runtime.core.ExceptionHandlerProvider
import org.codehaus.griffon.runtime.core.threading.DefaultExecutorServiceProvider
import org.codehaus.griffon.runtime.core.threading.UIThreadManagerTestSupport
import org.junit.Rule
import spock.lang.Specification

import javax.annotation.Nullable
import javax.inject.Inject
import java.util.concurrent.ExecutorService

class DefaultEventPublisherSpec extends Specification {
    @Rule
    final GuiceBerryRule guiceBerry = new GuiceBerryRule(TestModule)

    @Inject
    private EventPublisher eventPublisher

    def 'Invoking an event by name in synchronous mode with a runnable listener'() {
        given:

        String eventName1 = MyEvent1.simpleName
        String eventName2 = MyEvent2.simpleName
        TestRunnableEventHandler eventHandler = new TestRunnableEventHandler()
        eventPublisher.addEventListener(eventName1, eventHandler)

        when:

        eventPublisher.publishEvent(eventName1, [1, 'one'])
        eventPublisher.publishEvent(eventName2, [2, 'two'])

        then:

        eventHandler.args == [1, 'one']
    }

    def 'Invoking an event by name in asynchronous mode with a runnable listener'() {
        given:

        String eventName1 = MyEvent1.simpleName
        String eventName2 = MyEvent2.simpleName
        TestRunnableEventHandler eventHandler = new TestRunnableEventHandler()
        eventPublisher.addEventListener(eventName1, eventHandler)

        when:

        eventPublisher.publishEventAsync(eventName1, [1, 'one'])
        eventPublisher.publishEventAsync(eventName2, [2, 'two'])
        Thread.sleep(200L)

        then:

        eventHandler.args == [1, 'one']
    }

    def 'Invoking an event by name in outside mode with a runnable listener'() {
        given:

        String eventName1 = MyEvent1.simpleName
        String eventName2 = MyEvent2.simpleName
        TestRunnableEventHandler eventHandler = new TestRunnableEventHandler()
        eventPublisher.addEventListener(eventName1, eventHandler)

        when:

        eventPublisher.publishEventOutsideUI(eventName1, [1, 'one'])
        eventPublisher.publishEventOutsideUI(eventName2, [2, 'two'])

        then:

        eventHandler.args == [1, 'one']
    }

    def 'Invoking an event by name in synchronous mode with a Map listener (runnable)'() {
        given:

        String eventName1 = MyEvent1.simpleName
        String eventName2 = MyEvent2.simpleName
        TestRunnableEventHandler eventHandler = new TestRunnableEventHandler()
        eventPublisher.addEventListener([(eventName1): eventHandler])

        when:

        eventPublisher.publishEvent(eventName1, [1, 'one'])
        eventPublisher.publishEvent(eventName2, [2, 'two'])

        then:

        eventHandler.args == [1, 'one']
    }

    def 'Invoking an event by name in asynchronous mode with a Map listener (runnable)'() {
        given:

        String eventName1 = MyEvent1.simpleName
        String eventName2 = MyEvent2.simpleName
        TestRunnableEventHandler eventHandler = new TestRunnableEventHandler()
        eventPublisher.addEventListener([(eventName1): eventHandler])

        when:

        eventPublisher.publishEventAsync(eventName1, [1, 'one'])
        eventPublisher.publishEventAsync(eventName2, [2, 'two'])
        Thread.sleep(200L)

        then:

        eventHandler.args == [1, 'one']
    }

    def 'Invoking an event by name in outside mode with a Map listener (runnable)'() {
        given:

        String eventName1 = MyEvent1.simpleName
        String eventName2 = MyEvent2.simpleName
        TestRunnableEventHandler eventHandler = new TestRunnableEventHandler()
        eventPublisher.addEventListener([(eventName1): eventHandler])

        when:

        eventPublisher.publishEventOutsideUI(eventName1, [1, 'one'])
        eventPublisher.publishEventOutsideUI(eventName2, [2, 'two'])

        then:

        eventHandler.args == [1, 'one']
    }

    def 'Invoking an event by name in synchronous mode with a bean listener'() {
        given:

        String eventName1 = MyEvent1.simpleName
        String eventName2 = MyEvent2.simpleName
        EventHandler eventHandler = new EventHandler()
        eventPublisher.addEventListener(eventHandler)

        when:

        eventPublisher.publishEvent(eventName1, [1, 'one'])
        eventPublisher.publishEvent(eventName2, [2, 'two'])

        then:

        eventHandler.args == [1, 'one']
    }

    def 'Invoking an event by name in asynchronous mode with a bean listener'() {
        given:

        String eventName1 = MyEvent1.simpleName
        String eventName2 = MyEvent2.simpleName
        EventHandler eventHandler = new EventHandler()
        eventPublisher.addEventListener(eventHandler)

        when:

        eventPublisher.publishEventAsync(eventName1, [1, 'one'])
        eventPublisher.publishEventAsync(eventName2, [2, 'two'])
        Thread.sleep(200L)

        then:

        eventHandler.args == [1, 'one']
    }

    def 'Invoking an event by name in outside mode with a bean listener'() {
        given:

        String eventName1 = MyEvent1.simpleName
        String eventName2 = MyEvent2.simpleName
        EventHandler eventHandler = new EventHandler()
        eventPublisher.addEventListener(eventHandler)

        when:

        eventPublisher.publishEventOutsideUI(eventName1, [1, 'one'])
        eventPublisher.publishEventOutsideUI(eventName2, [2, 'two'])

        then:

        eventHandler.args == [1, 'one']
    }

    def 'Invoking an event in synchronous mode with a runnable listener'() {
        given:

        Event event1 = new MyEvent1(new Object())
        Event event2 = new MyEvent2(new Object())
        TestRunnableEventHandler eventHandler = new TestRunnableEventHandler()
        eventPublisher.addEventListener(event1.class, eventHandler)

        when:

        eventPublisher.publishEvent(event1)
        eventPublisher.publishEvent(event2)

        then:

        eventHandler.args == [event1]
    }

    def 'Invoking an event in asynchronous mode with a runnable listener'() {
        given:

        Event event1 = new MyEvent1(new Object())
        Event event2 = new MyEvent2(new Object())
        TestRunnableEventHandler eventHandler = new TestRunnableEventHandler()
        eventPublisher.addEventListener(event1.class, eventHandler)

        when:

        eventPublisher.publishEventAsync(event1)
        eventPublisher.publishEventAsync(event2)
        Thread.sleep(200L)

        then:

        eventHandler.args == [event1]
    }

    def 'Invoking an event in outside mode with a runnable listener'() {
        given:

        Event event1 = new MyEvent1(new Object())
        Event event2 = new MyEvent2(new Object())
        TestRunnableEventHandler eventHandler = new TestRunnableEventHandler()
        eventPublisher.addEventListener(event1.class, eventHandler)

        when:

        eventPublisher.publishEventOutsideUI(event1)
        eventPublisher.publishEventOutsideUI(event2)

        then:

        eventHandler.args == [event1]
    }

    def 'Invoking an event in synchronous mode with a Map listener (runnable)'() {
        given:

        Event event1 = new MyEvent1(new Object())
        Event event2 = new MyEvent2(new Object())
        TestRunnableEventHandler eventHandler = new TestRunnableEventHandler()
        eventPublisher.addEventListener(event1.class, eventHandler)

        when:

        eventPublisher.publishEvent(event1)
        eventPublisher.publishEvent(event2)

        then:

        eventHandler.args == [event1]
    }

    def 'Invoking an event in asynchronous mode with a Map listener (runnable)'() {
        given:

        Event event1 = new MyEvent1(new Object())
        Event event2 = new MyEvent2(new Object())
        TestRunnableEventHandler eventHandler = new TestRunnableEventHandler()
        eventPublisher.addEventListener(event1.class, eventHandler)

        when:

        eventPublisher.publishEventAsync(event1)
        eventPublisher.publishEventAsync(event2)
        Thread.sleep(200L)

        then:

        eventHandler.args == [event1]
    }

    def 'Invoking an event in outside mode with a Map listener (runnable)'() {
        given:

        Event event1 = new MyEvent1(new Object())
        Event event2 = new MyEvent2(new Object())
        TestRunnableEventHandler eventHandler = new TestRunnableEventHandler()
        eventPublisher.addEventListener(event1.class, eventHandler)

        when:

        eventPublisher.publishEventOutsideUI(event1)
        eventPublisher.publishEventOutsideUI(event2)

        then:

        eventHandler.args == [event1]
    }

    def 'Register and unregister a runnable listener by name'() {
        given:

        String eventName1 = MyEvent1.simpleName
        String eventName2 = MyEvent2.simpleName
        TestRunnableEventHandler eventHandler = new TestRunnableEventHandler()
        eventPublisher.addEventListener(eventName1, eventHandler)
        eventPublisher.removeEventListener(eventName1, eventHandler)

        when:

        eventPublisher.publishEvent(eventName1, [1, 'one'])
        eventPublisher.publishEvent(eventName2, [2, 'two'])

        then:

        !eventHandler.args
    }

    def 'Register and unregister a Map listener by name (runnable)'() {
        given:

        String eventName1 = MyEvent1.simpleName
        String eventName2 = MyEvent2.simpleName
        TestRunnableEventHandler eventHandler = new TestRunnableEventHandler()
        eventPublisher.addEventListener([(eventName1): eventHandler])
        eventPublisher.removeEventListener([(eventName1): eventHandler])

        when:

        eventPublisher.publishEvent(eventName1, [1, 'one'])
        eventPublisher.publishEvent(eventName2, [2, 'two'])

        then:

        !eventHandler.args
    }

    def 'Register and unregister a runnable listener'() {
        given:

        String eventName1 = MyEvent1.simpleName
        String eventName2 = MyEvent2.simpleName
        TestRunnableEventHandler eventHandler = new TestRunnableEventHandler()
        eventPublisher.addEventListener(MyEvent1, eventHandler)
        eventPublisher.removeEventListener(MyEvent1, eventHandler)

        when:

        eventPublisher.publishEvent(eventName1, [1, 'one'])
        eventPublisher.publishEvent(eventName2, [2, 'two'])

        then:

        !eventHandler.args
    }

    def 'Register and unregister a bean listener'() {
        given:

        String eventName1 = MyEvent1.simpleName
        String eventName2 = MyEvent2.simpleName
        EventHandler eventHandler = new EventHandler()
        eventPublisher.addEventListener(eventHandler)
        eventPublisher.removeEventListener(eventHandler)

        when:

        eventPublisher.publishEvent(eventName1)
        eventPublisher.publishEvent(eventName2)

        then:

        !eventHandler.args
    }

    def 'Register and unregister a bean listener with nested listeners'() {
        given:

        String eventName1 = MyEvent1.simpleName
        String eventName2 = MyEvent2.simpleName
        Subject subject = new Subject()
        eventPublisher.addEventListener(subject)
        eventPublisher.addEventListener(subject.events)
        eventPublisher.removeEventListener(subject)

        when:

        eventPublisher.publishEvent(eventName1, [1, 'one'])
        eventPublisher.publishEvent(eventName2, [2, 'two'])

        then:

        !subject.args
    }

    def 'Register and unregister an invalid bean listener'() {
        given:

        def eventHandler = new Object()

        expect:

        eventPublisher.addEventListener(eventHandler)
        eventPublisher.removeEventListener(eventHandler)
    }

    def 'Triggering an event with event published disabled does not notify listener'() {
        given:

        String eventName1 = MyEvent1.simpleName
        TestRunnableEventHandler eventHandler = new TestRunnableEventHandler()

        when:

        eventPublisher.eventPublishingEnabled = false
        eventPublisher.publishEvent(eventName1, [1, 'one'])

        then:

        !eventHandler.args
        eventHandler.called == 0
        eventPublisher.eventPublishingEnabled == false
    }

    def 'Query existing listeners by event name'() {
        given:

        String eventName1 = MyEvent1.simpleName
        String eventName2 = MyEvent2.simpleName
        TestRunnableEventHandler eventHandler1 = new TestRunnableEventHandler()
        TestRunnableEventHandler eventHandler2 = new TestRunnableEventHandler()

        expect:

        !eventPublisher.eventListeners

        when:

        eventPublisher.addEventListener(eventName1, eventHandler1)
        eventPublisher.addEventListener([(eventName2): eventHandler2])
        eventPublisher.addEventListener(new EventHandler())
        eventPublisher.addEventListener(new Subject().events)

        then:

        eventPublisher.eventListeners.size() == 5
        eventPublisher.getEventListeners(eventName1).size() == 3
        eventPublisher.getEventListeners(eventName2).size() == 2
    }

    static final class TestModule extends AbstractModule {
        @Override
        protected void configure() {
            install(new GuiceBerryModule())
            bind(ExecutorServiceManager).to(DefaultExecutorServiceManager)
            bind(UIThreadManager).to(UIThreadManagerTestSupport)
            bind(EventRouter).to(DefaultEventRouter)
            bind(EventPublisher).to(DefaultEventPublisher)
            bind(ExceptionHandler).toProvider(ExceptionHandlerProvider)
            bind(ExecutorService).annotatedWith(AnnotationUtils.named('defaultExecutorService')).toProvider(DefaultExecutorServiceProvider)
        }
    }

    static class TestRunnableEventHandler implements RunnableWithArgs {
        int called
        Object[] args

        @Override
        void run(@Nullable Object... args) {
            called++
            this.args = args
        }
    }

    static class MyEvent1 extends Event {
        MyEvent1(Object source) {
            super(source)
        }
    }

    static class MyEvent2 extends Event {
        MyEvent2(Object source) {
            super(source)
        }
    }

    static class EventHandler {
        List args

        void onMyEvent1(int arg0, String arg1) {
            this.args = [arg0, arg1]
        }

        void onMyEvent1(MyEvent1 event) {
            this.args = [event]
        }
    }
}
