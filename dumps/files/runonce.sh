# This script is run once after reboot following a firmware update
echo "Running runonce.sh"

# AEI - enable restore defaults
rd-button-enable 1
# AEI - enable reset
reset-button-enable 1
# AEI - set rd time to 10 sec
rd-button-time 10
