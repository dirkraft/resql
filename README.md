You need a postgres jdbc driver. gradle.build.kts style

    implementation("org.postgresql:postgresql:42.2.6")

Basic usage seems to work with sqlite.

    implementation("org.xerial:sqlite-jdbc:3.32.3.1")
