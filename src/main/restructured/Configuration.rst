TTS-NG Configuration
=====================

Test Configuration
--------------------

In order to allow connecting to any server that we want to test we need to provide server details and such details are configured in ``src/test/resources/server.properties`` file. Through that file it’s possible to configure:

-  list of configured domains/VHosts (at least one entry is needed):

   ``server.domains=localhost``

-  define which domain should be used for Two-way-TLS test (such domain must be configured to use certificate from ``certs/root_ca.pem`` file)

   ``server.client_auth.domain=a.localhost``

-  a list of cluster nodes to which test should connect (for single node setup leave only one node; at least one entry is needed)

   ``server.cluster.nodes=localhost``

-  details of the admin account (JID will be created from below username and domain name or, if details are not provided, ``admin`` name will be used with first item from ``server.domains``; default password will be the same as used user-name)

   .. code:: bash

      test.admin.username=admin
      test.admin.domain=localhost
      test.admin.password=admin

-  HTTP-API component

   -  API key used to access service:

      ``test.http.api-key=test-api-key``

   -  port under which service is listening:

      ``test.http.port=8088``

-  it’s possible to override default ports for WebSocket nad BOSH connections: ``test.ws.port=5290`` and ``test.bosh.port=5280`` respectively.

-  Tigase XMPP Server e-mail monitoring configuration can be set-up using following entries:

   .. code:: bash

      imap.server=localhost
      imap.username=xygoteheyd
      imap.password=medkbreqeppbemzinmtu

Recommended server configuration
--------------------------------------

In case of running tests against Tigase XMPP Server, there are a couple of required configuration changes (that differ from the default server configuration). Recommended server configuration is located under following location: ``src/test/resources/server/etc/init.properties``. Please refer to the documentation for the explanation of the settings used if in doubt.

Test-NG configuration
---------------------------

All tests are grouped in *suites*. All main *suites* are defined in ``pom.xml`` file in ``maven-surefire-plugin`` plugin configuration:

.. code:: xml

   <suiteXmlFiles>
       <suiteXmlFile>src/test/resources/testng_server.xml</suiteXmlFile>
       <suiteXmlFile>src/test/resources/testng_muc.xml</suiteXmlFile>
       <suiteXmlFile>src/test/resources/testng_pubsub.xml</suiteXmlFile>
       <suiteXmlFile>src/test/resources/testng_http_api.xml</suiteXmlFile>
       <suiteXmlFile>src/test/resources/testng_archive.xml</suiteXmlFile>
       <suiteXmlFile>src/test/resources/testng_custom.xml</suiteXmlFile>
       <suiteXmlFile>src/test/resources/testng_jaxmpp.xml</suiteXmlFile>
   </suiteXmlFiles>

For the actual semantics of the suite xml files please refer to Test-NG documentation.
