#!/bin/bash
##
##  Tigase TTS-NG
##  Copyright (C) 2004-2017, "Tigase, Inc." <office@tigase.com>
##
##  This program is free software: you can redistribute it and/or modify
##  it under the terms of the GNU General Public License as published by
##  the Free Software Foundation, either version 3 of the License.
##
##  This program is distributed in the hope that it will be useful,
##  but WITHOUT ANY WARRANTY; without even the implied warranty of
##  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
##  GNU General Public License for more details.
##
##  You should have received a copy of the GNU General Public License
##  along with this program. Look for COPYING file in the top folder.
##  If not, see http://www.gnu.org/licenses/.
##

# defaults

JDBC_URI_derby="jdbc:derby:tigase_test_db;create=true"
JDBC_URI_mongodb="mongodb://localhost/tigase_test_db"
JDBC_URI_sqlserver="jdbc:jtds:sqlserver://localhost:1433;databaseName=tigase_test_db;user=tigase_test_user;password=tigase_test_pass;schema=dbo;lastUpdateCount=false"
JDBC_URI_mysql="jdbc:mysql://localhost/tigase_test_db?user=tigase_test_user&password=tigase_test_pass&useSSL=false&useUnicode=true&characterEncoding=UTF-8"
JDBC_URI_postgresql="jdbc:postgresql://localhost/tigase_test_db?user=tigase_test_user"

SETTINGS_FILE=`dirname ${0}`/tests-runner-settings.sh
[[ -f ${SETTINGS_FILE} ]] && source ${SETTINGS_FILE} \
  || {
	echo "Can't find settings file: ${SETTINGS_FILE} using defaults"
#	server_dir="../tigase-server"
#	server_dir="../tigase-server/server"
	server_dir="tmp/tigase-server"
	server_ip="localhost"

	server_timeout=30

    DATABASES=("derby" "mysql" "postgresql" "sqlserver" "mongodb")
    DATABASES_IPS=("127.0.0.1" "127.0.0.1" "127.0.0.1" "127.0.0.1" "127.0.0.1")
    IPS=("localhost" "localhost" "localhost" "localhost" "localhost")

#	database="derby"
#	database_host="localhost"

	db_user="tigase_test_user"
	db_pass="tigase_test_pass"
	db_name="tigase_test_db"

    db_root_user="root"
    db_root_pass="root"


#    MAIL_SMTP_HOST="localhost"
#    MAIL_IMAP_HOST="localhost"
#    MAIL_SENDER_ADDRESS="sender@localhost"
#    MAIL_SENDER_PASS="bt555ml"
#    MAIL_RECEIVER_ADDRESS="receiver@localhost"

    tigase_distribution_url="https://build.tigase.net/nightlies/dists/latest/tigase-server-dist-max.tar.gz"

	MS_MEM=100
	MX_MEM=1000

	SMALL_MS_MEM=10
	SMALL_MX_MEM=20

	#ROOT_DIR=/home/tigase/tigase-tts-ng/files/
}
export MIN_MEM=$MS_MEM
export MAX_MEM=$MX_MEM

[ -n "${MAIL_SMTP_HOST}" ] && export MAIL_SMTP_HOST=${MAIL_SMTP_HOST}
[ -n "${MAIL_IMAP_HOST}" ] && export MAIL_IMAP_HOST=${MAIL_IMAP_HOST}
[ -n "${MAIL_SENDER_ADDRESS}" ] && export MAIL_SENDER_ADDRESS=${MAIL_SENDER_ADDRESS}
[ -n "${MAIL_SENDER_PASS}" ] && export MAIL_SENDER_PASS=${MAIL_SENDER_PASS}
[ -n "${MAIL_RECEIVER_ADDRESS}" ] && export MAIL_RECEIVER_ADDRESS=${MAIL_RECEIVER_ADDRESS}


FUNCTIONS_FILE=`dirname ${0}`/tests-runner-functions.sh
[[ -f ${FUNCTIONS_FILE} ]] && source ${FUNCTIONS_FILE} \
  || { echo "Can't find functions file: ${FUNCTIONS_FILE}" ; exit 1 ; }


