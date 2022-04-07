mkdir artemis-data
cd artemis-data
artemis create --user "artemis"              ^
               --password "artemis"          ^
               --queues "example_address"    ^
               --require-login               ^
               mybroker
