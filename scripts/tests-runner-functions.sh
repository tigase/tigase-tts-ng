#!/bin/bash
##
##  Tigase TTS-NG
##  Copyright (C) 2004-2017  "Tigase, Inc." <office@tigase.com>
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

# This file contains functions definition used
# in all other scripts.

function get_database_uri() {
	[[ -z ${1} ]] && local _db_type="${db_type}" || local _db_type=${1}
	[[ -z ${2} ]] && local _database_host="${database_host}" || local _database_host=${2}
	[[ -z ${3} ]] && local _src_dir="${server_dir}" || local _src_dir=${3}
	[[ -z ${4} ]] && local _config_file="etc/tigase.conf" || local _config_file=${4}
	[[ -z ${5} ]] && local _db_name="${db_name}" || local _db_name=${5}
	[[ -z ${6} ]] && local _db_user="${db_user}" || local _db_user=${6}
	[[ -z ${7} ]] && local _db_pass="${db_pass}" || local _db_pass=${7}
	[[ -z ${8} ]] && local _db_root_user="${db_root_user}" || local _db_root_user=${8}
	[[ -z ${9} ]] && local _db_root_pass="${db_root_pass}" || local _db_root_pass=${9}

    case ${_db_type} in
        mysql)
            export JDBC_URI="jdbc:mysql://${_database_host}/${_db_name}?user=${_db_user}&password=${_db_pass}&useSSL=false&useUnicode=true"
            ;;
        postgresql)
            export JDBC_URI="jdbc:postgresql://${_database_host}/${_db_name}?user=${_db_user}"
            ;;
        sqlserver)
            export JDBC_URI="jdbc:jtds:sqlserver://${_database_host}:1433;databaseName=${_db_name};user=${_db_user};password=${_db_pass};schema=dbo;lastUpdateCount=false"
            ;;
        derby)
            export JDBC_URI="jdbc:derby:"`pwd`"/${_db_name};create=true"
            ;;
        mongodb)
            [[ -z ${_db_user} ]] && export JDBC_URI="mongodb://${_database_host}/${_db_name}" ||  export JDBC_URI="mongodb://${_db_user}:${_db_pass}@${_database_host}/${_db_name}"
            ;;
        *)
            echo "Unknown db: ${_db_type}"
            ;;

    esac

	echo "JDBC uri: ${JDBC_URI}"

}

function db_reload_sql() {

	[[ -z ${1} ]] && local _db_type="${db_type}" || local _db_type=${1}
	[[ -z ${2} ]] && local _database_host="${database_host}" || local _database_host=${2}
	[[ -z ${3} ]] && local _src_dir="${server_dir}" || local _src_dir=${3}
	[[ -z ${4} ]] && local _config_file="etc/tigase.conf" || local _config_file=${4}
	[[ -z ${5} ]] && local _db_name="${db_name}" || local _db_name=${5}
	[[ -z ${6} ]] && local _db_user="${db_user}" || local _db_user=${6}
	[[ -z ${7} ]] && local _db_pass="${db_pass}" || local _db_pass=${7}
	[[ -z ${8} ]] && local _db_root_user="${db_root_user}" || local _db_root_user=${8}
	[[ -z ${9} ]] && local _db_root_pass="${db_root_pass}" || local _db_root_pass=${9}

	tts_dir=`pwd`
	cd ${_src_dir}

	if [[ "${_db_type}" == "derby" ]] ; then
		_db_name="${tts_dir}/${_db_name}"
	fi


    ./scripts/tigase.sh destroy-schema ${_config_file} -T ${_db_type} -D ${_db_name} -H ${_database_host} -U ${_db_user} -P ${_db_pass} -R ${_db_root_user} -A ${_db_root_pass} -L ALL

    if [[ ! $? -eq 0 ]] ; then
        cd ${tts_dir}
        return 1
    fi

    ./scripts/tigase.sh upgrade-schema ${_config_file} -R ${_db_root_user} -A ${_db_root_pass} -L ALL

    if [[ ! $? -eq 0 ]] ; then
        cd ${tts_dir}
        return 1
    fi

	cd ${tts_dir}
}

function tig_start_server() {

	[[ -z ${1} ]] && local _src_dir="../server" || local _src_dir=${1}
	[[ -z ${2} ]] && local _config_file="etc/tigase.conf" || local _config_file=${2}

	rm -f ${_src_dir}/logs/tigase.pid
	#killall java
	sleep_fun 2
	${_src_dir}/scripts/tigase.sh clear ${_config_file}
	${_src_dir}/scripts/tigase.sh start ${_config_file}
}

function tig_stop_server() {

	[[ -z ${1} ]] && local _src_dir="../server" || local _src_dir=${1}
	[[ -z ${2} ]] && local _config_file="etc/mysql.conf" \
		|| local _config_file=${2}

	${_src_dir}/scripts/tigase.sh stop ${_config_file}
	sleep_fun 2
	#killall java

}

function sleep_fun() {
	[[ -z ${1} ]] && local _sleep_timeout="1" || local _sleep_timeout=${1}

    while [ ${_sleep_timeout} -gt 0 ] ; do
        echo -ne "Sleeping: ${_sleep_timeout}\r"
        sleep 5
        _sleep_timeout=$((_sleep_timeout-5))
    done
    echo
}

