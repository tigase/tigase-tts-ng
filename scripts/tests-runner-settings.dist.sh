#!/bin/bash

db_name="tigase_test_db"

db_user="tigase_test_user"
db_pass="tigase_test_pass"

db_root_user="root"
db_root_pass="root"

database_host="localhost"

DATABASES=("derby" "mysql" "postgresql" "sqlserver" "mongodb")
DATABASES_IPS=("127.0.0.1" "127.0.0.1" "127.0.0.1" "sqlserverhost" "127.0.0.1")
IPS=("127.0.0.1" "127.0.0.1" "127.0.0.1" "127.0.0.1" "127.0.0.1")

server_timeout=15

server_dir="../tigase-server/server"

MAIL_HOST="localhost"
MAIL_SENDER_PASS="password"

tigase_distribution_url="http://build.tigase.org/nightlies/dists/latest/tigase-server-dist-max.tar.gz"

MS_MEM=100
MX_MEM=1000

SMALL_MS_MEM=10
SMALL_MX_MEM=50

#ROOT_DIR=./files/

