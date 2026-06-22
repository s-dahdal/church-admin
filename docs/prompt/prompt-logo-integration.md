# Logo Integration Prompt — Church Admin Application

## Context

The Church Admin desktop application is built with JavaFX + Spring Boot, packaged with `jpackage`.
The following logo files have been prepared and must be placed in the project:

| File | Purpose |
|---|---|
| `logo.png` | 256×256 PNG — JavaFX window icon (titlebar + taskbar at runtime) |
| `logo-48.png` | 48×48 PNG — Linux desktop icon for jpackage |
| `logo.ico` | Multi-resolution ICO (256, 48, 32, 16px) — Windows installer + taskbar icon |

All three files go into:
```
src/main/resources/images/
```

---

## Task 1 — JavaFX Window Icon (Stage)

In the application's main entry point — the class that extends `Application` or the Spring `@Component` that initialises the primary `Stage` — add the window icon so it appears in the OS titlebar and taskbar at runtime.

Find the method where `primaryStage` is configured (typically `start(Stage primaryStage)` or equivalent), and add this **before** `primaryStage.show()`:

```java
primaryStage.getIcons().add(
    new Image(getClass().getResourceAsStream("/images/logo.png"))
);
```

If the app already sets a title or scene on the stage, add the icon line right alongside those, keeping the stage setup grouped together.

---

## Task 2 — About / Splash Branding (optional inline use)

If there is an About dialog or a splash header in the app (e.g. in `Dashboard.fxml` or a dedicated `About.fxml`), add the logo as a decorative `ImageView`:

```xml
<ImageView fitWidth="80" preserveRatio="true">
    <image>
        <Image url="@/images/logo.png"/>
    </image>
</ImageView>
```

Place it at the top of the relevant FXML, inside the existing layout container. Do not create a new screen for this — only add it if a natural location already exists.

---

## Task 3 — jpackage Native Icon (pom.xml)

Locate the `jpackage` Maven plugin configuration in `pom.xml`. It will be under `<build><plugins>` and reference `jpackage` or `org.panteleyev:jpackage-maven-plugin`.

Add or update the icon configuration:

**For Windows builds:**
```xml
<icon>src/main/resources/images/logo.ico</icon>
```

**For Linux builds:**
```xml
<icon>src/main/resources/images/logo-48.png</icon>
```

If the plugin uses profiles to separate OS builds (e.g. `<profile><id>win</id>` and `<profile><id>linux</id>`), add the correct icon path inside each profile's plugin configuration block. Do not flatten the profiles.

If no jpackage plugin exists yet in `pom.xml`, add the following plugin entry inside `<build><plugins>`:

```xml
<plugin>
    <groupId>org.panteleyev</groupId>
    <artifactId>jpackage-maven-plugin</artifactId>
    <version>1.6.0</version>
    <configuration>
        <name>Church Admin</name>
        <appVersion>1.0</appVersion>
        <vendor>Samer Dahdal</vendor>
        <icon>src/main/resources/images/logo.ico</icon>
        <destination>target/dist</destination>
        <mainJar>church-admin.jar</mainJar>
    </configuration>
</plugin>
```

Adjust `<mainJar>` to match the actual JAR name produced by the build (check `<artifactId>` and `<version>` in the top of `pom.xml`).

---

## Constraints

- Do not modify any FXML layout geometry or CSS — only add the `ImageView` where instructed.
- Do not rename or relocate the image files; the paths above are final.
- The `logo.png` resource must be loaded via `getResourceAsStream` (classpath), not as a filesystem path, so it works both in the IDE and in the packaged installer.
- Do not add the icon line inside a try/catch — if the resource is missing it should fail loudly at startup, not silently.
