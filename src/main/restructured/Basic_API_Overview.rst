Basic API overview
===================

API Overview
--------------

All test cases should extend ``tigase.tests.AbstractTest`` class, which offers a couple of handy methods that makes writing a test case easier. The most useful and basic are following:

-  handling user/admin account/connection:

   -  ``createAccount()`` - returns ``AccountBuilder`` allowing adjusting settings of particular user account;

   -  ``getAdminAccount()`` and ``getJaxmppAdmin()`` - get either ``Account`` object related to admin account or ``Jaxmpp`` object directly.

   -  ``removeUserAccount(Jaxmpp jaxmpp)`` - delete particular user account

-  vhost management:

   -  ``addVhost(Jaxmpp adminJaxmpp, String prefix)``

   -  ``removeVhost(Jaxmpp adminJaxmpp, String VHost)``

-  user basic xmpp functionality methods:

   -  ``changePresenceAndWait(Jaxmpp from, Jaxmpp to, Presence.Show p)``

   -  ``sendAndFail(Jaxmpp from, Jaxmpp to)``

   -  ``sendAndWait(Jaxmpp j, IQ iq, AsyncCallback asyncCallback)``

   -  ``sendAndWait(Jaxmpp from, Jaxmpp to, Message message)``

   -  ``sendAndWait(Jaxmpp from, Jaxmpp to, String message)``

   -  ``testSendAndWait(Jaxmpp from, Jaxmpp to)``

   -  ``testSubscribeAndWait(Jaxmpp from, Jaxmpp to)``

-  utility and configuration

   -  ``getDomain()`` - returns random domain from the list of available domains from the list of available domains (see `??? <#test-configuration>`__)

   -  ``getDomain(int i)`` - returns i-th domain from the list of available domains from the list of available domains (see `??? <#test-configuration>`__)

   -  ``getInstanceHostname()`` - returns random machine hostname from the list of available hostnames from the list of available domains (see `??? <#test-configuration>`__)

   -  ``getInstanceHostnames()`` - returns i-th machine hostname from the list of available hostnames from the list of available domains (see `??? <#test-configuration>`__)

   -  ``getApiKey()`` - returns configured HTTP-API key

   -  ``getHttpPort()`` - returns configured HTTP-API port

   -  ``getBoshURI()`` - returns BOSH URI based on configuration in `??? <#test-configuration>`__

   -  ``getWebSocketURI()`` - returns WebSocket URI based on configuration in `??? <#test-configuration>`__

In addition ``tigase.tests.utils.AccountBuilder`` class allows:

-  ``setUsername(String username)`` - set username/local part name of particular account

-  ``setDomain(String domain)`` - set domain name of particular account

-  ``setPassword(String password)`` - set password of particular account

-  ``setEmail(String email)`` - set e-mail address of particular account

-  ``setLogPrefix(String logPrefix)`` - allows to customize log prefix for log entries for this particular account

-  ``setRegister(boolean register)`` - specify whether account should be registered automatically

For the purpose of testing delayed response ``tigase.tests.Mutex`` can be used: \* ``waitFor(long timeout, String…​ items)`` - instruct Mutex to wait for the particular items during configured ``timeout`` \* ``notify(String…​ itemName)`` - upon receiving desired response notify Mutex about it \* ``isItemNotified(String item)`` - can be used to verify whether particular item was received (useful in asserts)

Creating simple test
-------------------------

As an example we will use ``src/test/java/tigase/tests/ExampleJaxmppTest.java`` test case. Followings steps should be taken:

1. extend ``AbstractTest`` class:

   .. code:: java

      public class ExampleJaxmppTest extends AbstractTest {}

2. create test method and annote it with ``@Test``. In addition specify test group and provide short description

   .. code:: java

      @Test(groups = { "examples" }, description = "Simple test verifying logging in by the user")
      public void SimpleLoginTest() {}

3. create an Account object, configure it, later build Jaxmpp object from it and connect to the server

   .. code:: java

      Account userAccount = createAccount().setLogPrefix("test_user" ).build();
      Jaxmpp jaxmpp = userAccount.createJaxmpp().build();
      jaxmpp.login( true );

4. check whether the connection was successful

   .. code:: java

      assertTrue(createJaxmpp.isConnected(), "contact was not connected" );


Adding test to suite
-------------------------

As described in `??? <#Test-NG_configuration>`__, each test case must be included in Test Suite configuration.

1. create new xml file under ``src/test/resources/``, for example ``example.xml``

2. in the created xml file add new test case as follows, creating new Suite (specifying name) with a list of tests (specifying names), and each test can contain multiple classes (for details please refer to TestNG documentation)

   .. code:: xml

      <!DOCTYPE suite SYSTEM "https://testng.org/testng-1.0.dtd" >

      <suite name="Tigase Various Tests" verbose="1">
          <test name="Example Tests">
              <classes>
                  <class name="tigase.tests.ExampleJaxmppTest" />
              </classes>
          </test>
      </suite>

3. include created xml file in the Test Suite (see `??? <#Test-NG_configuration>`__)