function usage() {
	echo "Run selected or all tests for defined server"
	echo "----"
	echo "  --all-tests [(database)] :: Run all available tests"
	echo "          (database) is an array, if database is missing tests will be run against all configured ones"
	echo "                     possible values: derby, mysql, postgresql, sqlserver, mongodb"
	echo "  --custom <package.Class[#method]]> [(database)] :: Run defined test, accepts wildcards, eg.:"
	echo "          --custom tigase.tests.util.RetrieveVersion"
	echo "  --help :: print this help"
	echo "----"
	echo "  Special parameters only at the beginning of the parameters list"
	echo "  --debug|-d                   Turns on debug mode"
	echo "  --skip-rebuild-tts|-srb      Turns off rebuilding TTS-NG and only runs already build tests"
	echo "  --skip-summary-page-get|-sp  Turns off automatic generation of Summary Page"
	echo "  --skip-overriding-config     Turns off copying default TTS-NG test config"



# use ready-package or build the server


	echo "  --download-latest|-dl        Turns on downloading latest Tigase Server release"
	echo "  --reload-db|-db              Turns on reloading database"
	echo "  --start-server|-serv         Turns on starting Tigase server"
	echo "-----------"
	echo "  Other possible parameters are in following order:"
	echo "  [server-dir] [server-ip]"
}

found=1
while [ "${found}" == "1" ] ; do
	case "${1}" in
        --debug|-d)
			set -x
			export DEBUG_MODE=true
			shift
			;;
		--skip-rebuild-tts|-srb)
			export SKIP_REBUILD_TTS=1
			shift
			;;
		--skip-summary-page-get|-sp)
			export SKIP_SUMMARY_PAGE_GET=1
			shift
			;;
		--test-setup)
		    export TEST_SETUP=1
		    tests="tigase.tests.setup.TestSetup"
		    shift
		    ;;
		--download-latest|-dl)
			export SERVER_DOWNLOAD=1
			shift
			;;
		--reload-db|-db)
			export DB_RELOAD=1
			shift
			;;
		--start-server|-serv)
			export SERVER_START=1
			shift
			;;
		*)
			found=0
			;;
	esac
done

case "${1}" in
	--help|-h)
		usage
		;;
    --all-tests)
        echo "${2}"
		[[ -z ${2} ]] || DATABASES=( "${@:2}" )
		;;
	--custom)
		[[ -z ${2} ]] || tests=${2}
		[[ -z ${3} ]] || DATABASES=( "${@:3}" )
		;;
	*)
		[[ -z "${1}" ]] || echo "Invalid command '$1'"
		usage
		exit 1
		;;
esac

if [ ! -z "${SERVER_DOWNLOAD}" ] ; then
    echo "Downloading latest version of Tigase server."

    server_dir="tmp/tigase-server"
    rm -rf "`pwd`/${server_dir}"
    rmdir "`pwd`/tmp"

    mkdir -p ${server_dir}

    wget --no-verbose -O tmp/tigase-server.tar.gz ${tigase_distribution_url}

    tar -xf tmp/tigase-server.tar.gz -C ${server_dir} --strip-components=1
fi

server_binary="${server_dir}/jars/tigase-server.jar"
if [ -f "${server_dir}/jars/tigase-server-dist.jar" ] ; then
    server_binary="${server_dir}/jars/tigase-server-dist.jar"
fi

ver=`unzip -qc ${server_binary} META-INF/MANIFEST.MF | grep "Tigase-Version" | sed -e "s/Tigase-Version: \(.*\)/\\1/" | sed 's/[[:space:]]//'`

[[ -z "${ROOT_DIR}" ]] && ROOT_DIR="files/"

output_dir="${ROOT_DIR}test-results/${ver}"

echo "Tigase server home directory: ${server_dir}"
echo "Version: ${ver}"
echo "Output dir: ${output_dir}"

[ ! -e ${server_dir} ] && echo "Tigase home directory (${server_dir}) doesn't exist, aborting!" && exit 1


echo "Databases: ${DATABASES[*]}"
echo "Tests: ${tests}"


idx=0
export failed_tests=0

for database in ${DATABASES[*]} ; do
#    echo "Database: ${database}"

    DB=$(echo $database | cut -s -d',' -f 1)
    HOST=$(echo $database | cut -s -d',' -f 2)

    [[ ! -z ${DB} ]] && database=${DB}
    [[ ! -z ${HOST} ]] && DATABASES_IPS[idx]=${HOST}

#    echo "${database}, host: ${DATABASES_IPS[idx]}"

    run_test ${database} ${server_dir} ${IPS[idx]} ${DATABASES_IPS[idx]} ${tests}
    if [[ ! $? -eq 0 ]] ; then
        ((failed_tests++))
    fi

    idx=$(expr $idx + 1)
    sleep_fun $(((${server_timeout} * 2)))
done

if [[ -z "${SKIP_SUMMARY_PAGE_GET}" || ! "${SKIP_SUMMARY_PAGE_GET}" -eq 1 ]] ; then
    echo "Generating Summary page"
    mvn exec:java -Dexec.mainClass="tigase.tests.SummaryGenerator" -Dexec.args="${ROOT_DIR}"
else
    echo "Skipping summary page generation"
fi

exit ${failed_tests}
