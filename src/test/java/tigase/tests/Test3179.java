package tigase.tests;

import org.apache.commons.lang3.mutable.MutableObject;
import org.testng.Assert;
import org.testng.annotations.Test;
import tigase.TestLogger;
import tigase.jaxmpp.core.client.AsyncCallback;
import tigase.jaxmpp.core.client.BareJID;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.eventbus.Event;
import tigase.jaxmpp.core.client.eventbus.EventHandler;
import tigase.jaxmpp.core.client.eventbus.EventListener;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.xml.ElementBuilder;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.forms.JabberDataElement;
import tigase.jaxmpp.core.client.xmpp.forms.ListSingleField;
import tigase.jaxmpp.core.client.xmpp.modules.muc.MucModule;
import tigase.jaxmpp.core.client.xmpp.modules.muc.Room;
import tigase.jaxmpp.core.client.xmpp.stanzas.Presence;
import tigase.jaxmpp.core.client.xmpp.stanzas.Stanza;
import tigase.jaxmpp.j2se.Jaxmpp;

public class Test3179 extends AbstractTest {


    @Test(
            groups = {"Multi User Chat"},
            description = "#3179: Implement Max Users"
    )
    public void testMaxUsers() throws Exception {
        final Mutex mutex = new Mutex();

        BareJID user1Jid = createUserAccount("user1");
        Jaxmpp user1Jaxmpp = createJaxmpp("user1", user1Jid);
        BareJID user2Jid = createUserAccount("user2");
        Jaxmpp user2Jaxmpp = createJaxmpp("user2", user2Jid);
        BareJID user3Jid = createUserAccount("user3");
        Jaxmpp user3Jaxmpp = createJaxmpp("user3", user3Jid);

        user1Jaxmpp.login(true);
        user2Jaxmpp.login(true);
        user3Jaxmpp.login(true);

        final BareJID roomJID = BareJID.bareJIDInstance("room" + nextRnd(), "muc." + user1Jid.getDomain());

        MucModule muc1Module = user1Jaxmpp.getModule(MucModule.class);
        MucModule muc2Module = user2Jaxmpp.getModule(MucModule.class);
        MucModule muc3Module = user3Jaxmpp.getModule(MucModule.class);

        user2Jaxmpp.getEventBus().addHandler(MucModule.YouJoinedHandler.YouJoinedEvent.class, new MucModule.YouJoinedHandler() {
            @Override
            public void onYouJoined(SessionObject sessionObject, Room room, String asNickname) {
                mutex.notify("2:joinAs:" + asNickname);
            }
        });
        user3Jaxmpp.getEventBus().addHandler(MucModule.YouJoinedHandler.YouJoinedEvent.class, new MucModule.YouJoinedHandler() {
            @Override
            public void onYouJoined(SessionObject sessionObject, Room room, String asNickname) {
                mutex.notify("3:joinAs:" + asNickname);
            }
        });

        final MutableObject<JabberDataElement> roomConfig = new MutableObject<JabberDataElement>();

        Room join = muc1Module.join(roomJID.getLocalpart(), roomJID.getDomain(), "user1");
        user1Jaxmpp.getEventBus().addHandler(MucModule.YouJoinedHandler.YouJoinedEvent.class, new MucModule.YouJoinedHandler() {
                    @Override
                    public void onYouJoined(SessionObject sessionObject, final Room room, final String asXNickname) {
                        mutex.notify("joinAs:user1");
                    }
                }
        );

        mutex.waitFor(1000 * 20, "joinAs:user1");

        muc1Module.getRoomConfiguration(muc1Module.getRoom(roomJID), new MucModule.RoomConfgurationAsyncCallback() {
            @Override
            public void onConfigurationReceived(JabberDataElement jabberDataElement) throws XMLException {
                roomConfig.setValue(jabberDataElement);
                try {
                    ElementBuilder b = ElementBuilder.create("iq");
                    b.setAttribute("id", nextRnd()).setAttribute("to", roomJID.toString()).setAttribute("type", "set").child(
                            "query").setXMLNS("http://jabber.org/protocol/muc#owner").child("x").setXMLNS("jabber:x:data").setAttribute(
                            "type", "submit");

                    user1Jaxmpp.send(Stanza.create(b.getElement()));
                } catch (JaxmppException e) {
                    fail(e);
                }
                mutex.notify("getConfig:success", "getConfig");
            }

            @Override
            public void onError(Stanza stanza, XMPPException.ErrorCondition errorCondition) throws JaxmppException {
                mutex.notify("getConfig:error", "getConfig");
            }


            @Override
            public void onTimeout() throws JaxmppException {
                mutex.notify("getConfig:timeout", "getConfig");
            }
        });

        mutex.waitFor(1000 * 20, "getConfig");
        Assert.assertTrue(mutex.isItemNotified("joinAs:user1"));
        Assert.assertTrue(mutex.isItemNotified("getConfig:success"));

        Thread.sleep(1000);

        joinAs(user2Jaxmpp, roomJID, "user2", "joinAs:user2");
        joinAs(user3Jaxmpp, roomJID, "user3", "joinAs:user3");
        muc2Module.leave(muc2Module.getRoom(roomJID));
        muc3Module.leave(muc3Module.getRoom(roomJID));

        Thread.sleep(1000);

        ((ListSingleField) roomConfig.getValue().getField("muc#roomconfig_maxusers")).setFieldValue("2");

        muc1Module.setRoomConfiguration(muc1Module.getRoom(roomJID), roomConfig.getValue(), new AsyncCallback() {
            @Override
            public void onError(Stanza stanza, XMPPException.ErrorCondition errorCondition) throws JaxmppException {
                TestLogger.log("Error on set config: " + errorCondition);
                mutex.notify("setConfig", "setConfig:error");
            }

            @Override
            public void onSuccess(Stanza stanza) throws JaxmppException {
                mutex.notify("setConfig", "setConfig:success");
            }

            @Override
            public void onTimeout() throws JaxmppException {
                mutex.notify("setConfig", "setConfig:timeout");
            }
        });
        mutex.waitFor(1000 * 20, "setConfig");
        Assert.assertTrue(mutex.isItemNotified("setConfig:success"));
        Thread.sleep(1000);

        joinAs(user2Jaxmpp, roomJID, "user2", "joinAs:user2");
        joinAs(user3Jaxmpp, roomJID, "user3", "notJoinAs:user3");


        removeUserAccount(user1Jaxmpp);
        removeUserAccount(user2Jaxmpp);
        removeUserAccount(user3Jaxmpp);
    }


