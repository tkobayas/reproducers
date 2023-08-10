package org.example.drools.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.example.drools.test.domain.Event;
import org.junit.Test;
import org.kie.api.KieServices;
import org.kie.api.marshalling.KieMarshallers;
import org.kie.api.marshalling.Marshaller;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.conf.ClockTypeOption;
import org.kie.api.time.SessionClock;
import org.kie.api.time.SessionPseudoClock;
import org.kie.internal.marshalling.MarshallerFactory;

import static org.junit.Assert.assertEquals;

public class SerializeAdvanceTwiceTest {

    @Test
    public void testingMarshalling() throws IOException, ClassNotFoundException, InterruptedException {
        KieServices kieServices = KieServices.Factory.get();
        KieContainer kieContainer = kieServices.getKieClasspathContainer();
        KieSessionConfiguration config = KieServices.Factory.get().newKieSessionConfiguration();
        config.setOption(ClockTypeOption.PSEUDO);

        KieSession kieSession = kieContainer.getKieBase().newKieSession(config, null);
        SessionPseudoClock clock = kieSession.getSessionClock();

        EventNotificationChannel notificationChannel = new EventNotificationChannel();
        kieSession.registerChannel("notification-channel", notificationChannel);

        String eventId = UUID.randomUUID().toString();

        Event eventCreated = new Event();
        eventCreated.setEventId(eventId);
        eventCreated.setStatus("created");

        kieSession.insert(eventCreated);

        long ruleFireCount = kieSession.fireAllRules();
        System.out.println(ruleFireCount);
        assertEquals(0, ruleFireCount);
        System.out.println("advancing time");

        clock.advanceTime(4, TimeUnit.SECONDS);
        System.out.println("Session clock -> " + kieSession.getSessionClock());
        System.out.println("Session clock -> " + new Date(kieSession.getSessionClock().getCurrentTime()));

        System.out.println("--- Serialize ---");

        byte[] sessionBytes = serializeSession(kieSession);

        System.out.println("--- Deserialize ---");

        KieSession deserializeSession = deserializeSession(sessionBytes, config, kieContainer);

        System.out.println("Session clock -> " + deserializeSession.getSessionClock());
        System.out.println("Session clock -> " + new Date(deserializeSession.getSessionClock().getCurrentTime()));

        deserializeSession.registerChannel("notification-channel", notificationChannel);

        System.out.println("firing rules again");

        // ***REPLACE*** deserializeSession with kieSession to make rule fire
        SessionPseudoClock clock2 = deserializeSession.getSessionClock();
        clock2.advanceTime(1, TimeUnit.SECONDS);
        ruleFireCount = deserializeSession.fireAllRules();

        System.out.println(ruleFireCount);
        assertEquals(2, ruleFireCount);

        Event event = notificationChannel.getClosedEvent(eventId);
        assertEquals("closed", event.getStatus());
        assertEquals("auto-closed", event.getReason());
    }

    private KieSession deserializeSession(byte[] sessionBytes, KieSessionConfiguration config, KieContainer kieContainer) throws IOException, ClassNotFoundException {
        Marshaller marshaller = MarshallerFactory.newMarshaller(kieContainer.getKieBase());
        ByteArrayInputStream bais = new ByteArrayInputStream(sessionBytes);
        return marshaller.unmarshall(bais, config, null);
    }

    private byte[] serializeSession(KieSession kieSession) throws IOException {
        KieMarshallers kieMarshallers = KieServices.get().getMarshallers();
        Marshaller marshaller = kieMarshallers.newMarshaller(kieSession.getKieBase());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        marshaller.marshall(baos, kieSession);
        return baos.toByteArray();
    }

}
