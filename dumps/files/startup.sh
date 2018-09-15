# This script is run every time the board boots.  runonce.sh is run before this script, after a sw update
echo Running startup.sh

# To disable all WEB commands except dir
# http-cmd-disable all
# http-cmd-enable dir
# http-cmd-disable http-cmd-enable 

# To disable all CLI commands except mocap, uncomment the following three lines:
# (note: this script can't run commands once they are disabled)
# cli-disable all
# cli-enable mocap
# cli-disable cli-enable

# AEI - set IP address here to keep it from restore defaults
ifconfig 192.168.144.30

mocap set --bonding 1

# AEI - set tx/rx power offset to meet MoCA2.0 power spec. From Jim H.
mocap set --max_tx_power_tune offset 2 1125 1475 
mocap set --max_tx_power_tune offset 1 1500 1675 
mocap set --max_tx_power_tune_sec_ch offset 0 1125 1675
mocap set --rx_power_tune offset 0 1125 1675

# AEI - support fast ethernet
eport-mdio 0x4 0x0de1
eport-gphy-set-pause off

# AEI - solid MoCA LED 
mocap set --led_mode 1

# AEI - set impedance tuning value to improve signal quality for sdk 2.11.1.30 or later
mocap set --impedance_mode_bonding 0x5c3

# AEI - set 2ndary channel seed to old stype to make 2.11.x interop with older sdk 2.10.x
mocap set --brcm_bonding_seed 1