    private void joinAs(final Jaxmpp jaxmpp, final BareJID roomJID, final String nick, String expectedEvent) throws InterruptedException {
        final Mutex mutex = new Mutex();

        final MucModule.YouJoinedHandler handlerJoined = new MucModule.YouJoinedHandler() {
            @Override
            public void onYouJoined(SessionObject sessionObject, Room room, String asNickname) {
                mutex.notify("resp", "joinAs:" + asNickname);
            }
        };
        final MucModule.PresenceErrorHandler handlerError = new MucModule.PresenceErrorHandler() {
            @Override
            public void onPresenceError(SessionObject sessionObject, Room room, Presence presence, String asNickname) {
                mutex.notify("resp", "notJoinAs:" + asNickname);
            }
        };

        final EventListener listener = new EventListener() {
            @Override
            public void onEvent(Event<? extends EventHandler> event) {
            }
        };
        try {
            jaxmpp.getEventBus().addHandler(MucModule.YouJoinedHandler.YouJoinedEvent.class, handlerJoined);
            jaxmpp.getEventBus().addHandler(MucModule.PresenceErrorHandler.PresenceErrorEvent.class, handlerError);
            jaxmpp.getEventBus().addListener(listener);
            final MucModule mucModule = jaxmpp.getModule(MucModule.class);

            mucModule.join(roomJID.getLocalpart(), roomJID.getDomain(), nick);

            mutex.waitFor(1000 * 20, "resp");

            Assert.assertTrue(mutex.isItemNotified(expectedEvent), "Expected event '" + expectedEvent + "' not received.");
        } catch (JaxmppException e) {
            fail(e);
            e.printStackTrace();
        } finally {
            jaxmpp.getEventBus().remove(listener);
            jaxmpp.getEventBus().remove(MucModule.PresenceErrorHandler.PresenceErrorEvent.class, handlerError);
            jaxmpp.getEventBus().remove(MucModule.YouJoinedHandler.YouJoinedEvent.class, handlerJoined);
        }
    }
}
