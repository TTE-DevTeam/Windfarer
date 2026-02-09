Windfarer
======

Maintained former fork of Movecraft.
Features long overdue overhauls and rewrites of multiple systems (e.g. Signs, Crafttypes) as well as extended API for developers (e.g. CraftInitiateTranslateEvent).

Focus lies on the demands of the TTE server.

You are free to use this version of Movecraft as long as you give credit to us.
However, there is no guarantee given that this 100% fits your needs and demands.

**Movecraft requires at least Java 17**

## Download

Releases can be found on the [releases tab](https://github.com/TTE-DevTeam/Movecraft/releases).

Development builds aren't provided, if you want one, compile it yourself using the instructions below.  Use at your own risk!

## Why TTE-Movecraft?

TTE-Movecraft is a technically motivated fork of Movecraft with the goal of addressing long-standing architectural and extensibility limitations in the APDev mainline.

While APDev Movecraft primarily focuses on maintaining compatibility with newer Minecraft versions, TTE focuses on **core improvements**, **modern abstractions**, and **better extension points for developers and addon authors**.

### Key Differences

- **Active core development**  
  TTE introduces meaningful changes to internal systems instead of limiting updates to version support and NMS adjustments.

- **Modernized CraftType system**  
  CraftTypes have been reworked to reduce implicit behavior, improve maintainability, and make extensions and tooling significantly easier for developers.

- **Reduced reliance on legacy Bukkit patterns**  
  TTE moves away from heavy usage of legacy enums (e.g. `Material`) and favors more modern, data-driven and namespaced approaches where possible.

- **Improved addon integration**  
  Additional events and a DataTag-based system provide safer and more flexible ways for addons to interact with Movecraft without relying on internal hacks or reflection.

- **Willingness to perform necessary breaking changes**  
  TTE prioritizes long-term code health and extensibility over preserving outdated abstractions.

TTE-Movecraft is intended for developers and server operators who want a more flexible, future-oriented Movecraft codebase, even if that means diverging from legacy design decisions.


## Support
As of now, the links below relay to the old APDev wiki. We are currently more or less busy writing our own. Most stuff there should be applicable to this edition though.
Crafttypes however are not compatible as we broke away from the old system!

Please check the [Wiki](https://github.com/APDevTeam/Movecraft/wiki) and [FAQ](https://github.com/APDevTeam/Movecraft/wiki/Frequently-Asked-Questions) pages before asking for help!

[Github Issues](https://github.com/TTE-DevTeam/movecraft/issues)

## Development Environment
Building Movecraft is as easy as downloading the source code and executing the following command:
```
./gradlew clean shadowJar --parallel
```
Compiled jars can be found in the `Movecraft/build/libs` directory.

#### Movecraft is released under the GNU General Public License V3. 
