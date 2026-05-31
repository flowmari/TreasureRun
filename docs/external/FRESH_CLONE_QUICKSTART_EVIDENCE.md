# Fresh Clone QuickStart Evidence

This document records whether a new contributor can clone the repository, start the local runtime, connect to the Minecraft server, and run the basic gameplay path without maintainer-only setup.

## Current status

Fresh-clone startup measurement is pending.

The README intentionally avoids claiming a startup time until this has been measured from a clean clone.

## Evidence target

A successful fresh-clone transcript should prove:

1. A clean clone can be created.
2. The contributor runtime starts with one documented command.
3. The plugin JAR is built and installed into the local server.
4. MySQL and Spigot start in isolated Docker containers.
5. Minecraft Java Edition 1.20.1 can connect to `localhost:25575`.
6. `/lang` and `/gamestart normal` can be tested.
7. Shutdown works through the documented script.

## Measurement procedure

Use a temporary directory outside the working repository:

```bash
cd /tmp
rm -rf treasurerun-fresh-clone-test
git clone https://github.com/flowmari/TreasureRun.git treasurerun-fresh-clone-test
cd treasurerun-fresh-clone-test

time TREASURERUN_OPS=YourMinecraftName ./scripts/contributor-up.sh
```

Then connect from Minecraft Java Edition 1.20.1:

```text
localhost:25575
```

After testing:

```bash
./scripts/contributor-down.sh --volumes
```

## Evidence to paste after the test

```text
Date:
Commit:
OS:
Java version:
Docker version:
Minecraft version:
Command used:
Startup time:
Connection result:
Commands tested:
ResourcePack prompt result:
Observed errors:
Shutdown result:
```

## Current non-claim

Until the transcript above is filled in, TreasureRun should be described as having a documented one-command local runtime, not a measured three-minute fresh-clone setup.

## Expected result

The expected result is that a new contributor can clone the repository, run the documented setup command, join the local Minecraft server, and try the basic TreasureRun gameplay without private maintainer knowledge.

This has not yet been measured from a fresh clone in this document. Until that transcript is added, the project should avoid claims such as "starts in under three minutes" or "works for any new contributor."

## Not measured yet

The following should remain non-claims until evidence is added:

- exact startup time from a fresh clone;
- Paper compatibility;
- public SpigotMC Resource installation flow;
- external user success rate;
- successful external issue / PR / translation participation.
