#!/bin/bash
#
# Tigase TTS-NG - Test suits for Tigase XMPP Server
# Copyright (C) 2004 Tigase, Inc. (office@tigase.com)
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published by
# the Free Software Foundation, version 3 of the License.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program. Look for COPYING file in the top folder.
# If not, see http://www.gnu.org/licenses/.
#


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

MAIL_SMTP_HOST="smtp.tigase.org"
MAIL_IMAP_HOST="imap.tigase.org"
MAIL_SENDER_ADDRESS="sender@localhost"
MAIL_SENDER_PASS="bt555ml"
MAIL_RECEIVER_ADDRESS="receiver@localhost"
MAIL_RECEIVER_PASS="bt55ml"

tigase_distribution_url="https://build.tigase.net/nightlies/dists/latest/tigase-server-dist-enterprise.tar.gz"

MS_MEM=100
MX_MEM=1000

SMALL_MS_MEM=10
SMALL_MX_MEM=50

#ROOT_DIR=./files/

