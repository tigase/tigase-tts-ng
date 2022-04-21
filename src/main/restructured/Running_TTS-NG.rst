Running TTS-NG
===============

Basics
--------

As TTS-NG is based on the known framework running the test in the basic form is simply a matter of executing particular test case class(es). The easiest way to do that is to use Maven - you can either run all tests

.. code:: bash

   mvn test

or for particular package/class

.. code:: bash

   mvn test -Dtest=tigase.tests.util.*
   mvn test -Dtest=tigase.tests.util.RetrieveVersion

This will run particular test(s) using server details (details about server configuration are described in `??? <#TTS-NG_Configuration>`__). It’s possible to override any of the server configuration options from command line by simply appending ``-D<property_name>=<property_value>`` to the above options.

Using test-runner script
----------------------------

For the convenience of running automatic tests it’s recommended to use ``$ ./scripts/tests-runner.sh`` BASH script, which automates whole process of setting up the server (and database, with correct configuration and components being enabled), running desired tests as well as generating summary page.

Running it without any parameter will print a help with description of the possible options:

.. code:: bash

   $ ./scripts/tests-runner.sh
   Can't find settings file: ./scripts/tests-runner-settings.sh using defaults
   Tigase server home directory: tmp/tigase-server
   Version: 7.2.0-SNAPSHOT-b4823
   Output dir: files/test-results/7.2.0-SNAPSHOT-b4823
   Run selected or all tests for defined server
   ----
     --all-tests [(database)] :: Run all available tests
             (database) is an array, if database is missing tests will be run against all configured ones
                        possible values: derby, mysql, postgresql, sqlserver, mongodb
     --custom <package.Class[#method]]> [(database)] :: Run defined test, accepts wildcards, eg.:
             --custom tigase.tests.util.RetrieveVersion
     --help :: print this help
   ----
     Special parameters only at the beginning of the parameters list
     --debug|-d                   Turns on debug mode
     --skip-rebuild-tts|-srb      Turns off rebuilding TTS-NG and only runs already build tests
     --skip-summary-page-get|-sp  Turns off automatic generation of Summary Page
     --download-latest|-dl        Turns on downloading latest Tigase Server release
     --reload-db|-db              Turns on reloading database
     --start-server|-serv         Turns on starting Tigase server
   -----------
     Other possible parameters are in following order:
     [server-dir] [server-ip]

Majority of those are self-explanatory. By default, the script will only rebuild all test cases, run them and then generate Summary page and place the output in the ``files`` subdirectory (if not configured otherwise, see `Test Runner settings <#test-runner-settings>`__).

After ``--all-tests`` and ``--custom <test_case>`` options it’s possible to specify a space-delimited list of databases for which tests should be run (they will match database and server IPs defined in `Test Runner settings <#test-runner-settings>`__).

By appending following options

-  ``-dl`` (or full variant: ``--download-latest``) - you will instruct the script to download latest version of the server and unpack it to ``tmp/tigase-server`` sub directory

-  ``-db`` (or full variant: ``--reload-db``) - you will instruct the script to download prepare the configured database (drop it if it exists and load current schema)

-  ``-serv`` (or full variant: ``--start-server``) - you will instruct the script start the server from the configured location and utilize recommended settings (located in ``src/test/resources/server/etc/init.properties``)

Test Runner settings
^^^^^^^^^^^^^^^^^^^^^^^^

It’s possible to adjust default Test Runner settings by copying distribution settings and adjusting it to your needs:

::

   $ cp scripts/tests-runner-settings.dist.sh scripts/tests-runner-settings.sh

Following configuration options are available:

-  database configuration:

   -  ``db_name`` - name of the database to be created,

   -  ``db_user``, ``db_pass`` - name and password of the basic user, which will be used by Tigase,

   -  ``db_root_user``, ``db_root_pass`` - name and password of the database *root* user, which will be used to create all necessary databases and grant roles.

-  databases selection

   -  ``DATABASES=("derby" "mysql" "postgresql" "sqlserver" "mongodb")`` - a list of databases which will be tested

   -  ``DATABASES_IPS=("127.0.0.1" "127.0.0.1" "127.0.0.1" "sqlserverhost" "127.0.0.1")`` - a list of IPs of the databases which will be used while testing particular database, i.e. if you have a list of 3 databases in ``DATABASE`` for each item/index respective item from this array will be used (so for first item ``derby`` from ``DATABASES``, first item from ``DATABASES_IPS`` will be used;

   -  ``IPS=("127.0.0.1" "127.0.0.1" "127.0.0.1" "127.0.0.1" "127.0.0.1")`` - a list of IPs of the servers which will be used while testing particular database, i.e. if you have a list of 3 databases in ``DATABASE`` for each item/index respective item from this array will be used (so for first item ``derby`` from ``DATABASES``, first item from ``IPS`` will be used as a server IP to which connection will be made.

-  ``server_timeout=15`` - a timeout in seconds used to delay subsequent actions/tasks (for example to allow server proper startup)

-  ``server_dir="../tigase-server/server"`` - server directory which will be used to reload database (if enabled) and start the server (if enabled)

-  ``tigase_distribution_url="http://build.tigase.org/nightlies/dists/latest/tigase-server-dist-max.tar.gz"`` - link which will be used to download latest release of Tigase XMPP Server

-  memory configuration for normal tests: ``MS_MEM=100`` and ``MX_MEM=1000`` (minimum and maximum JVM heap size respectively) and *low memory tests*: ``SMALL_MS_MEM=10``, ``SMALL_MX_MEM=50`` (minimum and maximum JVM heap size respectively)

-  ``ROOT_DIR=./files/`` - a root directory where tests results will be stored and where summary page will be placed (in not disabled)