function copy_results() {
	[[ -z ${1} ]] && local _server_dir=${server_dir} || local _server_dir=${1}
	[[ -z ${2} ]] && local _output_dir=${output_dir} || local _output_dir=${2}
	[[ -z ${3} ]] && local unset _only_server_logs || local only_server_logs=${3}

    [[ ! -z '${_output_dir}' &&  "/" != '${_output_dir}' ]] && rm -rf '${_output_dir}'

    mkdir -p "${_output_dir}/server-log/"

    if [[ -e "${_server_dir}/logs/tigase-console.log" ]] ; then
        cp ${_server_dir}/logs/tigase-console.log "${_output_dir}/server-log/"
    fi

    [[ ! -z "${only_server_logs}" ]] && return

    for file in testng-results.xml html xml ; do
        if [[ -e "target/surefire-reports/${file}" ]] ; then
            cp -r target/surefire-reports/${file} "${_output_dir}/"
        fi
    done
}

function run_test() {

	[[ -z ${1} ]] && local _database=${database} || local _database=${1}
	[[ -z ${2} ]] && local _server_dir=${server_dir} || local _server_dir=${2}
	[[ -z ${3} ]] && local _server_ip=${server_ip} || local _server_ip=${3}
	[[ -z ${4} ]] && local _database_host=${database_host} || local _database_host=${4}
	[[ -z ${5} ]] && local _test_case="all" || local _test_case=${5}

	local _output_dir="${output_dir}/${_test_case}/${_database}"

	[[ -z ${server_timeout} ]] && server_timeout=15

	echo "Database:         ${_database}"
	echo "Database IP:      ${_database_host}"
	echo "Server directory: ${_server_dir}"
	echo "Server IP:        ${_server_ip}"
	echo "Test case:        ${_test_case}"

    # to clear variables from other tests
    unset JDBC_URI

    get_database_uri ${_database} ${_database_host} ${_server_dir} "${CONFIG_BASE_DIR}/etc/tigase.conf"

    if [ -z "${JDBC_URI}" ] ; then
        TEMP=JDBC_URI_${_database}
        export JDBC_URI=${!TEMP}
        echo "Setting default JDBC_URI: ${JDBC_URI}"
    fi

    export CONFIG_BASE_DIR=`pwd`"/src/test/resources/server"

	if [ ! -z "${DB_RELOAD}" ] ; then
	  echo "Re-creating database: ${_database}"
		case ${_database} in
			derby|mongodb|mysql|postgresql|sqlserver)
				db_reload_sql ${_database} ${_database_host} ${_server_dir} "${CONFIG_BASE_DIR}/etc/tigase.conf"
				;;
			*)
				echo "Not supported database: '${database}'"
				usage
				exit 1
				;;
		esac

        if [[ ! $? -eq 0 ]] ; then
            echo "Database reloading failed! Skipping running tests!"
            return 1
        fi
	else
		echo "Skipped database reloading."
	fi

	sleep_fun 1

	if [ ! -z "${SERVER_START}" ] ; then

		tig_start_server ${_server_dir} "${CONFIG_BASE_DIR}/etc/tigase.conf"

        _PID=$(cat ${_server_dir}/logs/tigase.pid)
        sleep_fun $(((${server_timeout} / 2)))

        counter=$(((${server_timeout} * 15)))
        while [ $counter -gt 0 ] ; do
            if ! ps -p"${_PID}" -o "pid=" >/dev/null 2>&1; then
                echo "Process is NOT running... output of ${_server_dir}/logs/tigase-console.log";

                cat ${_server_dir}/logs/tigase-console.log

                tig_stop_server ${_server_dir} "etc/tigase.conf"

                copy_results ${_server_dir} ${_output_dir} "true"

                return
            fi

            if ! nc -z ${_server_ip} 5222 ; then
                echo -ne "waiting for server: ${counter}\r"
                sleep $(((${server_timeout} / 10)))
                counter=$((counter-5))
            else
                break;
            fi
        done

	else
		echo "Skipped Tigase server starting."
	fi

	echo -e "\nRunning: ${ver}-${_database} test, IP ${_server_ip}..."
	start_test=`date +%s`



	[[ -z ${_server_ip} ]] || local _server_ip_param="-Dserver.cluster.nodes=${_server_ip}"
	[[ -z ${_test_case} || "all" == "${_test_case}" ]] && local _test_case_param="" || local _test_case_param="-Dtest=${_test_case}"
	[[ -z ${MAIL_IMAP_HOST} ]] || local _mail_host_param="-Dimap.server=${MAIL_IMAP_HOST}"
	[[ -z ${MAIL_RECEIVER_ADDRESS} ]] || local _mail_receiver_user_address_param="-Dimap.username=${MAIL_RECEIVER_ADDRESS}"
	[[ -z ${MAIL_SENDER_PASS} ]] || local _mail_sender_pass_param="-Dimap.password=${MAIL_SENDER_PASS}"

	mvn clean test ${_server_ip_param} ${_test_case_param} ${_mail_host_param} ${_mail_sender_pass_param} ${_mail_receiver_user_address_param}



	end_test=`date +%s`
	total_time=$((end_test-start_test))

	if [[ "$(uname -s)" == "Darwin" ]] ; then
		total_str=`date -u -r $total_time +%H:%M:%S`
	else
        total_str=`date -u -d @$total_time +%H:%M:%S`
	fi

	echo "Test finished after: ${total_str}"

	sleep_fun 1

	if [ -z "${SKIP_SERVER_START}" ] ; then
        tig_stop_server ${_server_dir} "etc/tigase.conf"
    fi

    copy_results ${_server_dir} ${_output_dir}
}