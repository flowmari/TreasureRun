# Third-party notices

TreasureRun source code is licensed under the MIT License. The project license
is in `LICENSE`, and the distributed plugin JAR includes it as
`META-INF/licenses/TreasureRun-LICENSE.txt`.

The shaded TreasureRun plugin JAR bundles the following third-party code:

- **MySQL Connector/J 8.0.33** — GNU General Public License version 2 with the
  Universal FOSS Exception, version 1.0. The exact license file distributed in
  the Connector/J artifact is included as
  `META-INF/licenses/mysql-connector-j-8.0.33-LICENSE.txt`.
- **MyBatis 3.5.14** — Apache License 2.0. The JAR includes MyBatis' original
  notice as `META-INF/licenses/mybatis-3.5.14-NOTICE.txt`. The MyBatis artifact
  also contains repackaged OGNL and Javassist classes; this notice preserves
  the upstream notice shipped in that artifact.
- **Gson 2.10.1** — Apache License 2.0.
- **Protocol Buffers Java 3.21.9** — BSD 3-Clause license.

The Apache License 2.0 text used by the listed Apache-licensed components is
included as `META-INF/licenses/Apache-2.0.txt`. The Protocol Buffers license is
included as `META-INF/licenses/protobuf-java-3.21.9-LICENSE.txt`.

Spigot API, ProtocolLib, and Lombok are compile-time or server-provided
dependencies and are not bundled in the shaded TreasureRun JAR. Test-only
dependencies are also not bundled in the distributed JAR.

This notice describes the third-party code packaged in the JAR. It does not
change the license of TreasureRun source code.
