# actiontec-cli
A command line interface for Actiontec MoCA devices

**Requirements**
+ Java 8 or later
+ A route in the routing table that directs traffic to `192.168.144.30` out the port the MoCA adapter is connected too.

**Running the Jar**

To use run the jar open Terminal/Command Prompt/Power Shell and enter

``java -jar atcli.jar``

If you have a non-default IP address (``192.168.144.30``) assigned to the MoCA adapter, the jar has an optional argument 
to allow it to be used instead.

``java -jar atcli.jar <ip address>``

**Adding a Route**

The easiest way to add a route is to assign an IP address to an interface in the same subnet. For example,
on windows this can be done running this command:

``netsh interface ip set address "Ethernet" static 192.168.144.2 255.255.255.0``

You will now see a route ``192.168.144.0/24`` going out the Ethernet interface.

NOTE: "Ethernet" must be the name of the interface that is connected to the MoCA adapter. You can find this
by typing name ``ipconfig /all`` into Command Prompt or Power Shell.

**Help Text**

Dumps of important commands help text can be viewed in the ``/help-doc`` directory on this github.

**Basic Internal Commands**

Internal commands are abbreviated with ``:`` and will not send anything to the MoCA device.

``:dump <file> <get | set | do>`` Dumps the help text for a mocap subcommand for every subcommand provided in the
``file`` (separated by newline). ``get``, ``set``, and ``do`` are the shell commands to be appended.

``:test`` Tests the connectivity to the MoCA adapter.

``:exit`` or ``exit`` Exists the process.

**Using grep**

For non-internal commands, one can use a grep-like feature by adding so to the end of the command.

``command | grep <regex>``

Example:

``help | grep mocap`` 
will only print 
``mocap                                    -- Moca stats and settings``