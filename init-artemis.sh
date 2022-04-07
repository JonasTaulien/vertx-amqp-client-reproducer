#!/usr/bin/env bash
mkdir artemis-data
cd artemis-data || exit
artemis create --user "artemis"              \
               --password "artemis"          \
               --queues "example_address"    \
               --require-login               \
               mybroker
